package im.webuzz.piled.manager;

public class RewriteMatching extends RewriteItem {

	public String userAgent;
	
	public String remoteIP;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((remoteIP == null) ? 0 : remoteIP.hashCode());
		result = prime * result + ((userAgent == null) ? 0 : userAgent.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		RedirectMatching other = (RedirectMatching) obj;
		if (remoteIP == null) {
			if (other.remoteIP != null)
				return false;
		} else if (!remoteIP.equals(other.remoteIP))
			return false;
		if (userAgent == null) {
			if (other.userAgent != null)
				return false;
		} else if (!userAgent.equals(other.userAgent))
			return false;
		return true;
	}

}
