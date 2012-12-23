package com.starlight.intrepid.demo.lease;

import com.starlight.intrepid.PerformanceListener;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.LeaseIMessage;
import com.starlight.intrepid.message.LeaseReleaseIMessage;

import java.lang.reflect.Method;


/**
*
*/
class LeaseListener implements PerformanceListener {

	@Override
	public void leasedObjectRemoved( VMID vmid, int object_id ) {
		System.out.println( "*** Leased object removed: " + object_id + "***" );
	}

	@Override
	public void leaseInfoUpdated( VMID vmid, int object_id, String delegate_tostring,
		boolean holding_strong_reference, int leasing_vm_count, boolean renew,
		boolean release ) {

		System.out.println( "*** Lease info updates: " + vmid + " oid: " + object_id +
			" delegate_tostring: " + delegate_tostring +
			" strong: " + holding_strong_reference +
			" count: " + leasing_vm_count + " renew: " + renew +
			" release: " + release );
	}


	@Override
	public void inboundRemoteCallCompleted( VMID instance_vmid, long time,
		int call_id, Object result, boolean result_was_thrown ) {}

	@Override
	public void inboundRemoteCallStarted( VMID instance_vmid, long time, int call_id,
		VMID source_vmid, int object_id, int method_id, Method method, Object[] args,
		UserContextInfo user_context, String persistent_name ) {}

	@Override
	public void messageReceived( VMID source_vmid, IMessage message ) {
		if ( message instanceof LeaseIMessage ||
			message instanceof LeaseReleaseIMessage ) {

			System.out.println( "<<< Message received: " + message );
		}
	}

	@Override
	public void messageSent( VMID destination_vmid, IMessage message ) {
		if ( message instanceof LeaseIMessage ||
			message instanceof LeaseReleaseIMessage ) {

			System.out.println( ">>> Message sent: " + message );
		}
	}

	@Override
	public void remoteCallCompleted( VMID instance_vmid, long time, int call_id,
		Object result, boolean result_was_thrown, Long server_time ) {}

	@Override
	public void remoteCallStarted( VMID instance_vmid, long time, int call_id,
		VMID destination_vmid, int object_id, int method_id, Method method,
		Object[] args, UserContextInfo user_context, String persistent_name ) {}

	@Override
	public void virtualChannelClosed( VMID instance_vmid, VMID peer_vmid,
		short channel_id ) {}

	@Override
	public void virtualChannelDataReceived( VMID instance_vmid, VMID peer_vmid,
		short channel_id, int bytes ) {}

	@Override
	public void virtualChannelDataSent( VMID instance_vmid, VMID peer_vmid,
		short channel_id, int bytes ) {}

	@Override
	public void virtualChannelOpened( VMID instance_vmid, VMID peer_vmid,
		short channel_id ) {}
}
