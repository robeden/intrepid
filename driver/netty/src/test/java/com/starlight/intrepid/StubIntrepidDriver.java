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

import com.logicartisan.common.core.thread.ScheduledExecutor;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.driver.InboundMessageHandler;
import com.starlight.intrepid.driver.IntrepidDriver;
import com.starlight.intrepid.driver.SessionInfo;
import com.starlight.intrepid.driver.UnitTestHook;
import com.starlight.intrepid.exception.NotConnectedException;
import com.starlight.intrepid.message.IMessage;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;


/**
 *
 */
public class StubIntrepidDriver implements IntrepidDriver {
	@Override
	public VMID connect(SocketAddress socket_address, ConnectionArgs args,
		Object attachment, long timeout, TimeUnit timeout_unit, boolean keep_trying )
		throws IOException {

		return new VMID( UUID.randomUUID(), "Stub: " + socket_address );
	}

	@Override
	public void disconnect( VMID vmid ) {}


	@Override
	public boolean hasConnection( VMID vmid ) {
		return false;
	}



	@Override
	public void init( SocketAddress server_address, String vmid_hint,
		InboundMessageHandler message_handler, ConnectionListener connection_listener,
		ScheduledExecutor thread_pool, VMID vmid,
		ThreadLocal<VMID> deserialization_context_vmid,
		PerformanceListener performance_listener, UnitTestHook unit_test_hook,
		BiFunction<UUID,String,VMID> vmid_creator )
		throws IOException {}

	@Override
	public void shutdown() {}


	@Override
	public SessionInfo sendMessage( VMID destination, IMessage message,
		@Nullable IntConsumer protocol_version_consumer )
		throws IOException, NotConnectedException {
		return null;
	}

	@Override
	public Integer getServerPort() {
		return null;
	}

	@Override
	public SocketAddress getServerAddress() {
		return null;
	}

	@Override
	public void setMessageSendDelay( Long delay_ms ) {}
}
