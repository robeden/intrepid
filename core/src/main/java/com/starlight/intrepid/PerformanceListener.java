// Copyright (c) 2010 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
// * Neither the name of Intrepid nor the
// names of its contributors may be used to endorse or promote products
// derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.starlight.intrepid;

import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.message.IMessage;

import java.lang.reflect.Method;
import java.net.SocketAddress;


/**
 * Interface that can be used to gather performance information
 */
public interface PerformanceListener {
	default void remoteCallStarted( VMID instance_vmid, long time, int call_id,
		VMID destination_vmid, int object_id, int method_id, Method method, Object[] args,
		UserContextInfo user_context, String persistent_name ) {}
	default void remoteCallCompleted( VMID instance_vmid, long time, int call_id,
		Object result, boolean result_was_thrown, Long server_time ) {}


	default void inboundRemoteCallStarted( VMID instance_vmid, long time, int call_id,
		VMID source_vmid, int object_id, int method_id, Method method, Object[] args,
		UserContextInfo user_context, String persistent_name ) {}
	default void inboundRemoteCallCompleted( VMID instance_vmid, long time, int call_id,
		Object result, boolean result_was_thrown ) {}


	default void virtualChannelOpened( VMID instance_vmid, VMID peer_vmid,
		short channel_id ) {}
	default void virtualChannelClosed( VMID instance_vmid, VMID peer_vmid,
		short channel_id ) {}
	default void virtualChannelDataReceived( VMID instance_vmid, VMID peer_vmid,
		short channel_id, int bytes ) {}
	default void virtualChannelDataSent( VMID instance_vmid, VMID peer_vmid,
		short channel_id, short message_id, int bytes ) {}
	default void virtualChannelDataAckSent( VMID instance_vmid, VMID peer_vmid,
		short channel_id, short message_id, int new_window ) {}


	default void messageSent( VMID destination_vmid, IMessage message ) {}
	default void messageReceived( VMID source_vmid, IMessage message ) {}
	default void invalidMessageReceived( SocketAddress peer, IMessage message ) {}


	default void leaseInfoUpdated( VMID vmid, int object_id, String delegate_tostring,
		boolean holding_strong_reference, int leasing_vm_count, boolean renew,
		boolean release ) {}
	default void leasedObjectRemoved( VMID vmid, int object_id ) {}
}