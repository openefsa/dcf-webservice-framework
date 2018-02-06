package pending_request;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import config.Environment;
import dcf_log.DcfLogDownloaderMock;
import dcf_log.DcfLogParserMock;
import soap.DetailedSOAPException;
import user.DcfUser;

public class PendingRequestWorkerTest {
	
	private int count;
	
	@Before
	public void init() {
		this.count = 0;
	}
	
	@Test
	public void start() throws DetailedSOAPException, IOException {
		
		DcfUser user = new DcfUser();
		user.login("avonva", "Ab123456");
		Environment env = Environment.TEST;
		
		IPendingRequestDao<IPendingRequest> dao = new PendingRequestDaoMock<>();
		UploadCatalogueFilePersistentImplMock upc = new UploadCatalogueFilePersistentImplMock(dao);
		IPendingRequest r1 = upc.unreserve(user, env, "ACTION", "first pending request");
		IPendingRequest r2 = upc.unreserve(user, env, "MTX", "second pending request");
		
		PendingRequestWorker worker = new PendingRequestWorker(new DcfLogDownloaderMock(), new DcfLogParserMock()) {
			
			@Override
			public void statusChanged(PendingRequestStatusChangedEvent event) {
				
				if (event.getNewStatus() == PendingRequestStatus.COMPLETED)
					count++;
			}
		};
		
		worker.execute();
		
		worker.startPendingRequests(r1);
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		worker.startPendingRequests(r2);

		while(count < 2) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}