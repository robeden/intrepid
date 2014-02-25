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

package com.starlight.intrepid.spi;

import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.UserContextInfo;

import java.io.Serializable;
import java.net.SocketAddress;
import java.util.concurrent.ScheduledFuture;


/**
 * Interface to access attributes associated with the session.
 */
public interface SessionInfo {
	/**
	 * Get an attribute for the session.
	 *
	 * @param key		The key of the attribute.
	 */
	public Object getAttribute( Object key );

	/**
	 * Set an attribute for the session.
	 *
	 * @param key		The key of the attribute. If the key is a String, it should not
	 * 					start with a period ('.') as those are reserved for the SPI
	 * 					implementation.
	 * @param value		The value of the attribute.
	 *
	 * @return			The old value of the attribute if there was one, otherwise null.
	 */
	public Object setAttribute( Object key, Object value );


	/**
	 * Returns the VMID of the session, if available.
	 */
	public VMID getVMID();

	/**
	 * Sets the VMID and the invoke ack rate for the session. If the peer doesn't support
	 * method ack, the value for ack_rate_sec is undefined.
	 */
	public void setVMID( VMID vmid, byte ack_rate_sec );


	/**
	 * Returns the protocol version of the session.
	 */
	public Byte getProtocolVersion();

	/**
	 * Sets the protocol version for the session.
	 */
	public void setProtocolVersion( Byte version );


	/**
	 * Return the user context for the session, if any.
	 */
	public UserContextInfo getUserContext();

	/**
	 * Set the user context for the session.
	 */
	public void setUserContext( UserContextInfo user_context );


	/**
	 * Returns the remote address of the session.
	 */
	public SocketAddress getRemoteAddress();


	/**
	 * Returns the SPI-dependent "source" for the session. In the normal case, this would
	 * be a SocketChannel or Socket. This is optional and my return null if not supported.
	 */
	public Object getSessionSource();


	/**
	 * Set the server port used by the remote peer.
	 */
	public void setPeerServerPort( Integer port );

	/**
	 * Get the server port used by the remote peer.
	 *
	 * @see #setPeerServerPort(Integer)
	 */
	public Integer getPeerServerPort();


	/**
	 * Return the reconnect token for the session.
	 * @see com.starlight.intrepid.auth.TokenReconnectAuthenticationHandler
	 */
	public Serializable getReconnectToken();

	public void setReconnectToken( Serializable reconnect_token );


	/**
	 * This is the Future for the timer that regenerates reconnect tokens for the session,
	 * if applicable.
	 */
	public ScheduledFuture<?> getReconnectTokenRegenerationTimer();

	public void setReconnectTokenRegenerationTimer( ScheduledFuture<?> timer );


	/**
	 * Returns the ack rate (in seconds).
	 */
	public Byte getAckRateSec();
}
