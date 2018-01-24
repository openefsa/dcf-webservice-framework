package soap;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import config.Config;
import user.IDcfUser;

public class SOAPConsole {

	private static final Logger LOGGER = LogManager.getLogger(SOAPConsole.class);
	
	public static void log(String action, IDcfUser user) {
		
		Config config = new Config();
		
		String env = config.isProductionEnvironment() ? "production" : "test";
		
		LOGGER.info("WebServiceRequest#" + action + " required from user=" + user.getUsername() 
			+ " in " + env + " environment");
	}
	
	public static void log(String action, Object object) {
		LOGGER.info("WebServiceResponse#" + action + " response received");
		LOGGER.debug("WebServiceResponse#" + action + " response=" + object);
	}
}
