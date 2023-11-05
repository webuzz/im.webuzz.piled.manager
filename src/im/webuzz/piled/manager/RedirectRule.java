package im.webuzz.piled.manager;

public class RedirectRule {

	/*
	 {
	 matching: {
	 	protocol:"",
	 	host:null,
	 	port:-1,
	 	url:"",
	 	query:""
	 },
	 replacing: {
	 	protocol:"",
	 	host:null,
	 	port:-1,
	 	url:"",
	 	query:"",
	 },
	 permanent:true,
	 cookie:true,
	 }
	 
	 {
	 	matching:{url:"^\\/\\.wellknown\\/"},
	 	replacing:null, // ignore redirect for matched requests
	 }
	 { // redirect all www.*.com to *.com
	 	matching:{host:"^www\\.(.+)$"},
	 	replacing:{host:"{$1}"},
	 	permanent:true,
	 }
	 */
	
	public RedirectMatching matching;
	public RedirectItem replacing;
	
	public boolean permanent;
	public boolean cookie;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (cookie ? 1231 : 1237);
		result = prime * result + ((matching == null) ? 0 : matching.hashCode());
		result = prime * result + (permanent ? 1231 : 1237);
		result = prime * result + ((replacing == null) ? 0 : replacing.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RedirectRule other = (RedirectRule) obj;
		if (cookie != other.cookie)
			return false;
		if (matching == null) {
			if (other.matching != null)
				return false;
		} else if (!matching.equals(other.matching))
			return false;
		if (permanent != other.permanent)
			return false;
		if (replacing == null) {
			if (other.replacing != null)
				return false;
		} else if (!replacing.equals(other.replacing))
			return false;
		return true;
	}

}
