package soap;

import javax.xml.soap.SOAPException;

/**
 * Custom version of the soap exception which saves
 * also the reason of the exception.
 * @author avonva
 * @author shahaal
 *
 */
public class DetailedSOAPException extends SOAPException {

	private static final long serialVersionUID = 1L;

	public DetailedSOAPException(SOAPException e) {
		super(e);
		e.printStackTrace();
	}
	
	public boolean isConnectionProblem() {
		return getError() == SOAPError.NO_CONNECTION;
	}
	
	public boolean isUnauthorized() {
		return getError() == SOAPError.UNAUTHORIZED ||
				getError() == SOAPError.FORBIDDEN;
	}
	
	public boolean isSendMessageFailed() {
		return getError() == SOAPError.MESSAGE_SEND_FAILED;
	}
	
	/**
	 * handle when exceeded the max calls per minute
	 * 
	 * @author shahaal
	 * @return
	 */
	public boolean isTooManyRequests() {
		return getError() == SOAPError.TOO_MANY_REQUESTS;
	}
	
	/**
	 * handle when exceeded the max calls per week
	 * 
	 * @author shahaal
	 * @return
	 */
	public boolean isQuotaExceeded() {
		return getError() == SOAPError.QUOTA_EXCEEDED;
	}
	
	public SOAPError getError() {
		
		SOAPError error;
		
		String message = this.getMessage();
		
		if(message.contains("Quota Exceeded"))
			error = SOAPError.QUOTA_EXCEEDED;
		else if (message.contains("Too Many Requests"))
			error = SOAPError.TOO_MANY_REQUESTS;
		else if (message.contains("Message send failed"))
			error = SOAPError.MESSAGE_SEND_FAILED;
		else if (message.contains("401"))
			error = SOAPError.UNAUTHORIZED;
		else if (message.contains("403"))
			error = SOAPError.FORBIDDEN;
		else if (message.contains("soap:Server"))
			error = SOAPError.SERVER_ERROR;
		else
			error = SOAPError.NO_CONNECTION;
		
		return error;
	}
}
