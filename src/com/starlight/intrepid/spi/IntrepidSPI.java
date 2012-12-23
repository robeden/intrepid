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

import com.starlight.intrepid.ConnectionListener;
import com.starlight.intrepid.PerformanceListener;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.exception.NotConnectedException;
import com.starlight.intrepid.message.IMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public interface IntrepidSPI {
	/**
	 * Connect to the given host.
	 *
	 * @param timeout		Time to wait for the connection. Note that this is a soft
	 * 						timeout, so it's guaranteed to try for at least the time given
	 * 						and not start any long operations after the time has expired.
	 * @param timeout_unit  Time unit for <tt>timeout</tt> argument.
	 * @param keep_trying		If true, the implementation should keep trying to connect
	 * 							even if problems occur such as the host being unreachable,
	 * 							until the allotted timeout is reached.
	 *
	 * @return					The VMID of the host connected to.
	 */
	public VMID connect( InetAddress address, int port, ConnectionArgs args,
		Object attachment, long timeout, TimeUnit timeout_unit, boolean keep_trying )
		throws IOException;
	
	public void disconnect( VMID vmid );


    /**
     * Returns true if a connection is currently held to the given VMID.
     */
    public boolean hasConnection( VMID vmid );


	/**
	 * @param vmid                          VMID of the instance to which this SPI applies.
	 * @param deserialization_context_vmid  A ThreadLocal in which <tt>vmid</tt> should
	 *                                      be set when deserializing (at least) invoke
	 *                                      and invoke return messages. See
	 *          {@link com.starlight.intrepid.ProxyInvocationHandler#DESERIALIZING_VMID}
	 */
	public void init( InetAddress server_address, Integer server_port, String vmid_hint,
		InboundMessageHandler message_handler, ConnectionListener connection_listener,
		ThreadPoolExecutor thread_pool, VMID vmid,
		ThreadLocal<VMID> deserialization_context_vmid,
		PerformanceListener performance_listener ) throws IOException;

	public void shutdown();


	/**
	 * Send a message to a remote VM.
	 *
	 * @param destination		Destination for the message.
	 * @param message			Message to send.
	 *
	 * @return		If this VMID of the <tt>destination</tt> VM has changed, it will be
	 * 				returned.
	 */
	public VMID sendMessage( VMID destination, IMessage message )
		throws IOException, NotConnectedException;


	/**
	 * Return the server port in use, if applicable.
	 */
	public Integer getServerPort();



	/**
	 * Adds an artificial delay before sending messages. See
	 * {@link com.starlight.intrepid.PerformanceControl#setMessageSendDelay(Long)}.
	 *
	 * @param delay_ms  The delay in milliseconds or null for none.
	 */
	public void setMessageSendDelay( Long delay_ms );
}
