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

package com.starlight.intrepid.driver.netty;

import com.starlight.intrepid.CommTest;
import com.starlight.intrepid.driver.IntrepidDriver;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;


/**
 *
 */
public class UnixCommTest extends CommTest {
	private File comm_file;

	@Override
	protected void subclassSetUp() throws IOException {
		comm_file = File.createTempFile("UnixCommTest", "socket");
	}

	@Override
	protected void subclassTearDown() {
		if(!comm_file.delete()) comm_file.deleteOnExit();
	}

	@Override
	protected IntrepidDriver createSPI( boolean server ) {
		if (System.getProperty("os.name").toLowerCase().contains("mac")) {
			return NettyIntrepidDriver.<KQueueDomainSocketChannel, KQueueServerDomainSocketChannel>newBuilder()
				.clientChannelClass(KQueueDomainSocketChannel.class)
				.serverChannelClass(KQueueServerDomainSocketChannel.class)
				.clientWorkerGroup(new KQueueEventLoopGroup())
				.serverWorkerGroup(new KQueueEventLoopGroup())
				.serverBossGroup(new KQueueEventLoopGroup(1))
				.build();
		}
		else {
			return NettyIntrepidDriver.<EpollDomainSocketChannel, EpollServerDomainSocketChannel>newBuilder()
				.clientChannelClass(EpollDomainSocketChannel.class)
				.serverChannelClass(EpollServerDomainSocketChannel.class)
				.clientWorkerGroup(new EpollEventLoopGroup())
				.serverWorkerGroup(new EpollEventLoopGroup())
				.serverBossGroup(new EpollEventLoopGroup(1))
				.build();
		}
	}

	@Override
	protected SocketAddress createServerAddress() {
		return new DomainSocketAddress(comm_file);
	}

	@Override
	protected SocketAddress createClientConnectAddress(SocketAddress server_address) {
		return server_address;
	}

	@Override
	protected SocketAddress createFakeServerAddress() {
		return createServerAddress();
	}

}
