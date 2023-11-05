package im.webuzz.piled.manager;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.webuzz.pilet.HttpLoggingUtils;
import im.webuzz.pilet.HttpRequest;
import im.webuzz.pilet.HttpResponse;
import im.webuzz.pilet.HttpWorkerUtils;
import im.webuzz.pilet.IPilet;

public class RedirectPilet implements IPilet {

	@Override
	public boolean service(HttpRequest req, HttpResponse resp) {
		Map<String, RedirectRule[]> redirectings = RedirectConfig.redirectings;
		if (redirectings == null) return false;
		
		for (int k = 0; k < 2; k++) {
			String host = k == 0 ? "all" : (req.host != null ? req.host : "127.0.0.1"); 
			RedirectRule[] rules = redirectings.get(host);
			if (rules == null || rules.length == 0) continue;
			for (int i = 0; i < rules.length; i++) {
				RedirectRule r = rules[i];
				if (r == null) continue;
				if (r.matching == null) continue; // ignore this rule
				RedirectItem result = checkMatchingReplacing(req, resp, r.matching, r.replacing);
				if (result == null) continue; // not matched
				if (r.replacing == null) return false; // skip matched request
				String newURL = toFullURL(result, req, resp);
				if (r.permanent) {
					if (r.cookie) {
						HttpWorkerUtils.redirect(newURL, req.cookies, req, resp);
					} else {
						HttpWorkerUtils.redirect(newURL, req, resp);
					}
				} else {
					if (r.cookie) {
						HttpWorkerUtils.found(newURL, req.cookies, req, resp);
					} else {
						HttpWorkerUtils.found(newURL, req, resp);
					}
				}
				HttpLoggingUtils.addLogging(req.host, req, resp, null, r.permanent ? 301 : 302, 0);
				return true;
			}
		}
		return false;
	}

	static RedirectItem checkMatchingReplacing(HttpRequest req, HttpResponse resp, RedirectMatching matching, RedirectItem replacing) {
		boolean compared = false;
		RedirectItem result = new RedirectItem();
		if (matching.protocol != null && matching.protocol.length() > 0) {
			String protocol = resp.worker.getServer().isSSLEnabled() ? "https" : "http";
			if (!matching.protocol.equalsIgnoreCase(protocol)) return null; // Not matched
			compared = true;
			if (replacing != null && replacing.protocol != null && replacing.protocol.length() > 0) {
				result.protocol = replacing.protocol;
			} else {
				result.protocol = protocol;
			}
		} else if (replacing != null && replacing.protocol != null && replacing.protocol.length() > 0) {
			result.protocol = replacing.protocol;
		}
		
		if (matching.host != null && matching.host.length() > 0) {
			if (req.host == null) return null; // No host! not matched
			if (matching.host.charAt(0) != '^') { // plain text
				if (!req.host.startsWith(matching.host)) return null; // Not matched
				compared = true;
				if (replacing != null && replacing.host != null && replacing.host.length() > 0) {
					result.host = replacing.host; // replace with given full domain
				} else {
					result.host = req.host;
				}
			} else {
				try {
					Pattern pattern = Pattern.compile(matching.host);
					Matcher matcher = pattern.matcher(req.host);
					matcher.reset();
					if (!matcher.find()) return null; // Not matched
					compared = true;
					if (replacing != null && replacing.host != null && replacing.host.length() > 0) {
						StringBuffer sb = new StringBuffer();
						matcher.appendReplacement(sb, replacing.host);
						matcher.appendTail(sb);
						result.host = sb.toString();
					} else {
						result.host = req.host;
					}
				} catch (Exception e) {
					e.printStackTrace();
					return null; // not matching
				}
			}
		} else if (replacing != null && replacing.host != null && replacing.host.length() > 0) {
			result.host = replacing.host;
		}
		
		if (matching.port > 0) {
			int port = resp.worker.getServer().getPort();
			if (port != matching.port) return null; // Not matched
			compared = true;
			if (replacing != null && replacing.port > 0) {
				result.port = replacing.port;
			} else if (replacing.port < 0) {
				result.port = port;
			}
		} else if (replacing != null) {
			if (replacing.port > 0) {
				result.port = replacing.port;
			} else if (replacing.port < 0) {
				result.port = resp.worker.getServer().getPort();
			}
		}
		
		if (matching.url != null && matching.url.length() > 0) {
			if (matching.url.charAt(0) != '^') { // plain text
				if (!req.url.startsWith(matching.url)) return null; // Not matched
				compared = true;
				if (replacing != null && replacing.url != null && replacing.url.length() > 0) {
					result.url = replacing.url + req.url.substring(matching.url.length());
				} else {
					result.url = req.url;
				}
			} else {
				try {
					Pattern pattern = Pattern.compile(matching.url);
					Matcher matcher = pattern.matcher(req.url);
					matcher.reset();
					if (!matcher.find()) return null; // Not matched
					compared = true;
					if (replacing != null && replacing.url != null && replacing.url.length() > 0) {
						StringBuffer sb = new StringBuffer();
						matcher.appendReplacement(sb, replacing.url);
						matcher.appendTail(sb);
						result.url = sb.toString();
					} else {
						result.url = req.url;
					}
				} catch (Exception e) {
					e.printStackTrace();
					return null; // not matching
				}
			}
		} else if (replacing != null && replacing.url != null && replacing.url.length() > 0) {
			result.url = replacing.url;
		}
		
		if (matching.query != null && matching.query.length() > 0) {
			String query = req.requestQuery;
			if (query != null && query.length() > 0) {
				if (matching.query.charAt(0) != '^') { // plain text
					if (!query.startsWith(matching.query)) return null; // Not matched
					compared = true;
					if (replacing != null && replacing.query != null) {
						if (replacing.query.length() == 0) {
							result.query = ""; // empty query
						} else if (replacing.query.startsWith("?")) {
							// e.g. ?/archive/?mode=1 will result in URL: /archive/?mode=1
							int questionIndex = replacing.query.indexOf('?', 1);
							if (questionIndex != -1) {
								// keep
								result.query = replacing.query + query.substring(matching.query.length());
							} else {
								int nextQueryIndex = matching.query.length();
								if (query.charAt(nextQueryIndex) == '&') {
									nextQueryIndex++;
								}
								String leftQuery = query.substring(nextQueryIndex);
								if (leftQuery.length() > 0) {
									result.query = replacing.query + "?" + leftQuery;
								} else {
									result.query = replacing.query;
								}
							}
						} else { // replace query parameter
							result.query = replacing.query + query.substring(matching.query.length());
						}
					} else {
						result.query = query;
					}
				} else {
					try {
						Pattern pattern = Pattern.compile(matching.query);
						Matcher matcher = pattern.matcher(query);
						matcher.reset();
						if (!matcher.find()) return null; // Not matched
						compared = true;
						if (replacing != null && replacing.query != null) {
							if (replacing.query.length() > 0) {
								StringBuffer sb = new StringBuffer();
								matcher.appendReplacement(sb, replacing.query);
								matcher.appendTail(sb);
								result.query = sb.toString();
							} else {
								result.query = ""; // empty query
							}
						} else {
							result.query = query;
						}
					} catch (Exception e) {
						e.printStackTrace();
						return null; // not matching
					}
				}
			}
		} else if (replacing != null && replacing.query != null/* && replacing.query.length() > 0*/) {
			result.query = replacing.query;
		}
		
		if (matching.userAgent != null && matching.userAgent.length() > 0) {
			if (req.userAgent == null) return null; // No user agent! not matched
			if (matching.userAgent.charAt(0) != '^') { // plain text
				if (!req.userAgent.startsWith(matching.userAgent)) return null; // Not matched
				compared = true;
			} else {
				try {
					Pattern pattern = Pattern.compile(matching.userAgent);
					Matcher matcher = pattern.matcher(req.userAgent);
					matcher.reset();
					if (!matcher.find()) return null; // Not matched
					compared = true;
				} catch (Exception e) {
					e.printStackTrace();
					return null; // not matching
				}
			}
		}
		if (matching.remoteIP != null && matching.remoteIP.length() > 0) {
			if (req.remoteIP == null) return null; // No remote IP! not matched
			if (matching.remoteIP.charAt(0) != '^') { // plain text
				if (!req.remoteIP.startsWith(matching.remoteIP)) return null; // Not matched
				compared = true;
			} else {
				try {
					Pattern pattern = Pattern.compile(matching.remoteIP);
					Matcher matcher = pattern.matcher(req.remoteIP);
					matcher.reset();
					if (!matcher.find()) return null; // Not matched
					compared = true;
				} catch (Exception e) {
					e.printStackTrace();
					return null; // not matching
				}
			}
		}
		if (!compared) return null; // Not matched
		return result;
	}
	
	static String toFullURL(RedirectItem r, HttpRequest req, HttpResponse resp) {
		StringBuilder sb = new StringBuilder();
		boolean isHTTPS = false;
		if (r.protocol != null && r.protocol.length() > 0) {
			isHTTPS = "https".equalsIgnoreCase(r.protocol);
			sb.append(r.protocol);
		} else {
			isHTTPS = resp.worker.getServer().isSSLEnabled();
			String protocol = isHTTPS ? "https" : "http";
			sb.append(protocol);
		}
		sb.append("://");
		if (r.host != null && r.host.length() > 0) {
			sb.append(r.host);
		} else {
			String host = req.host != null ? req.host : "127.0.0.1"; 
			sb.append(host);
		}
		if (r.port > 0 && r.port != (isHTTPS ? 443 : 80)) {
			sb.append(":").append(r.port);
		} else if (r.port < 0) {
			int localPort = resp.worker.getServer().getPort();
			sb.append(":").append(localPort);
		}
		if (r.url != null && r.url.length() > 0) {
			sb.append(r.url);
		} else {
			sb.append(req.url);
		}
		if (r.query != null) {
			if (r.query.length() > 0) {
				if (r.query.charAt(0) == '?') {
					sb.append(r.query.substring(1));
				} else {
					if (sb.indexOf("?") == -1) {
						sb.append("?");
					}
					sb.append(r.query);
				}
			}
		} else {
			String query = req.requestQuery;
			if (query != null && query.length() > 0) {
				if (sb.indexOf("?") == -1) {
					sb.append("?");
				}
				sb.append(query);
			}
		}
		return sb.toString();
	}
	
}
