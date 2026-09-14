package pending_request;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.soap.SOAPException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import config.Environment;
import dcf_log.DcfLog;
import dcf_log.DcfResponse;
import dcf_log.IDcfLogParser;
import soap.DetailedSOAPException;
import soap.ExportCatalogueFile;
import soap.SOAPError;
import soap_interface.IExportCatalogueFile;
import user.IDcfUser;

/**
 * Implementation of a {@link IPendingRequest}
 * @author avonva
 * @author shahaal
 */
public class PendingRequest implements IPendingRequest {
	
	private static final Logger LOGGER = LogManager.getLogger(PendingRequest.class);
	
	private IExportCatalogueFile exportCatFile;
	private boolean paused;
	
	// type of the pending request (RESERVE, UNRESERVE, PUBLISH, UPLOAD_DATA)
	private String type;
	
	// requestor
	private IDcfUser user;
	
	// code of the log to retrieve
	private String logCode;

	// the priority of the pending reserve
	private PendingRequestPriority priority;
	
	// on which DCF the action was requested
	private Environment environment;

	// the status of the pending reserve
	private PendingRequestStatus status;

	//  the dcf response to the pending reserve
	private DcfResponse response;
	
	// the retrieved log
	private DcfLog log;
	
	// additional data of the request
	private Map<String, String> data;
	
	private long restartTime;  // when the request will be restarted
	
	// external listeners
	private Collection<PendingRequestListener> pendingRequestListeners;

	/**
	 * Use reserved to fetch information from the database
	 */
	public PendingRequest() {
		this.priority = PendingRequestPriority.HIGH;
		this.status = PendingRequestStatus.WAITING;
		this.pendingRequestListeners = new ArrayList<>();
	}
	
	/**
	 * 
	 * @param type
	 * @param user
	 * @param logCode
	 * @param priority
	 * @param environment
	 */
	public PendingRequest(String type, IDcfUser user, String logCode, Environment environment) {
		this();
		this.type = type;
		this.user = user;
		this.logCode = logCode;
		this.environment = environment;
	}

	@Override
	public DcfResponse start(IDcfLogParser parser) throws SOAPException, IOException {
		
		LOGGER.info("Starting pending request=" + this);
		
		this.setStatus(PendingRequestStatus.DOWNLOADING);
		
		File logFile = downloadLog(this.priority);

		// if it was in high priority but no log found
		if (this.priority == PendingRequestPriority.HIGH && logFile == null) {
			
			// retry with low priority
			
			LOGGER.info("Downgrading priority of pending request=" + this);
			this.priority = PendingRequestPriority.LOW;
			this.setStatus(PendingRequestStatus.QUEUED);
			
			logFile = downloadLog(this.priority);
		}

		if (logFile == null) {
			// this should never happen because the
			// log retrieval in low priority does not
			// end until a log is found
			LOGGER.error("This should never happen, the log was not found and the low priority cicle finished for request=" + this);
			this.setStatus(PendingRequestStatus.ERROR);
			return DcfResponse.ERROR;
		}
		
		// parse the log and get the dcf response in it
		this.log = parser.parse(logFile);
		
		// get the macro operation result
		this.response = this.log.getMacroOpResult();

		// log retrieved, thus request completed
		this.setStatus(PendingRequestStatus.COMPLETED);
		
		return this.response;
	}
	
	/**
	 * Download the log using the pending action. The speed
	 * behavior of the process is defined by {@link #priority}
	 * @return the log related to the reserve operation if it
	 * was found in the available time, otherwise null
	 * @throws SOAPException 
	 */
	private File downloadLog(PendingRequestPriority priorityVar) throws SOAPException {
		
		File logVar = null;
		
		// 12 attempts, one every 10 seconds -> 2 minutes total
		int maxAttempts = priorityVar == PendingRequestPriority.HIGH ? 12 : -1;
		long interAttemptsTime = getInterAttemptsTime(priorityVar);

		if (priorityVar == PendingRequestPriority.LOW)
			this.restartTime = System.currentTimeMillis() + getInterAttemptsTime(priorityVar);
		else
			this.restartTime = -1;
		
		logVar = this.downloadLog(this.user, this.environment, this.logCode, interAttemptsTime, maxAttempts);
		
		if (logVar != null)
			this.restartTime = -1;
		
		return logVar;
	}
	
	/**
	 * Download a log without polling strategy
	 * @param logCodeVar the code of the log to download
	 * @throws SOAPException
	 */
	private File downloadLog(IDcfUser userVar, Environment env, String logCodeVar) throws SOAPException {
		
		if (this.exportCatFile == null)
			this.exportCatFile = new ExportCatalogueFile();
		
		File logVar = null;
		try {
		 logVar = this.exportCatFile.exportLog(env, userVar, logCodeVar);
		}catch(DetailedSOAPException e) {
			if(e.getError().equals(SOAPError.SERVER_ERROR)) {
				LOGGER.info("SERVER ERROR",e.getMessage());
				logVar = null;
			}else {
				throw e;
			}
		}
		
		if (logVar != null)
			LOGGER.info("Log successfully downloaded, file=" + logVar);

		return logVar;
	}
	
	/**
	 * Download a log from DCF with a polling strategy
	 * @param logCodeVar the code of the log to download
	 * @param interAttemptsTime waiting time before trying again to download the log
	 * @param maxAttempts max number of allowed attempts (prevents DOS)
	 * @throws SOAPException 
	 */
	private synchronized File downloadLog(IDcfUser userVar, Environment env, String logCodeVar, 
			long interAttemptsTime, int maxAttempts) throws SOAPException {
		
		// if maxAttempts is > 0 then a limit is applied
		boolean isLimited = maxAttempts > 0;
		
		File logVar = null;
		
		// number of tried attempts
		int attemptsCount = 1;
		
		// polling
		while(true) {

			String diagnostic = "Getting log=" + logCodeVar + ", attempt n°=" + attemptsCount;
			
			// add maximum number of attempts if limited attempts
			if(isLimited)
				diagnostic += "/" + maxAttempts;
			
			LOGGER.info(diagnostic);

			logVar = downloadLog(userVar, env, logCodeVar);
			
			if(logVar == null)
				LOGGER.info("Log=" + logCodeVar + " not available yet in DCF");

			// if the log was found or no another attempt is available
			if(logVar != null || (isLimited && attemptsCount >= maxAttempts))
				break;
			
			LOGGER.info("Waiting " + (interAttemptsTime/1000.00) 
					+ " seconds and then retry to download log=" + logCodeVar);
			
			// wait inter attempts time
			try {
				this.paused = true;
				this.wait(interAttemptsTime);
			} catch(InterruptedException e) {
				LOGGER.error("Error during wait ", e);
				e.printStackTrace();
			}
			
			this.paused = false;
			
			// go to the next attempt
			attemptsCount++;
		}
		
		return logVar;
	}
	
	/**
	 * Get inter attempt time based on priority
	 * @return
	 */
	private static long getInterAttemptsTime(PendingRequestPriority priority) {
		
		long interAttemptsTime = 10000; 
		
		// set inter attempts time according to the priority
		switch (priority) {
		case HIGH:
			interAttemptsTime = 10000;  // 10 seconds
			break;
		case LOW:
			interAttemptsTime = 300000; // 5 minutes
			break;
		default:
			break;
		}
		return interAttemptsTime;
	}
	
	/**
	 * Change the status of the request and notify
	 * listeners
	 * @param newStatus
	 */
	private void setStatus(PendingRequestStatus newStatus) {
		
		PendingRequestStatus oldStatus = this.status;
		this.status = newStatus;
		
		PendingRequestStatusChangedEvent event = new PendingRequestStatusChangedEvent(
				this, oldStatus, newStatus);
		
		// notify listeners
		for (PendingRequestListener listener : this.pendingRequestListeners) {
			listener.statusChanged(event);
		}
	}
	
	@Override
	public void addPendingRequestListener(PendingRequestListener listener) {
		this.pendingRequestListeners.add(listener);
	}
	
	@Override
	public Environment getEnvironmentUsed() {
		return this.environment;
	}
	
	@Override
	public PendingRequestStatus getStatus() {
		return this.status;
	}
	
	@Override
	public PendingRequestPriority getPriority() {
		return this.priority;
	}
	
	@Override
	public IDcfUser getRequestor() {
		return this.user;
	}
	
	@Override
	public DcfResponse getResponse() {
		return this.response;
	}
	
	@Override
	public String getLogCode() {
		return this.logCode;
	}
	
	@Override
	public DcfLog getLog() {
		return this.log;
	}
	
	@Override
	public Map<String, String> getData() {
		return this.data;
	}
	
	/**
	 * Add data to the request
	 * @param key
	 * @param value
	 */
	public void addData(String key, String value) {
		
		if (this.data == null || this.data.isEmpty())
			this.data = new HashMap<>();

		this.data.put(key, value);
	}
	
	@Override
	public String getType() {
		return this.type;
	}
	
	@Override
	public boolean isPaused() {
		return this.paused;
	}
	
	@Override
	public long getRestartTime() {
		return this.restartTime;
	}

	@Override
	public synchronized void restart() {
		
		if (!this.isPaused())
			return;
		
		this.restartTime = System.currentTimeMillis();
		this.notify();
	}

	@Override
	public void setEnvironmentUsed(Environment env) {
		this.environment = env;
	}

	@Override
	public void setRequestor(IDcfUser user) {
		this.user = user;
	}

	@Override
	public void setLogCode(String logCode) {
		this.logCode = logCode;
	}

	@Override
	public void setType(String type) {
		this.type = type;
	}

	@Override
	public void setPriority(PendingRequestPriority priority) {
		this.priority = priority;
	}
	
	@Override
	public void setData(Map<String, String> data) {
		this.data = data;
	}
	
	@Override
	public boolean equals(Object obj) {
		
		if (!(obj instanceof IPendingRequest))
			return super.equals(obj);
		
		IPendingRequest req = (IPendingRequest) obj;
		
		return this.logCode.equals(req.getLogCode()) && this.environment == req.getEnvironmentUsed()
				&& this.user.equals(req.getRequestor()) && this.type.equals(req.getType());
	}
	
	@Override
	public String toString() {
		return "logCode=" + this.logCode 
				+ "; status=" + this.status
				+ "; priority=" + this.priority
				+ "; environment=" + this.environment 
				+ "; requestor=" + this.user 
				+ "; requestType=" + this.type
				+ "; data=" + this.data;
	}

	@Override
	public int hashCode() {
		
		return super.hashCode();
	}
}
