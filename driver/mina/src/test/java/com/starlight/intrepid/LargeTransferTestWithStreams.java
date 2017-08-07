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
import com.logicartisan.common.core.IOKit;
import junit.framework.TestCase;
import org.apache.mina.core.buffer.SimpleBufferAllocator;

import java.io.*;
import java.net.InetAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 *
 */
public class LargeTransferTestWithStreams extends TestCase {
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


	public void testStreamingLargeTransfer() throws Exception {
		byte[] data = new byte[ 1024 ];//* 1024 ];//* 1 ];//16 ];	// 16 M
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
			ByteChannel channel = null;
			InputStream in = null;
			OutputStream out = null;
			BufferedOutputStream bout = null;
			

			long start = System.currentTimeMillis();
			try {
				channel = client_instance.createChannel( server_vmid, null );
				
				in = new ByteArrayInputStream( data );
				
				out = Channels.newOutputStream( channel );
				bout = new BufferedOutputStream( out, 1 << 18 );

				IOKit.copy( in, bout );
			}
			finally {
				IOKit.close( in );
				IOKit.close( bout );
				IOKit.close( out );

				IOKit.close( channel ); // NOTE: may be obvious, but don't put too early!
			}

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

		ChannelReadThread( ByteChannel channel, byte[] expected_data,
			CountDownLatch read_latch, AtomicBoolean read_error_slot ) {
			
			this.channel = channel;
			this.expected_data = expected_data;
			this.read_latch = read_latch;
			this.read_error_slot = read_error_slot;
		}

		@Override
		public void run() {
			try {
				long start = System.currentTimeMillis();

				ByteArrayOutputStream out = null;
				BufferedOutputStream bout = null;
				InputStream in = null;
				BufferedInputStream bin = null;
				try {
					in = Channels.newInputStream( channel );
					bin = new BufferedInputStream( in, 1 << 18 );
					
					out = new ByteArrayOutputStream();
					bout = new BufferedOutputStream( out, 1 << 18 );
					
					IOKit.copy( bin, bout );
				}
				finally {
					IOKit.close( bin );
					IOKit.close( in );
					IOKit.close( bout );
					IOKit.close( out );
					IOKit.close( channel );
				}
				
				byte[] data = out.toByteArray();
				
				long time = System.currentTimeMillis() - start;

				double bps = ( data.length / ( double ) time ) * 1000.0;

				System.out.println( "Server read " + data.length + " bytes in " +
					time + ": " + NumberFormat.getNumberInstance().format( bps ) + " Bps" );

				if ( !Arrays.equals( expected_data, data ) ) {
					synchronized ( System.err ) {
						System.err.println( "******************Data mismatch******************" );
						System.err.println( "-------------- Expected --------------" );
						int col = 0;
						for( byte b : expected_data ) {
							col++;
							if ( col > 20 ) {
								col = 0;
								System.err.println();
							}
							String str = Integer.toHexString( b & 0xff );
							if ( str.length() == 1 ) System.err.print( "0" );
							System.err.print( str );
						}
						System.err.println();

						System.err.println( "-------------- Actual --------------" );
						col = 0;
						for( byte b : data ) {
							col++;
							if ( col > 20 ) {
								col = 0;
								System.err.println();
							}
							String str = Integer.toHexString( b & 0xff );
							if ( str.length() == 1 ) System.err.print( "0" );
							System.err.print( str );
						}
						System.err.println();

					}
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
