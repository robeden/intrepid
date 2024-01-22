package com.starlight.intrepid;

import gnu.trove.map.TIntObjectMap;

import java.lang.reflect.Method;


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


	public static TIntObjectMap<Method> generateMethodMap( Class clazz ) {
		return MethodMap.generateMethodMap( clazz );
	}
}
