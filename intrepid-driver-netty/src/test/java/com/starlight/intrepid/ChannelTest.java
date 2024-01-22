// Copyright (c) 2011 Rob Eden.
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

import com.logicartisan.common.core.IOKit;
import com.logicartisan.common.core.Triple;
import com.logicartisan.common.core.thread.SharedThreadPool;
import com.logicartisan.common.core.thread.ThreadKit;
import com.starlight.intrepid.exception.ChannelRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


/**
 *
 */
public class ChannelTest {
	Intrepid server;
	Intrepid client;

	@AfterEach
	public void tearDown() throws Exception {

		if ( server != null ) server.close();
		if ( client != null ) client.close();
	}

	@Test
	public void testNoAcceptor() throws Exception {
		server = Intrepid.newBuilder().openServer().build();
		client = Intrepid.newBuilder().build();

		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );

		try {
			client.createChannel( server_vmid, "testing" );
			fail("Should have rejected the channel");
		}
		catch( ChannelRejectedException ex ) {
			// This is good
		}
	}


	@Test
	public void testChannelReject() throws IOException {
		ChannelAcceptor acceptor = new TestAcceptor( "Test reject" );

		server = Intrepid.newBuilder().openServer().channelAcceptor( acceptor ).build();
		client = Intrepid.newBuilder().build();

		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );

		try {
			client.createChannel( server_vmid, "testing" );
			fail("Should have rejected the channel");
		}
		catch( ChannelRejectedException ex ) {
			// This is good
			assertNotNull(ex.getMessage());
			assertEquals("Test reject", ex.getMessage());
		}
	}


	@Test
	public void testBasic() throws Exception {
		TestAcceptor acceptor = new TestAcceptor( null );

		server = Intrepid.newBuilder()
			.openServer()
			.channelAcceptor( acceptor )
			.vmidHint( "server" )
			.build();
		client = Intrepid.newBuilder().vmidHint( "client" ).build();

		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );

		ByteChannel client_channel = client.createChannel( server_vmid, "testing" );

		// Client write
		ByteBuffer buffer = ByteBuffer.allocate( 10 );
		buffer.put( ( byte ) 0xCA );
		buffer.put( ( byte ) 0xFE );
		buffer.flip();

		int written = client_channel.write( buffer );
		assertEquals(2, written);
		buffer.clear();

		buffer.put( ( byte ) 0xBA );
		buffer.put( ( byte ) 0xBE );
		buffer.flip();

		written = client_channel.write( buffer );
		assertEquals(2, written);
		buffer.clear();

		// Server read
		Triple<ByteChannel,VMID,Serializable> channel_info =
			acceptor.queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull(channel_info);

		assertEquals("testing", channel_info.getThree());
		assertEquals(client.getLocalVMID(), channel_info.getTwo());

		ByteChannel server_channel = channel_info.getOne();

		buffer.limit( 4 );
		int read = 0;
		while( read < 4 ) {
			int sweep_read = server_channel.read( buffer );
			assertTrue(sweep_read > 1, String.valueOf( read ));
			read += sweep_read;
		}
		buffer.flip();

		assertEquals(( byte ) 0xCA, buffer.get());
		assertEquals(( byte ) 0xFE, buffer.get());
		assertEquals(( byte ) 0xBA, buffer.get());
		assertEquals(( byte ) 0xBE, buffer.get());

		buffer.clear();

		// Server write
		buffer.put( ( byte ) 0xDE );
		buffer.put( ( byte ) 0xAD );
		buffer.put( ( byte ) 0xBE );
		buffer.put( ( byte ) 0xEF );
		buffer.flip();

		written = server_channel.write( buffer );
		buffer.clear();
		assertEquals(4, written);

		// Server close
		server_channel.close();

		// Client read
		buffer.limit( 1 );
		read = client_channel.read( buffer );
		assertEquals(1, read);
		buffer.flip();
		assertEquals(( byte ) 0xDE, buffer.get());
		buffer.clear();

		buffer.limit( 1 );
		read = client_channel.read( buffer );
		assertEquals(1, read);
		buffer.flip();
		assertEquals(( byte ) 0xAD, buffer.get());
		buffer.clear();

		buffer.limit( 1 );
		read = client_channel.read( buffer );
		assertEquals(1, read);
		buffer.flip();
		assertEquals(( byte ) 0xBE, buffer.get());
		buffer.clear();

		buffer.limit( 1 );
		read = client_channel.read( buffer );
		assertEquals(1, read);
		buffer.flip();
		assertEquals(( byte ) 0xEF, buffer.get());
		buffer.clear();

		// Make sure we see a close
		read = client_channel.read( buffer );
		assertEquals(-1, read);
	}


	/**
	 * Write more data than should fit in a single message to ensure it's broken up in
	 * multiple messages for sending.
	 */
	@Test
	public void testLargeDataBreakup() throws Exception {
		TestAcceptor acceptor = new TestAcceptor( null );

		// Server setup
		server = Intrepid.newBuilder()
			.openServer()
			.channelAcceptor( acceptor )
			.vmidHint( "server" )
			.build();
		VMID server_vmid = server.getLocalVMID();


		AtomicInteger data_message_count = new AtomicInteger( 0 );

		PerformanceListener client_listener = new PerformanceListener() {
			@Override public void virtualChannelDataSent( VMID instance_vmid,
				VMID peer_vmid,
				short channel_id, short message_id, int bytes,
				long window_wait_time_nanos ) {

				data_message_count.incrementAndGet();
			}
		};

		client = Intrepid.newBuilder()
			.vmidHint( "client" )
			.performanceListener( client_listener )
			.build();


		client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );

		ByteChannel client_channel = client.createChannel( server_vmid, "testing" );


		// Client write
		ByteBuffer buffer = ByteBuffer.allocate( 1 << 20 ); // 1M
		Random rand = new Random();
		while( buffer.hasRemaining() ) {
			buffer.putLong( rand.nextLong() );
		}
		buffer.flip();
		client_channel.write( buffer );

		client_channel.close();

		ThreadKit.sleep( 1000 );

		client.disconnect( server_vmid );

		System.out.println( "Data messages: " + data_message_count );
		assertTrue(data_message_count.get() > 1);
	}


	@Test
	public void testQuickServerWrite() throws Exception {
		ByteBuffer to_write = ByteBuffer.allocate( 4 );
		to_write.put( ( byte ) 0xDE );
		to_write.put( ( byte ) 0xAD );
		to_write.put( ( byte ) 0xBE );
		to_write.put( ( byte ) 0xEF );
		to_write.flip();
		TestAcceptorServerWrite acceptor = new TestAcceptorServerWrite( to_write );


		server = Intrepid.newBuilder()
			.openServer()
			.channelAcceptor( acceptor )
			.vmidHint( "server" )
			.build();
		client = Intrepid.newBuilder().vmidHint( "client" ).build();

		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );

		ByteChannel client_channel = client.createChannel( server_vmid, "testing" );

		// NOTE: this would block forever before svn change 367
		ByteBuffer read_buffer = ByteBuffer.allocate( 4 );
		while( read_buffer.hasRemaining() ) {
			client_channel.read( read_buffer );
		}

		read_buffer.flip();

		assertEquals(( byte ) 0xDE, read_buffer.get());
		assertEquals(( byte ) 0xAD, read_buffer.get());
		assertEquals(( byte ) 0xBE, read_buffer.get());
		assertEquals(( byte ) 0xEF, read_buffer.get());
	}

	

	private class TestAcceptor implements ChannelAcceptor {
		private final String reject_reason;

		private BlockingQueue<Triple<ByteChannel,VMID,Serializable>> queue =
			new LinkedBlockingQueue<>();


		TestAcceptor( String reject_reason ) {
			this.reject_reason = reject_reason;
		}

		@Override
		public void newChannel( ByteChannel channel, VMID source_vmid,
			Serializable attachment ) throws ChannelRejectedException {

			if ( reject_reason != null ) {
				throw new ChannelRejectedException( reject_reason );
			}

			queue.add( Triple.create( channel, source_vmid, attachment ) );
		}
	}
	
	
	private class TestAcceptorServerWrite implements ChannelAcceptor {
		private final ByteBuffer to_write;
		
		TestAcceptorServerWrite( ByteBuffer to_write ) {
			this.to_write = to_write;
		}


		@Override
		public void newChannel( final ByteChannel channel, VMID source_vmid,
			Serializable attachment ) throws ChannelRejectedException {

			SharedThreadPool.INSTANCE.execute( () -> {
				try {

					while( to_write.hasRemaining() ) {
						channel.write( to_write );
					}
				}
				catch( Exception ex ) {
					ex.printStackTrace();
				}
				finally {
					IOKit.close( channel );
				}
			} );
		}
	}
}
