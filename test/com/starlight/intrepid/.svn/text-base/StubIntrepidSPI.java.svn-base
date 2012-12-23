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

package com.starlight.intrepid;

import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.spi.InboundMessageHandler;
import com.starlight.intrepid.spi.IntrepidSPI;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class StubIntrepidSPI implements IntrepidSPI {
	@Override
	public VMID connect( InetAddress address, int port, ConnectionArgs args,
		Object attachment, long timeout, TimeUnit timeout_unit, boolean keep_trying )
		throws IOException {

		return new VMID( UUID.randomUUID(), "Stub: " + address.getHostAddress() );
	}

	@Override
	public void disconnect( VMID vmid ) {}


	@Override
	public boolean hasConnection( VMID vmid ) {
		return false;
	}

	@Override
	public void init( InetAddress server_address, Integer server_port, String vmid_hint,
		InboundMessageHandler message_handler, ConnectionListener connection_listener,
		ThreadPoolExecutor thread_pool, VMID vmid,
		ThreadLocal<VMID> deserialization_context_vmid,
		PerformanceListener performance_listener ) throws IOException {}

	@Override
	public void shutdown() {}

	@Override
	public VMID sendMessage( VMID destination, IMessage message )
		throws IOException {

		return null;
	}

	@Override
	public Integer getServerPort() {
		return null;
	}

	@Override
	public void setMessageSendDelay( Long delay_ms ) {}
}
