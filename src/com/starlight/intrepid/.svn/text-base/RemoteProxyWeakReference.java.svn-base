package com.starlight.intrepid;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;


/**
*
*/
class RemoteProxyWeakReference
	extends WeakReference<ProxyInvocationHandler> {

	final VMID vmid;
	final int object_id;

	public RemoteProxyWeakReference( ProxyInvocationHandler referent,
		ReferenceQueue<? super ProxyInvocationHandler> q ) {

		super( referent, q );

		vmid = referent.getVMID();
		object_id = referent.getObjectID();
	}
}
