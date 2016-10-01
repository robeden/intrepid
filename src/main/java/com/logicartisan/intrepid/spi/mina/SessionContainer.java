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

package com.logicartisan.intrepid.spi.mina;

import com.logicartisan.intrepid.auth.ConnectionArgs;
import com.starlight.ValidationKit;
import com.starlight.thread.ObjectSlot;
import org.apache.mina.core.session.IoSession;


/**
*
*/
class SessionContainer {
	private final HostAndPort host_and_port;
	private final ConnectionArgs connection_args;

	private final ObjectSlot<IoSession> session_slot = new ObjectSlot<IoSession>();

	private volatile boolean canceled = false;

	SessionContainer( HostAndPort host_and_port, ConnectionArgs connection_args ) {
		ValidationKit.checkNonnull( host_and_port, "host_and_port" );

		this.host_and_port = host_and_port;
		this.connection_args = connection_args;
	}


	/**
	 * Try to get the session, without waiting for it to be set if it isn't already.
	 */
	public IoSession getSession() {
		return session_slot.get();
	}

	/**
	 * Try to get the session, waiting if necessary.
	 *
	 * @param timeout	The time (in milliseconds) to wait for the session if it isn't
	 *                 	immediately available.
	 */
	public IoSession getSession( long timeout ) {
		try {
			return session_slot.waitForValue( timeout );
		}
		catch ( InterruptedException e ) {
			return null;
		}
	}

	/**
	 * Set a new session and return the old one (if any).
	 */
	public IoSession setSession( IoSession session ) {
//		System.out.println( "Session set (" + host_and_port + "): " + session );
		return session_slot.getAndSet( session );
	}


	public boolean clearIfExpectedSession( IoSession session ) {
		return session_slot.compareAndSet( session, null );
	}


	public void setCanceled() {
		canceled = true;
	}

	public boolean isCanceled() {
		return canceled;
	}


	public HostAndPort getHostAndPort() {
		return host_and_port;
	}

	public ConnectionArgs getConnectionArgs() {
		return connection_args;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "SessionContainer" );
		sb.append( "{canceled=" ).append( canceled );
		sb.append( ", host_and_port=" ).append( host_and_port );
		sb.append( ", connection_args=" ).append( connection_args );
		sb.append( ", session_slot=" ).append( session_slot );
		sb.append( '}' );
		return sb.toString();
	}
}
