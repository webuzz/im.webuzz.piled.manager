package im.webuzz.piled.manager;

public class RewriteRule {

	/*
	 {
	 matching: {
	 	host:null,
	 	url:"",
	 	query:""
	 },
	 replacing: {
	 	host:null,
	 	url:"",
	 	query:"",
	 },
	 }
	 
	 {
	 	matching:{url:"^\\/\\.wellknown\\/"},
	 	replacing:null, // ignore redirect for matched requests
	 }
	 { // redirect all www.*.com to *.com
	 	matching:{host:"^www\\.(.+)$"},
	 	replacing:{host:"{$1}"},
	 }
	 */
	
	public RewriteMatching matching;
	public RewriteItem replacing;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((matching == null) ? 0 : matching.hashCode());
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
		RewriteRule other = (RewriteRule) obj;
		if (matching == null) {
			if (other.matching != null)
				return false;
		} else if (!matching.equals(other.matching))
			return false;
		if (replacing == null) {
			if (other.replacing != null)
				return false;
		} else if (!replacing.equals(other.replacing))
			return false;
		return true;
	}

}
