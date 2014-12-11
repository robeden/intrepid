package com.starlight.intrepid;

import com.starlight.intrepid.Proxy;
import com.starlight.intrepid.VMID;


/**
 *
 */
public class IntrepidTestProxyAccess {
	public static int getObjectID( Object proxy_obj ) {
		Proxy proxy = ( Proxy ) proxy_obj;
		return proxy.__intrepid__getObjectID();
	}

	public static String getPersistentName( Object proxy_obj ) {
		Proxy proxy = ( Proxy ) proxy_obj;
		return proxy.__intrepid__getPersistentName();
	}

	public static VMID getHostVMID( Object proxy_obj ) {
		Proxy proxy = ( Proxy ) proxy_obj;
		return proxy.__intrepid__getHostVMID();
	}
}
