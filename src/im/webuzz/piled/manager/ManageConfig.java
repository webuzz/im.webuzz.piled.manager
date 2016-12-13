package im.webuzz.piled.manager;

import java.util.Properties;

public class ManageConfig {
	
	public static String managerPassword;
	
	public static String restartPassword;
	
	public static boolean redirectPages = true;
	
	/**
	 * This trusted host can make high risk operations. 
	 */
	public static String serverTrustedHost = "127.0.0.1";
	
	/**
	 * Classpath will be used for reloading classes. Put updated *.class files
	 * in this classpath and try to call
	 * {@link net.sf.j2s.ajax.SimpleClassLoader#reloadSimpleClass(String, String)}
	 * to mark specified classes to-be-reloaded.
	 */
	public static String classpath = "./bin/";

	public static void update(Properties prop) {
		String p = prop.getProperty("manager.password");
		if (p != null && p.length() > 0) {
			managerPassword = p;
		}
		p = prop.getProperty("restart.password");
		if (p != null && p.length() > 0) {
			restartPassword = p;
		}
	}

}
