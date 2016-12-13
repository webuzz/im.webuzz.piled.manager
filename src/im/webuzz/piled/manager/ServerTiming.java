package im.webuzz.piled.manager;

import java.lang.management.ManagementFactory;
import java.util.Date;

import im.webuzz.pilet.IPiledServer;
import im.webuzz.pilet.IPiledWrapping;
import im.webuzz.pilet.IServerBinding;

/**
 * A server timing, which is used to help server debugging.
 * 
 * Server will print current time every 10s. If server system load average
 * increases a lot, it will print current thread stacks.
 *  
 * @author zhourenjian
 *
 */
public class ServerTiming implements IPiledWrapping, IServerBinding {

	private static boolean initialized = false;
	
	private boolean running;

	private IPiledServer server;

	@Override
	public void binding(IPiledServer server) {
		this.server = server;
	}

	@Override
	public void afterClosed(IPiledServer server) {
		running = false;
	}

	@Override
	public void beforeStartup(final IPiledServer server) {
		if (initialized) {
			return;
		}
		
		running = true;
		Thread timing = new Thread("Piled Time Logger") {
			public void run() {
				double lastLA = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
				logStates();
				
				while (running) {
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					double currentLA = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
					System.out.println("[TIME] " + server.getProcessingIOs() + " " + new Date() + " // " + currentLA);
					boolean logged = true;
					if (lastLA < 1.0) {
						if (currentLA > 2.0) {
							logged = logStates();
						}
					} else if (lastLA < 2.0) {
						if (currentLA - lastLA > 1.5) {
							logged = logStates();
						}
					} else if (lastLA < 10.0) {
						if (currentLA - lastLA > 10.0) {
							logged = logStates();
						}
					} else if (lastLA < 20.0) {
						if (currentLA - lastLA > 20.0) {
							logged = logStates();
						}
					} else { // do not log states any more
					}
					
					if (logged) {
						lastLA = currentLA;
					}
				}
			};
		};
		timing.setDaemon(true);
		timing.start();
		
		initialized = true;
	}

	private boolean logStates() {
		try {
			// print all thread stacks
			System.out.println(ServerManagePilet.getSystemThreadStacks());
			
			if (server != null) {
				try {
					// print all connection
					System.out.println(ServerManagePilet.printAllKeyStatuses(server, false));
				} catch (Throwable e) {
					e.printStackTrace();
				}
				
				// print all HTTP requests
				System.out.println(ServerManagePilet.printStatistics(server, false, true, 3, true, false));
			}
			return true;
		} catch (Throwable e) {
			e.printStackTrace();
			return false;
		}
	}
	
}
