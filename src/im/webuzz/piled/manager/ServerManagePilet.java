package im.webuzz.piled.manager;

import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.URLDecoder;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;


//import net.sf.j2s.ajax.SimplePipeHelper;
//import net.sf.j2s.ajax.SimplePipeRunnable;
//import net.sf.j2s.ajax.SimplePipeHelper;
//import net.sf.j2s.ajax.SimplePipeRunnable;
import net.sf.j2s.ajax.SimpleSerializable;
import im.webuzz.classloader.SimpleClassLoader;
import im.webuzz.config.Config;
import im.webuzz.config.security.Base64;
import im.webuzz.pilet.HttpLoggingUtils;
import im.webuzz.pilet.HttpRequest;
import im.webuzz.pilet.HttpWorkerUtils;
import im.webuzz.pilet.IPiledServer;
import im.webuzz.pilet.IPiledWrapping;
import im.webuzz.pilet.IPilet;
import im.webuzz.pilet.IServerBinding;
import im.webuzz.pilet.HttpResponse;

public class ServerManagePilet implements IPilet, IServerBinding, IPiledWrapping {

	private IPiledServer server;

	@Override
	public void beforeStartup(IPiledServer server) {
		Config.registerUpdatingListener(ManageConfig.class);
		ClassLoader simpleLoader = new ClassLoader() {
			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				return SimpleClassLoader.loadSimpleClass(name);
			}
		};
		Config.setConfigurationClassLoader(simpleLoader);
		server.setSimpleClassLoader(simpleLoader);
	}

	@Override
	public void afterClosed(IPiledServer server) {
		
	}

	@Override
	public void binding(IPiledServer server) {
		this.server = server;
	}

	@Override
	public boolean service(HttpRequest req, HttpResponse resp) {
		//*
		if (ManageConfig.redirectPages && req.host != null && req.host.startsWith("www.")) {
			HttpWorkerUtils.found("http://" + req.host.substring(4) + req.url, req, resp);
			return true;
		}
		//*/
		// redirect http://hello.com./index.html to http://hello.com/index.html
		if (ManageConfig.redirectPages && req.host != null && req.host.matches("^.*[\\\\\\.]$")) {
			HttpWorkerUtils.redirect("http://" + req.host.substring(0, req.host.length() - 1) + req.url, req, resp);
			return true;
		}
		
		/* Restart server!!! */
		if (req.url.startsWith("/restart/") && (ManageConfig.serverTrustedHost == null
				|| ManageConfig.serverTrustedHost.equals(req.remoteIP))) {
			String retartPassword = Config.parseSecret(ManageConfig.restartPassword);
			if (retartPassword != null && retartPassword.length() > 0
					&& req.url.startsWith("/restart/" + retartPassword)) {
				HttpWorkerUtils.pipeOut(req, resp, "text/html", null, "ok", false);
				System.out.println(new Date());
				System.out.println("Received restarting server request!");
				
				Thread t = new Thread() {
					public void run() {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						HttpLoggingUtils.stopLogging();
						IPiledServer[] allServers = server.getAllServers();
						if (allServers != null) {
							for (IPiledServer s : allServers) {
								s.stop();
							}
						}
						//server.stop();
					};
				};
				t.setDaemon(true);
				t.start();
				return true;
			}
		}
		
		/* List started threads and their stacks */
		if (req.url.startsWith("/mx/")) {
			if (needAuthorization(req.authorization)) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			long tid = -1;
			String uri = req.url;
			int idx = uri.lastIndexOf('/');
			if (idx != -1) {
				try {
					tid = Long.parseLong(uri.substring(idx + 1));
				} catch (NumberFormatException e) {
					//e.printStackTrace();
				}
			}
			String output = getSystemThreadInformation(tid);
			String content = output.replaceAll("\r\n", "<br />\r\n");
			HttpWorkerUtils.pipeOut(req, resp, "text/html", null, content, false);
			return true;
		}
		/* current opened connections with referer information */
		if (req.url.startsWith("/manager/status-referer")) {
			if (needAuthorization(req.authorization)) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			HttpWorkerUtils.pipeOut(req, resp, "text/html", null, printStatistics(server, true, true, 1, req.url.endsWith("/with-idled"), false), false);
			return true;
		}
		/* current opened connections with user agent information */
		if (req.url.startsWith("/manager/status-ua")) {
			if (needAuthorization(req.authorization)) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			HttpWorkerUtils.pipeOut(req, resp, "text/html", null, printStatistics(server, true, true, 2, req.url.endsWith("/with-idled"), req.url.endsWith("/with-pipe")), false);
			return true;
		}
		/* current opened connections with all information */
		if (req.url.startsWith("/manager/status-all")) {
			if (needAuthorization(req.authorization)) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			HttpWorkerUtils.pipeOut(req, resp, "text/html", null, printStatistics(server, true, true, 3, req.url.endsWith("/with-idled"), false), false);
			return true;
		}
		if (req.url.startsWith("/manager/status-analytics")) {
			if (needAuthorization(req.authorization)) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			HttpWorkerUtils.pipeOut(req, resp, "text/html", null, printStatistics(server, true, false, -1, req.url.endsWith("/with-idled"), false), false);
			return true;
		}
		/* current opened connections with basic information */
		if (req.url.startsWith("/manager/status")) {
			if (needAuthorization(req.authorization)) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			HttpWorkerUtils.pipeOut(req, resp, "text/html", null, printStatistics(server, true, true, -1, req.url.endsWith("/with-idled"), false), false);
			return true;
		}
		if (req.url.startsWith("/manager/classloader/release")) {
			if (needAuthorization(req.authorization) || (ManageConfig.serverTrustedHost != null
					&& !ManageConfig.serverTrustedHost.equals(req.remoteIP))) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			SimpleClassLoader.releaseUnusedClassLoaders();
			HttpWorkerUtils.pipeOut(req, resp, "text/html", null, System.currentTimeMillis() + "\r\nUnused class loader released.", false);
			return true;
		}
		if (req.url.startsWith("/manager/classloader/status")) {
			if (needAuthorization(req.authorization)) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			HttpWorkerUtils.pipeOut(req, resp, "text/html", null, SimpleClassLoader.allLoaderStatuses().replaceAll("\r\n", "<br />\n"), false);
			return true;
		}
		if (req.url.startsWith("/manager/reload/")) {
			if (needAuthorization(req.authorization) || (ManageConfig.serverTrustedHost != null
					&& !ManageConfig.serverTrustedHost.equals(req.remoteIP))) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			String query = null;
			if (req.requestData != null) {
				try {
					query = (req.requestData instanceof String ? (String) req.requestData
							: new String((byte[]) req.requestData, "UTF-8"));
				} catch (UnsupportedEncodingException e1) {
					e1.printStackTrace();
				}
			}
			String tag = null;
			String classpath = ManageConfig.classpath;
			boolean rpc = true;
			if (query != null) {
				String key = "tag=";
				int keyLength = key.length();
				int idx = query.indexOf(key);
				if (idx != -1) {
					int idx2 = query.indexOf("&", idx + keyLength);
					if (idx2 != -1) {
						tag = query.substring(idx + keyLength, idx2);
					} else {
						tag = query.substring(idx + keyLength);
					}
				}
				key = "classpath=";
				keyLength = key.length();
				idx = query.indexOf(key);
				if (idx != -1) {
					int idx2 = query.indexOf("&", idx + keyLength);
					if (idx2 != -1) {
						classpath = query.substring(idx + keyLength, idx2);
					} else {
						classpath = query.substring(idx + keyLength);
					}
				}
				key = "rpc=";
				keyLength = key.length();
				idx = query.indexOf(key);
				if (idx != -1) {
					int idx2 = query.indexOf("&", idx + keyLength);
					if (idx2 != -1) {
						rpc = "true".equalsIgnoreCase(query.substring(idx + keyLength, idx2));
					} else {
						rpc = "true".equalsIgnoreCase(query.substring(idx + keyLength));
					}
				}
			}
			String classStr = req.url.substring("/manager/reload/".length());
			try {
				classStr = URLDecoder.decode(classStr, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			String[] classes = classStr.split("[\\/,:]");
			if (classes != null && classes.length > 0) {
				boolean reloading = true;
				if (rpc) {
					SimpleClassLoader.reloadSimpleClasses(classes, classpath, tag);
				} else if (server != null) {
					SimpleClassLoader.reloadSimpleClasses(classes, classpath, tag);
					server.reloadClasses(classes, classpath, tag);
				} else { // not loading class
					reloading = false;
				}
				StringBuilder builder = new StringBuilder();
				if (reloading) {
					for (int i = 0; i < classes.length; i++) {
						String clazz = classes[i];
						if (clazz != null && clazz.length() > 0) {
							builder.append(clazz);
							builder.append(",\r\n");
						}
					}
				}
				if (tag != null) {
					builder.append("on tag ");
					builder.append(tag);
					builder.append(",\r\n");
				}
				if (!rpc) {
					builder.append("non rpc\r\n");
				}
				builder.append(System.currentTimeMillis());
				builder.append("\r\nReloaded.");
				String output = builder.toString();
				System.out.println(output);
				HttpWorkerUtils.pipeOut(req, resp, "text/html", null, output, false);
			} else {
				HttpWorkerUtils.pipeOut(req, resp, "text/html", null, System.currentTimeMillis() + "\r\nNo classes are reloaded.", false);
			}
			return true;
		}
		if (req.url.startsWith("/manager/mapping/")) {
			if (needAuthorization(req.authorization) || (ManageConfig.serverTrustedHost != null
					&& !ManageConfig.serverTrustedHost.equals(req.remoteIP))) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			String classStr = req.url.substring("/manager/mapping/".length());
			try {
				classStr = URLDecoder.decode(classStr, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			String[] classes = classStr.split("[\\/,:]");
			if (classes != null && classes.length > 0) {
				StringBuilder builder = new StringBuilder();
				for (int i = 0; i < classes.length; i++) {
					String clazz = classes[i];
					if (clazz != null && clazz.length() > 0) {
						String[] mapping = clazz.split("-");
						if (mapping != null && mapping.length == 2
								&& mapping[0] != null && mapping[0].length() > 0
								&& mapping[1] != null && mapping[1].length() > 0) {
							SimpleSerializable.registerClassShortenName(mapping[0], mapping[1]);
							builder.append(mapping[0]);
							builder.append(" - ");
							builder.append(mapping[1]);
							builder.append("\r\n");
						}
					}
				}
				builder.append(System.currentTimeMillis());
				builder.append("\r\nMapping updated.");
				String output = builder.toString();
				System.out.println(output);
				HttpWorkerUtils.pipeOut(req, resp, "text/html", null, output, false);
			} else {
				HttpWorkerUtils.pipeOut(req, resp, "text/html", null, System.currentTimeMillis() + "\r\nNo class mappings are updated.", false);
			}
			return true;
		}
		/* current sockets status */
		if (req.url.startsWith("/server-status/")) {
			if (needAuthorization(req.authorization)) {
				HttpWorkerUtils.send401AuthorizationRequired("Piled Manager", req, resp);
				return true;
			}
			HttpWorkerUtils.pipeOut(req, resp, "text/html", null, printAllKeyStatuses(server, true), false);
			return true;
		}
		return false;
	}

	
	/*
	 * Decrypt and format into Basic Authentication string.
	 */
	private static String decryptBasicAuth(final String password) {
		String secret = Config.parseSecret(password);
		if (secret != null && secret.indexOf(':') != -1) {
			secret = Base64.byteArrayToBase64(secret.getBytes());
		} // else original base 64 encoded secret
		return secret;
	}

	private boolean needAuthorization(String authStr) {
		if (authStr == null) {
			return true;
		}
		int idx = authStr.indexOf(" ");
		if (idx == -1) {
			return true;
		}
		String method = authStr.substring(0, idx);
		String value = authStr.substring(idx + 1);
		String password = ManageConfig.managerPassword;
		return !method.equals("Basic")
				|| password == null || password.length() == 0
				|| !value.equals(decryptBasicAuth(password));
	}

	public static String printStatistics(IPiledServer server, boolean html, boolean listRequests, int type, boolean idled, boolean details) {
		StringBuilder builder = new StringBuilder();
		if (html) builder.append("<html><head><title>Server Status</title><style>.time-span { width: 116px; float:left; clear: both; }\r\n.ip-span { width: 120px; float:left; }\r\n.host-span { width: 180px; float:left; }\r\n.method-span { width: 48px; display:none; float:left; }</style></head><body>");
		builder.append("Keeped alive sockets count: ");
		HttpRequest[] requests = server.getActiveRequests();
		builder.append(requests.length);
		if (html) builder.append("<br />");
		builder.append("\r\n");
		builder.append("Request count: ");
		builder.append(server.getTotalRequests());
		builder.append(" Error count: ");
		builder.append(server.getErrorRequests());
		if (html) builder.append("<br />");
		builder.append("\r\n");
		
		long now = System.currentTimeMillis();
		int idx = 0;
		for (int i = 0; i < requests.length; i++) {
			HttpRequest req = requests[i];
			if (/*req.keepAliveMax > 1 && */!req.done) {
				idx++;
				if (!listRequests) {
					continue;
				}
				if (html) builder.append("<div class=\"time-span\">");
				builder.append(idx);
				builder.append(": ");
				builder.append((now - req.created + 999) / 1000);
				builder.append("s");
				if (html) builder.append("</div>");
				builder.append(" ");
				if (html) builder.append("<div class=\"ip-span\">");
				builder.append(req.remoteIP);
				if (html) builder.append("</div>");
				builder.append(" ");
				if (html) builder.append("<div class=\"host-span\">");
				builder.append(req.host);
				if (html) builder.append("</div>");
//				builder.append(" ");
//				if (html) builder.append("<div class=\"method-span\">");
//				builder.append(req.method);
//				if (html) builder.append("</div>");
				builder.append(" ");
				builder.append(req.comet);
				builder.append(" ");
				builder.append(req.fullRequest);
//				builder.append(" ");
//				builder.append(req.keepAliveMax);
				builder.append(" ");
				builder.append(req.requestCount);
				builder.append(" ");
				builder.append(req.url);
				if (req.requestData instanceof String) {
					String more = (String) req.requestData;
					if (more.indexOf("WLL") == -1) {
						builder.append("?");
						if (html) {
							builder.append(more.replaceAll("&", "&amp;"));
						} else {
							builder.append(more);
						}
					}
				}
				/*
				if (req.pipeKey != null) {
					SimplePipeRunnable pipe = SimplePipeHelper.getPipe(req.pipeKey);
					if (pipe != null) {
						String pipeName = pipe.getClass().getName();
						int index = pipeName.lastIndexOf('.');
						if (index != -1) {
							builder.append(" " + pipeName.substring(index + 1));
						} else {
							builder.append(" " + pipeName);
						}
					}
				}
				// */
//				if (req.v11) {
//					builder.append(" HTTP/1.1");
//				} else {
//					builder.append(" HTTP/1.0");
//				}
				if (idled) {
					builder.append(" ");
					builder.append(req);
					builder.append(" ");
					builder.append(req.socket);
				}
				if (type > 0) {
					builder.append(" ");
					switch (type) {
					case 1:
						builder.append(req.referer);
						break;
					case 2:
						builder.append(req.userAgent);
						break;
					case 3:
						builder.append(req.referer);
						builder.append(" ");
						builder.append(req.userAgent);
						break;
					}
				}
				if (html) builder.append("<br />");
				builder.append("\r\n");
			}
		}
		if (html) builder.append("<br />");
		builder.append("\r\n");
		builder.append("Active count: ");
		builder.append(idx);
		if (html) builder.append("<br />");
		builder.append("\r\n");
		if (idled) {
			builder.append("Idled HTTP requests:");
			if (html) builder.append("<br />");
			builder.append("\r\n");
			idx = 0;
			for (int i = 0; i < requests.length; i++) {
				HttpRequest req = requests[i];
				if (req.done) {
					idx++;
					if (!listRequests) {
						continue;
					}
					if (html) builder.append("<div class=\"time-span\">");
					builder.append(idx);
					builder.append(": ");
					builder.append((now - req.created + 999) / 1000);
					builder.append("s");
					if (html) builder.append("</div>");
					builder.append(" ");
					if (html) builder.append("<div class=\"ip-span\">");
					builder.append(req.remoteIP);
					if (html) builder.append("</div>");
					builder.append(" ");
					if (html) builder.append("<div class=\"host-span\">");
					builder.append(req.host);
					if (html) builder.append("</div>");
//					builder.append(" ");
//					if (html) builder.append("<div class=\"method-span\">");
//					builder.append(req.method);
//					if (html) builder.append("</div>");
					builder.append(" ");
					builder.append(req.comet);
					builder.append(" ");
					builder.append(req.fullRequest);
					builder.append(" ");
					builder.append(req.keepAliveMax);
					builder.append(" ");
					builder.append(req.requestCount);
					builder.append(" ");
					builder.append(req.url);
					if (req.requestData instanceof String) {
						String more = (String) req.requestData;
						if (more.indexOf("WLL") == -1) {
							builder.append("?");
							if (html) {
								builder.append(more.replaceAll("&", "&amp;"));
							} else {
								builder.append(more);
							}
						}
					}
//					if (req.v11) {
//						builder.append(" HTTP/1.1");
//					} else {
//						builder.append(" HTTP/1.0");
//					}
					if (idled) {
						builder.append(" ");
						builder.append(req);
						builder.append(" ");
						builder.append(req.socket);
					}
					if (type > 0) {
						builder.append(" ");
						switch (type) {
						case 1:
							builder.append(req.referer);
							break;
						case 2:
							builder.append(req.userAgent);
							break;
						case 3:
							builder.append(req.referer);
							builder.append(" ");
							builder.append(req.userAgent);
							break;
						}
					}
					if (html) builder.append("<br />");
					builder.append("\r\n");
				}
			}

			builder.append("Idled count: ");
			builder.append(idx);
			if (html) builder.append("<br />");
			builder.append("\r\n");
		}

		if (html) builder.append("</body></html>");

		return builder.toString();
	}

	public static String getSystemThreadStacks() {
		StringBuilder builder = new StringBuilder();
		try {
			// Get runtime information
			RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

			// Handle thread info
			ThreadMXBean threads = ManagementFactory.getThreadMXBean();
			
			builder.append("thread-count : "
					+ Long.toString(threads.getThreadCount()) + "\r\n");
			builder.append("total-started-thread-count : "
					+ Long.toString(threads.getTotalStartedThreadCount())
					+ "\r\n");
			builder.append("daemon-thread-count : "
					+ Long.toString(threads.getDaemonThreadCount()) + "\r\n");
			builder.append("peak-thread-count : "
					+ Long.toString(threads.getPeakThreadCount()) + "\r\n");
			builder.append("\r\n\r\n");
			long totalCpuTime = 0l;
			long totalUserTime = 0l;

			// Parse each thread
			ThreadInfo[] threadInfos = threads.getThreadInfo(threads.getAllThreadIds(), 30);
			for (int i = 0; i < threadInfos.length; i++) {
				ThreadInfo thread = threadInfos[i];
				if (thread == null) {
					continue;
				}
				long threadId = thread.getThreadId();
				long cpuTime = threads.getThreadCpuTime(threadId) / 1000000l;
				builder.append("Thread \"" + thread.getThreadName() + "\" (" + cpuTime + "ms)\r\n");
				// Update our aggregate values
				totalCpuTime += threads.getThreadCpuTime(threadId);
				totalUserTime += threads.getThreadUserTime(threadId);
				
				builder.append("id : " + Long.toString(threadId)
						+ "\r\n");
				builder.append("name : " + thread.getThreadName() + "\r\n");
				
				builder.append("cpu-time : "
						+ Long.toString(cpuTime) + "ms\r\n");
				builder.append("user-time : "
						+ Long.toString(threads
										.getThreadUserTime(threadId) / 1000000l)
						+ "ms\r\n");
				builder.append("blocked-count : "
						+ Long.toString(thread.getBlockedCount())
						+ "\r\n");
				builder.append("blocked-time : "
						+ Long.toString(thread.getBlockedTime())
						+ "ms\r\n");
				builder.append("waited-count : "
						+ Long.toString(thread.getWaitedCount())
						+ "\r\n");
				builder.append("waited-time : "
						+ Long.toString(thread.getWaitedTime())
						+ "ms\r\n");

				builder.append("Stack Trace:\r\n");
				StackTraceElement[] stackTrace = thread.getStackTrace();
				for (int j = 0; j < stackTrace.length; j++) {
					StackTraceElement st = stackTrace[j];
					builder.append(j + " " + st.getClassName() + "#" + st.getMethodName() + "(" + st.getFileName() + ":" + st.getLineNumber() + ")\r\n");
				}
				builder.append("\r\n\r\n");
			}
			long totalCpuTimeMs = totalCpuTime / 1000000l;
			long totalUserTimeMs = totalUserTime / 1000000l;
			builder.append("\r\n\r\n");
			builder.append("total-cpu-time : " + Long.toString(totalCpuTimeMs)
					+ "ms\r\n");
			builder.append("total-user-time : " + Long.toString(totalUserTimeMs)
					+ "ms\r\n");

			// Compute thread percentages
			long uptime = runtime.getUptime();
			builder.append("up-time : " + Long.toString(uptime) + "ms\r\n");
			double cpuPercentage = ((double) totalCpuTimeMs / (double) uptime) * 100.0;
			double userPercentage = ((double) totalUserTimeMs / (double) uptime) * 100.0;
			builder.append("total-cpu-percent : " + Double.toString(cpuPercentage)
					+ "%\r\n");
			builder.append("total-user-percent : " + Double.toString(userPercentage)
					+ "%\r\n");

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return builder.toString();

	}
	
	public static String getSystemThreadInformation(long tid) {
		String output;
		StringBuilder builder = new StringBuilder();
		try {
			// Get runtime information
			RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

			// Handle thread info
			ThreadMXBean threads = ManagementFactory.getThreadMXBean();
			
			if (tid == -1) {
				builder.append("thread-count : "
						+ Long.toString(threads.getThreadCount()) + "\r\n");
				builder.append("total-started-thread-count : "
						+ Long.toString(threads.getTotalStartedThreadCount())
						+ "\r\n");
				builder.append("daemon-thread-count : "
						+ Long.toString(threads.getDaemonThreadCount()) + "\r\n");
				builder.append("peak-thread-count : "
						+ Long.toString(threads.getPeakThreadCount()) + "\r\n");
				builder.append("\r\n\r\n");
				long totalCpuTime = 0l;
				long totalUserTime = 0l;

				// Parse each thread
				ThreadInfo[] threadInfos = threads.getThreadInfo(threads.getAllThreadIds());
				for (int i = 0; i < threadInfos.length; i++) {
					ThreadInfo thread = threadInfos[i];
					if (thread == null) {
						continue;
					}
					long threadId = thread.getThreadId();
					long cpuTime = threads.getThreadCpuTime(threadId) / 1000000l;
					builder.append("<a href=\""  + Long.toString(threadId) + "\">Thread \"" + thread.getThreadName() + "\" (" + cpuTime + "ms)</a>\r\n");
					// Update our aggregate values
					totalCpuTime += threads.getThreadCpuTime(threadId);
					totalUserTime += threads.getThreadUserTime(threadId);
					
					//System.out.println(getSystemThreadInformation(threadId));
				}
				long totalCpuTimeMs = totalCpuTime / 1000000l;
				long totalUserTimeMs = totalUserTime / 1000000l;
				builder.append("\r\n\r\n");
				builder.append("total-cpu-time : " + Long.toString(totalCpuTimeMs)
						+ "ms\r\n");
				builder.append("total-user-time : " + Long.toString(totalUserTimeMs)
						+ "ms\r\n");
	
				// Compute thread percentages
				long uptime = runtime.getUptime();
				builder.append("up-time : " + Long.toString(uptime) + "ms\r\n");
				double cpuPercentage = ((double) totalCpuTimeMs / (double) uptime) * 100.0;
				double userPercentage = ((double) totalUserTimeMs / (double) uptime) * 100.0;
				builder.append("total-cpu-percent : " + Double.toString(cpuPercentage)
						+ "%\r\n");
				builder.append("user-cpu-percent : " + Double.toString(userPercentage)
						+ "%\r\n");

				MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
				MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
				MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
				
				builder.append("\r\n\r\n");
				builder.append("memory-heap-max : " + Long.toString(heap.getMax() / 1024) + "kb\r\n");
				builder.append("memory-heap-used : " + Long.toString(heap.getUsed() / 1024) + "kb\r\n");
				builder.append("memory-heap-committed : " + Long.toString(heap.getCommitted() / 1024) + "kb\r\n");
				builder.append("memory-non-heap-max : " + Long.toString(nonHeap.getMax() / 1024) + "kb\r\n");
				builder.append("memory-non-heap-used : " + Long.toString(nonHeap.getUsed() / 1024) + "kb\r\n");
				builder.append("memory-non-heap-committed : " + Long.toString(nonHeap.getCommitted() / 1024) + "kb\r\n");
			} else {
				ThreadInfo thread = threads.getThreadInfo(tid, 30);
				if (thread == null) {
					builder.append("No such thread.\r\n");
				} else {
					long threadId = thread.getThreadId();
					long cpuTime = threads.getThreadCpuTime(threadId) / 1000000l;
					builder.append("id : " + Long.toString(threadId)
							+ "\r\n");
					builder.append("name : " + thread.getThreadName() + "\r\n");
					
					builder.append("cpu-time : "
							+ Long.toString(cpuTime) + "ms\r\n");
					builder.append("user-time : "
							+ Long.toString(threads
											.getThreadUserTime(threadId) / 1000000l)
							+ "ms\r\n");
					builder.append("blocked-count : "
							+ Long.toString(thread.getBlockedCount())
							+ "\r\n");
					builder.append("blocked-time : "
							+ Long.toString(thread.getBlockedTime())
							+ "ms\r\n");
					builder.append("waited-count : "
							+ Long.toString(thread.getWaitedCount())
							+ "\r\n");
					builder.append("waited-time : "
							+ Long.toString(thread.getWaitedTime())
							+ "ms\r\n");

					builder.append("Stack Trace:\r\n");
					StackTraceElement[] stackTrace = thread.getStackTrace();
					for (int j = 0; j < stackTrace.length; j++) {
						StackTraceElement st = stackTrace[j];
						builder.append(j + " " + st.getClassName() + "#" + st.getMethodName() + "(" + st.getFileName() + ":" + st.getLineNumber() + ")\r\n");
					}
					builder.append("\r\n\r\n");
				}

			}
			
		} catch (Exception e) {
			e.printStackTrace();
			builder.append(e.getMessage());
		}
		output = builder.toString();
		return output;
	}

	
	public static String printAllKeyStatuses(IPiledServer server, boolean html) {
		StringBuilder builder = new StringBuilder();
		if (server != null && server.getSelector() != null) {
			Selector selector = server.getSelector();
			int count = 0;
			Set<SelectionKey> keys = selector.keys();
			int i = 0;
			for (Iterator<SelectionKey> itr = keys.iterator(); itr
					.hasNext();) {
				SelectionKey k = (SelectionKey) itr.next();
				if (!k.isValid()) {
					builder.append("What Key " + k + " invalid.");
					if (html) builder.append("<br />");
					builder.append("\r\n");
					try {
						k.cancel();
					} catch (Throwable e) {
						e.printStackTrace();
					}
					try {
						k.channel().close();
					} catch (Throwable e) {
						e.printStackTrace();
					}
					continue;
				}
				if ((k.interestOps() & k.readyOps()) != 0 && (k.readyOps() & (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) != 0) {
					count++;
				}
				builder.append(i + ": " + k.interestOps() + " | " + k.readyOps()
						+ " | " + k.isAcceptable()
						+ " | " + k.isConnectable()
						+ " | " + k.isReadable()
						+ " | " + k.isWritable()
						+ " | " + k.isValid() + " // " + k + " " + k.channel());
				if (html) builder.append("<br />");
				builder.append("\r\n");
				i++;
			}
			builder.append("No new keys are ready, manually selected: " + count + " / " + keys.size() + " // " + new Date());
		} else {
			builder.append("Not initialized.");
		}
		if (html) builder.append("<br />");
		builder.append("\r\n");
		return builder.toString();
	}

}
