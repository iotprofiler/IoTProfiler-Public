package dpcollector.manager;

import java.util.Set;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class MyResourceManager {
	private static MyResourceManager singleton;
	private MultiMap<String,String> clazz2URLs;
	private MultiMap<String,String> method2URLs;
	private MultiMap<String, String> callerMap;

	public static MyResourceManager getInstance() {
		if (singleton == null) {
			singleton = new MyResourceManager();
		}
		return singleton;
	}
	
	private MyResourceManager() {
		this.clazz2URLs = new HashMultiMap<String, String>();
		this.method2URLs = new HashMultiMap<String, String>();
		this.callerMap = new HashMultiMap<String, String>();
	}
	
	public void addCallerEntry(String callerSig, String calleeSig) {
		this.callerMap.put(calleeSig, callerSig);
	}
	
	public MultiMap<String, String> getCallerMap() {
		return callerMap;
	}

	public MultiMap<String, String> getClazz2URLs() {
		return clazz2URLs;
	}
	
	public MultiMap<String, String> getMethod2URLs() {
		return method2URLs;
	}
	
	public Set<String> getURLs() {
		return this.method2URLs.values();
	}
	
	public void addUrl(String url, String hostClazz, String hostMethod) {
		this.method2URLs.put(hostMethod, url);
		
		if (hostMethod.contains("<clinit>") ||
				hostMethod.contains("<init>")) {
			this.clazz2URLs.put(hostClazz, url);
		}
	}
}
