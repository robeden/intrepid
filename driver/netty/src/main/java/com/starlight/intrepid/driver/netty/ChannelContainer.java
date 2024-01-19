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

package com.starlight.intrepid.driver.netty;

import com.logicartisan.common.core.thread.ObjectSlot;
import com.starlight.intrepid.auth.ConnectionArgs;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Objects;


/**
*
*/
class ChannelContainer {
	private static final Logger LOG = LoggerFactory.getLogger(ChannelContainer.class);


	private final SocketAddress socket_address;
	private final ConnectionArgs connection_args;

	private final ObjectSlot<Channel> channel_slot = new ObjectSlot<>();

	private volatile boolean canceled = false;

	ChannelContainer(SocketAddress socket_address, ConnectionArgs connection_args ) {
		Objects.requireNonNull(socket_address);

		this.socket_address = socket_address;
		this.connection_args = connection_args;
	}

	/**
	 * Try to get the session, without waiting for it to be set if it isn't already.
	 */
	public Channel getChannel() {
		return channel_slot.get();
	}

	/**
	 * Try to get the session, waiting if necessary.
	 *
	 * @param timeout	The time (in milliseconds) to wait for the session if it isn't
	 *                 	immediately available.
	 */
	public Channel getChannel(long timeout ) {
		try {
			return channel_slot.waitForValue( timeout );
		}
		catch ( InterruptedException e ) {
			return null;
		}
	}

	/**
	 * Set a new session and return the old one (if any).
	 */
	public Channel setChannel(Channel channel ) {
		LOG.debug("Channel for {} container ({}) set: {}",
			System.identityHashCode(this), socket_address, channel);
//		System.out.println( "Session set (" + host_and_port + "): " + session );
		return channel_slot.getAndSet( channel );
	}


	public void setCanceled() {
		canceled = true;
	}

	public boolean isCanceled() {
		return canceled;
	}


	public SocketAddress getSocketAddress() {
		return socket_address;
	}

	public ConnectionArgs getConnectionArgs() {
		return connection_args;
	}

	@Override
	public String toString() {
        return "ContextContainer" +
            "{canceled=" + canceled +
            ", host_and_port=" + socket_address +
            ", connection_args=" + connection_args +
            ", session_slot=" + channel_slot +
            '}';
	}
}
