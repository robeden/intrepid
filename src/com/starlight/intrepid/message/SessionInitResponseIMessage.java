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

import com.starlight.ValidationKit;
import com.starlight.intrepid.VMID;

import java.io.Serializable;


/**
 * Packet sent in response to a {@link SessionInitIMessage}.
 * <strong>if the connection is successful</strong>. If the connection was not successful
 * a {@link SessionCloseIMessage} will be sent instead.
 */
public class SessionInitResponseIMessage implements IMessage {
	private final VMID responder_vmid;
	private final Integer responder_server_port;
	private final byte protocol_version;
	private final Serializable reconnect_token;
	private final byte ack_rate_sec;



	/**
	 * @param ack_rate_sec      The rate (in seconds) at which acks for invocations may
	 *                          be expected. Must be greater than 0.
	 */
	public SessionInitResponseIMessage( VMID responder_vmid, Integer responder_server_port,
		byte protocol_version, Serializable reconnect_token, byte ack_rate_sec ) {

		ValidationKit.checkGreaterThan( ack_rate_sec, 0, "ack_rate_sec" );

		this.responder_vmid = responder_vmid;
		this.responder_server_port = responder_server_port;
		this.protocol_version = protocol_version;
		this.reconnect_token = reconnect_token;
		this.ack_rate_sec = ack_rate_sec;
	}

	@Override
	public IMessageType getType() {
		return IMessageType.SESSION_INIT_RESPONSE;
	}

	public byte getProtocolVersion() {
		return protocol_version;
	}

	public VMID getResponderVMID() {
		return responder_vmid;
	}

	public Integer getResponderServerPort() {
		return responder_server_port;
	}

	/**
	 * If non-null, this token should be provided when reconnected. This token can be
	 * used by the server for re-authentication during the reconnection process.
	 *
	 * The reconnect token is considered optional data. This means that it will silently
	 * be dropped if there is a problem, for example, deserializing the object on the
	 * client. In this case, reconnection attempts will simply not have the token and the
	 * server should deal with that accordingly.
	 */
	public Serializable getReconnectToken() {
		return reconnect_token;
	}

	/**
	 * The rate (in seconds) at which acks for invocations may be expected. Must be
	 * greater than zero.
	 */
	public byte getAckRateSec() {
		return ack_rate_sec;
	}



	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "SessionInitResponseIMessage" );
		sb.append( "{protocol_version=" ).append( protocol_version );
		sb.append( ", responder_vmid=" ).append( responder_vmid );
		sb.append( ", responder_server_port=" ).append( responder_server_port );
		sb.append( ", reconnect_token=" ).append( reconnect_token );
		sb.append( ", ack_rate_sec=" ).append( ack_rate_sec );
		sb.append( '}' );
		return sb.toString();
	}
}
