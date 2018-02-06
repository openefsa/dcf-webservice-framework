package dcf_log;

import java.io.File;

import javax.xml.soap.SOAPException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import config.Environment;
import soap.ExportCatalogueFile;
import user.IDcfUser;

/**
 * Class used to download a dcf Log document starting from a log code.
 * Since a log is not immediately available in DCF, a polling
 * strategy can be used. In particular, it is possible to set the inter
 * attempts time and a limit to the maximum number of attempts.
 * @author avonva
 *
 */
public class DcfLogDownloader implements IDcfLogDownloader {

	private static final Logger LOGGER = LogManager.getLogger(DcfLogDownloader.class);

	/**
	 * Download a log without polling strategy
	 * @param logCode the code of the log to download
	 * @throws SOAPException
	 */
	public File getLog(IDcfUser user, Environment env, String logCode) throws SOAPException {
		
		ExportCatalogueFile req = new ExportCatalogueFile(user, env);
		File log = req.exportLog(logCode);
		
		if (log != null)
			LOGGER.info("Log successfully downloaded, file=" + log);
		
		return log;
	}
	
	/**
	 * Download a log from DCF with a polling strategy
	 * @param logCode the code of the log to download
	 * @param interAttemptsTime waiting time before trying again to download the log
	 * @throws SOAPException 
	 */
	public File getLog(IDcfUser user, Environment env, String logCode, long interAttemptsTime) throws SOAPException {
		return getLog(user, env, logCode, interAttemptsTime, -1);
	}
	
	/**
	 * Download a log from DCF with a polling strategy
	 * @param logCode the code of the log to download
	 * @param interAttemptsTime waiting time before trying again to download the log
	 * @param maxAttempts max number of allowed attempts (prevents DOS)
	 * @throws SOAPException 
	 */
	public File getLog(IDcfUser user, Environment env, String logCode, long interAttemptsTime, int maxAttempts) throws SOAPException {
		
		// if maxAttempts is > 0 then a limit is applied
		boolean isLimited = maxAttempts > 0;
		
		File log = null;
		
		// number of tried attempts
		int attemptsCount = 1;
		
		// polling
		while(true) {

			String diagnostic = "Getting log=" + logCode + ", attempt n�=" + attemptsCount;
			
			// add maximum number of attempts if limited attempts
			if(isLimited)
				diagnostic += "/" + maxAttempts;
			
			LOGGER.info(diagnostic);

			log = getLog(user, env, logCode);
			
			if(log == null)
				LOGGER.info("Log=" + logCode + " not available yet in DCF");

			// if the log was found or no another attempt is available
			if(log != null || (isLimited && attemptsCount >= maxAttempts))
				break;
			
			LOGGER.info("Waiting " + (interAttemptsTime/1000.00) 
					+ " seconds and then retry to download log=" + logCode);
			
			// wait inter attempts time
			try {
				Thread.sleep(interAttemptsTime);
			} catch(InterruptedException e) {
				e.printStackTrace();
				LOGGER.error("Cannot sleep thread=" + this, e);
			}
			
			// go to the next attempt
			attemptsCount++;
		}
		
		return log;
	}
}