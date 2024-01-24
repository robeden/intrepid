// Copyright (c) 2010 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Intrepid nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
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

package com.starlight.intrepid.message;

import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.ConnectionArgs;

import java.io.Serializable;


/**
 *
 */
public class SessionInitIMessage implements IMessage {
	private final VMID initiator_vmid;
	private final Integer initiator_server_port;
	private final ConnectionArgs connection_args;
	private final byte min_protocol_version;
	private final byte pref_protocol_version;

	private final Serializable reconnect_token;

	// The rate (in seconds) at which Invoke acks will be expected.
	// Must be greater than zero. A value of 0 means the server may choose.
	private final byte requested_ack_rate_sec;



	/**
	 * @param requested_ack_rate_sec    The rate (in seconds) at which Invoke ack's will
	 *                                  be expected. Must be greater than zero. A value
	 *                                  of 0 means the server may choose.
	 */
	public SessionInitIMessage( VMID initiator_vmid, Integer initiator_server_port,
		ConnectionArgs connection_args, byte min_protocol_version,
		byte pref_protocol_version, Serializable reconnect_token,
		byte requested_ack_rate_sec ) {

		this.initiator_vmid = initiator_vmid;
		this.initiator_server_port = initiator_server_port;
		this.connection_args = connection_args;
		this.min_protocol_version = min_protocol_version;
		this.pref_protocol_version = pref_protocol_version;
		this.reconnect_token = reconnect_token;
		this.requested_ack_rate_sec = requested_ack_rate_sec;
	}

	@Override
	public IMessageType getType() {
		return IMessageType.SESSION_INIT;
	}


	public ConnectionArgs getConnectionArgs() {
		return connection_args;
	}

	public byte getMinProtocolVersion() {
		return min_protocol_version;
	}

	public byte getPrefProtocolVersion() {
		return pref_protocol_version;
	}

	public VMID getInitiatorVMID() {
		return initiator_vmid;
	}

	public Integer getInitiatorServerPort() {
		return initiator_server_port;
	}

	public Serializable getReconnectToken() {
		return reconnect_token;
	}

	public byte getRequestedAckRateSec() {
		return requested_ack_rate_sec;
	}



	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "SessionInitIMessage" );
		sb.append( "{connection_args=" ).append( connection_args );
		sb.append( ", initiator_vmid=" ).append( initiator_vmid );
		sb.append( ", initiator_server_port=" ).append( initiator_server_port );
		sb.append( ", min_protocol_version=" ).append( min_protocol_version );
		sb.append( ", pref_protocol_version=" ).append( pref_protocol_version );
		sb.append( ", reconnect_token=" ).append( reconnect_token );
		sb.append( ", requested_ack_rate_sec=" ).append( requested_ack_rate_sec );
		sb.append( '}' );
		return sb.toString();
	}
}
