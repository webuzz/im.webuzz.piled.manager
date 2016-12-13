package im.webuzz.piled.manager;

import net.sf.j2s.ajax.SimpleRPCRunnable;

public class ServerStatus extends SimpleRPCRunnable {

	public boolean ok;
	
	@Override
	public void ajaxRun() {
		ok = true;
	}

}
