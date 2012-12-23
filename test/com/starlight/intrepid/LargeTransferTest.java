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

import com.starlight.intrepid.exception.ChannelRejectedException;
import junit.framework.TestCase;
import org.apache.mina.core.buffer.SimpleBufferAllocator;

import java.io.Serializable;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


/**
 *
 */
public class LargeTransferTest extends TestCase {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	@Override
	protected void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}

	@Override
	protected void setUp() throws Exception {
		final SimpleBufferAllocator delegate = new SimpleBufferAllocator();
//		IoBuffer.setAllocator( new IoBufferAllocator() {
//			@Override
//			public IoBuffer allocate( int capacity, boolean direct ) {
//				System.out.println( "*** allocate: " + capacity + ", " + direct );
//				return delegate.allocate( capacity, direct );
//			}
//
//			@Override
//			public ByteBuffer allocateNioBuffer( int capacity, boolean direct ) {
//				System.out.println( "*** allocateNio: " + capacity + ", " + direct );
//				return delegate.allocateNioBuffer( capacity, direct );
//			}
//
//			@Override
//			public IoBuffer wrap( ByteBuffer nioBuffer ) {
//				System.out.println( "*** wrap: " + nioBuffer );
//				return delegate.wrap( nioBuffer );
//			}
//
//			@Override
//			public void dispose() {
//				new Throwable( "*** dispose" ).printStackTrace();
////				delegate.dispose();
//			}
//		} );
	}

	public void testLargeTransfer() throws Exception {
		byte[] data = new byte[ 1024 * 1024 * 16 ];	// 16 M
		new Random().nextBytes( data );

		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer() );
		Integer server_port = server_instance.getServerPort();
		assertNotNull( server_port );
		ServerImpl original_instance = new ServerImpl();
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_port.intValue(), null, null );
		assertNotNull( server_vmid );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		Server server = ( Server ) server_registry.lookup( "server" );

		double total_bps = 0;
		for ( int i = 0; i < 5; i++ ) {
			long start = System.currentTimeMillis();
			byte[] data_copy = server.send( data, start );
			long transfer_time = System.currentTimeMillis() - start;
			double bps = ( ( data.length * 2 ) / ( double ) transfer_time ) * 1000.0;
			total_bps += bps;
			System.out.println( "Method round-trip time: " + transfer_time +
				"  - " + NumberFormat.getNumberInstance().format( bps ) + " Bps" );

			assertTrue( Arrays.equals( data, data_copy ) );
		}
		System.out.println( "Average Bps: " +
			NumberFormat.getNumberInstance().format( total_bps / 5.0 ) );
	}


	public void testStreamingLargeTransfer() throws Exception {
		byte[] data = new byte[ 1024 * 1024 * 16 ];	// 16 M
		new Random().nextBytes( data );

		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		final int RUNS = 50;
		final CountDownLatch read_latch = new CountDownLatch( RUNS );
		final AtomicBoolean read_error_slot = new AtomicBoolean( false );
		
		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer().channelAcceptor(
			new TestAcceptor( data, read_latch, read_error_slot ) ) );
		Integer server_port = server_instance.getServerPort();
		assertNotNull( server_port );
		ServerImpl original_instance = new ServerImpl();
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_port.intValue(), null, null );
		assertNotNull( server_vmid );

		double total_bps = 0;
		for( int i = 0; i < RUNS; i++ ) {
			ByteChannel channel = client_instance.createChannel( server_vmid, null );

			ByteBuffer write_buffer = ByteBuffer.allocate( 1 << 18 ); // 256KB
			long start = System.currentTimeMillis();
			int pos = 0;
			while( pos < data.length ) {
				write_buffer.put( data, pos,
					Math.min( write_buffer.remaining(), data.length - pos ) );
				pos += write_buffer.position();
				write_buffer.flip();

				channel.write( write_buffer );
				write_buffer.clear();
			}
			channel.close();
			long time = System.currentTimeMillis() - start;
			double bps = ( ( data.length * 2 ) / ( double ) time ) * 1000.0;
			total_bps += bps;
			System.out.println( "Client done writing in " + time + " ms: " +
				NumberFormat.getNumberInstance().format( bps ) + " Bps" );
			
			assertFalse( read_error_slot.get() );
		}
		
		long start = System.currentTimeMillis();
		read_latch.await();
		long time = System.currentTimeMillis() - start;
		assertTrue( "Waited too long for read_latch: " + time, time < 1000 );
		
		assertFalse( read_error_slot.get() );
		
		System.out.println( "Average Bps: " +
			NumberFormat.getNumberInstance().format( total_bps / ( double ) RUNS ) );
	}

	public void testConcurrentStreamingLargeTransfer() throws Exception {
		final byte[] data = new byte[ 1024 * 1024 * 16 ];	// 16 M
		new Random().nextBytes( data );

		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		final int RUNS = 50;
		final CountDownLatch read_latch = new CountDownLatch( RUNS );
		final AtomicBoolean read_error_slot = new AtomicBoolean( false );

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer().channelAcceptor(
			new TestAcceptor( data, read_latch, read_error_slot ) ) );
		Integer server_port = server_instance.getServerPort();
		assertNotNull( server_port );
		ServerImpl original_instance = new ServerImpl();
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		// Connect to the server
		final VMID server_vmid =
			client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_port.intValue(), null, null );
		assertNotNull( server_vmid );

		// Allow 10 streams at a time
		ExecutorService service = Executors.newFixedThreadPool( 10 );

		final CountDownLatch latch = new CountDownLatch( RUNS );
		final AtomicReference<Exception> exception_slot = new AtomicReference<Exception>();

		long start = System.currentTimeMillis();
		for( int i = 0; i < RUNS; i++ ) {
			final int thread_number = i;
			service.execute( new Runnable() {
				@Override
				public void run() {
					try {
						System.out.println( "Starting thread " + thread_number );
						ByteChannel channel =
							client_instance.createChannel( server_vmid, null );

						ByteBuffer write_buffer = ByteBuffer.allocate( 1 << 18 ); // 256KB
						long start = System.currentTimeMillis();
						int pos = 0;
						while( pos < data.length ) {
							write_buffer.put( data, pos,
								Math.min( write_buffer.remaining(), data.length - pos ) );
							pos += write_buffer.position();
							write_buffer.flip();

							channel.write( write_buffer );
							write_buffer.clear();
						}
						channel.close();
						long time = System.currentTimeMillis() - start;
						double bps = ( ( data.length * 2 ) / ( double ) time ) * 1000.0;
						System.out.println( "Client done writing in " + time + " ms: " +
							NumberFormat.getNumberInstance().format( bps ) + " Bps" );

						assertFalse( read_error_slot.get() );
					}
					catch( Exception ex ) {
						exception_slot.set( ex );
					}
					finally {
						latch.countDown();
						System.out.println( "Thread " + thread_number + " complete" );
					}
				}
			} );
		}

		//noinspection ThrowableResultOfMethodCallIgnored
		if ( exception_slot.get() != null ) throw exception_slot.get();

		System.out.println( "Waiting for completion..." );
		latch.await();
		System.out.println( "All threads completed." );

		double total_bps = ( data.length * RUNS ) /
			( double ) TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis() - start );

		start = System.currentTimeMillis();
		read_latch.await();
		long time = System.currentTimeMillis() - start;
		assertTrue( "Waited too long for read_latch: " + time, time < 1000 );

		assertFalse( read_error_slot.get() );

		System.out.println( "Average Bps: " +
			NumberFormat.getNumberInstance().format( total_bps / ( double ) RUNS ) );
	}


	public interface Server {
		public byte[] send( byte[] data, long send_time );
	}

	public class ServerImpl implements Server {
		@Override
		public byte[] send( byte[] data, long send_time ) {
			long transfer_time = System.currentTimeMillis() - send_time;

			double bps = ( data.length / ( double ) transfer_time ) * 1000.0;


			System.out.println( "Got data is length: " + data.length +
				"  - " + NumberFormat.getNumberInstance().format( bps ) + " Bps" );
			return data;
		}
	}


	public class TestAcceptor implements ChannelAcceptor {
		private final byte[] expected_data;
		private final CountDownLatch read_latch;
		private final AtomicBoolean read_error_slot;


		TestAcceptor( byte[] expected_data, CountDownLatch read_latch,
			AtomicBoolean read_error_slot ) {
			
			this.expected_data = expected_data;
			this.read_latch = read_latch;
			this.read_error_slot = read_error_slot;
		}

		@Override
		public void newChannel( ByteChannel channel, VMID source_vmid,
			Serializable attachment ) throws ChannelRejectedException {

			new ChannelReadThread( channel, expected_data, read_latch,
				read_error_slot ).start();
		}
	}


	public class ChannelReadThread extends Thread {
		private final ByteChannel channel;

		private final byte[] expected_data;
		private final CountDownLatch read_latch;
		private final AtomicBoolean read_error_slot;

		byte[] data;
		ByteBuffer read_buffer = ByteBuffer.allocate( 1 << 18 ); // 256KB

		ChannelReadThread( ByteChannel channel, byte[] expected_data,
			CountDownLatch read_latch, AtomicBoolean read_error_slot ) {
			
			this.channel = channel;
			this.expected_data = expected_data;
			this.read_latch = read_latch;
			this.read_error_slot = read_error_slot;
			this.data = new byte[ expected_data.length ];
		}

		@Override
		public void run() {
			try {
				long start = System.currentTimeMillis();

				int pos = 0;
				int read;
				while( ( read = channel.read( read_buffer ) ) != -1 ) {
					read_buffer.flip();

					assert read_buffer.remaining() == read :
						read_buffer.remaining() + " != " + pos;
					read_buffer.get( data, pos, read_buffer.remaining() );
					pos += read;
					read_buffer.clear();
				}

				assert pos == data.length;
				long time = System.currentTimeMillis() - start;

				double bps = ( data.length / ( double ) time ) * 1000.0;

				System.out.println( "Server read " + data.length + " bytes in " +
					time + ": " + NumberFormat.getNumberInstance().format( bps ) + " Bps" );

				if ( !Arrays.equals( expected_data, data ) ) {
					System.err.println( "******************Data mismatch******************" );
					read_error_slot.set( true );
				}
				
				read_latch.countDown();
			}
			catch( Exception ex ) {
				ex.printStackTrace();
			}
		}
	}
}
