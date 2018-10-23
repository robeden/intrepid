package com.starlight.intrepid;

import com.logicartisan.common.core.thread.SharedThreadPool;
import com.logicartisan.common.core.thread.ThreadKit;
import com.starlight.intrepid.exception.ChannelRejectedException;
import com.starlight.intrepid.exception.InterruptedCallException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.net.InetAddress;
import java.nio.channels.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


/**
 *
 */
public class DisconnectDuringCallTest {
	private Intrepid server_instance = null;
	private Intrepid client_instance = null;


	@After public void tearDown() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( server_instance != null ) server_instance.close();
		if ( client_instance != null ) client_instance.close();
	}
	

	@Test public void testDisconnecting() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.newBuilder().openServer().build();
		server_instance.getLocalRegistry().bind( "server", new ConnectionKillServer() );


		client_instance = Intrepid.newBuilder().build();
		VMID server_vmid = client_instance.connect( InetAddress.getLoopbackAddress(),
			server_instance.getServerPort().intValue(), null, null );

		Assert.assertEquals( server_instance.getLocalVMID(), server_vmid );


		Runnable runnable = ( Runnable ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );

		for( int i = 0; i < 5; i++ ) {
			try {
				runnable.run();
				Assert.fail( "Shouldn't have worked" );
			}
			catch( InterruptedCallException ex ) {
				// This is good
			}

			runnable.run(); // this should work
		}
	}
	
	
	
	@Test public void testVirtualChannelClosing() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		CountDownLatch read_start_latch = new CountDownLatch( 1 );
		CountDownLatch read_exit_latch = new CountDownLatch( 1 );
		AtomicReference<FakeChannelReader> reader_slot =
			new AtomicReference<FakeChannelReader>();

		server_instance = Intrepid.newBuilder()
			.openServer()
			.channelAcceptor(
				new FakeReaderAcceptor( read_start_latch, read_exit_latch, reader_slot ) )
			.build();

		client_instance = Intrepid.newBuilder().build();
		VMID server_vmid = client_instance.connect( InetAddress.getLoopbackAddress(),
			server_instance.getServerPort().intValue(), null, null );

		Assert.assertEquals( server_instance.getLocalVMID(), server_vmid );

		// Open a virtual channel to the server
		ByteChannel channel = client_instance.createChannel( server_vmid, null );

		// Wait for the reader to start, should be quick
		Assert.assertTrue( read_start_latch.await( 1, TimeUnit.SECONDS ) );

		CountDownLatch write_exit_latch = new CountDownLatch( 1 );

		FakeChannelWriter writer = new FakeChannelWriter( channel, write_exit_latch );
		SharedThreadPool.INSTANCE.execute( writer );

		// Things should plug merrily along for a bit
		Assert.assertFalse( read_exit_latch.await( 2, TimeUnit.SECONDS ) );
		Assert.assertFalse( write_exit_latch.await( 1, TimeUnit.MILLISECONDS ) );

		// Now close the main connection
		client_instance.disconnect( server_vmid );

		// Make sure both sides exit
		Assert.assertTrue( write_exit_latch.await( 1, TimeUnit.SECONDS ) );
		Assert.assertTrue( read_exit_latch.await( 1, TimeUnit.SECONDS ) );

		FakeChannelReader reader = reader_slot.get();
		Assert.assertNotNull( reader );
		Assert.assertNotNull( reader.getException() );
		//noinspection ThrowableResultOfMethodCallIgnored
		Assert.assertTrue( "Reader exception was: " + reader.getException(),
			reader.getException() instanceof ClosedChannelException ||
			reader.getException() instanceof EOFException );

		Assert.assertNotNull( writer.getException() );
		//noinspection ThrowableResultOfMethodCallIgnored
		Assert.assertTrue( "Writer exception was: " + writer.getException(),
			writer.getException() instanceof ClosedChannelException );
	}



	static class ConnectionKillServer implements Runnable {
		int call_number = 0;
		VMID last_vmid = null;

		@Override
		public void run() {
			if ( last_vmid == null ) call_number = 0;

			VMID caller = IntrepidContext.getCallingVMID();
			Intrepid instance = IntrepidContext.getActiveInstance();

			if ( !caller.equals( last_vmid ) ) {
				call_number = 0;
				System.out.println( "**** new caller ****" );
			}
			last_vmid = caller;

			System.out.println( "Got call " + call_number);
			if ( ( call_number & 0x01 ) == 0 ) {
				System.out.println( "  Call is even, killing..." );

//				ThreadKit.sleep( 1000 );
				instance.disconnect( caller );
			}
			else {
				System.out.println( "  Call is odd" );
			}

			call_number++;
		}
	}
	
	
	static class FakeChannelWriter implements Runnable {
		private final WritableByteChannel channel;
		private final CountDownLatch exit_latch;

		private Exception exception = null;

		
		FakeChannelWriter( WritableByteChannel channel, CountDownLatch exit_latch ) {
			this.channel = channel;
			this.exit_latch = exit_latch;
		}

		public Exception getException() {
			return exception;
		}

		@Override
		public void run() {
			try (
				OutputStream out = Channels.newOutputStream( channel );
				DataOutputStream dout = new DataOutputStream( out ) ) {

				//noinspection InfiniteLoopStatement
				while( true ) {
					dout.writeBoolean( true );

					ThreadKit.sleep( 100 );
				}
			}
			catch( Exception ex ) {
				exception = ex;
			}
			finally {
				exit_latch.countDown();

				try {
					channel.close();
				}
				catch ( IOException e ) {
					// ignore
				}
			}
		}
	}

	static class FakeChannelReader implements Runnable {
		private final ReadableByteChannel channel;
		private final CountDownLatch exit_latch;

		private Exception exception = null;


		FakeChannelReader( ReadableByteChannel channel, CountDownLatch exit_latch ) {
			this.channel = channel;
			this.exit_latch = exit_latch;
		}

		public Exception getException() {
			return exception;
		}

		@Override
		public void run() {
			try (
				InputStream in = Channels.newInputStream( channel );
				DataInputStream din = new DataInputStream( in ) ) {

				//noinspection InfiniteLoopStatement
				while( true ) {
					din.readBoolean();
				}
			}
			catch( Exception ex ) {
				exception = ex;
			}
			finally {
				exit_latch.countDown();

				try {
					channel.close();
				}
				catch ( IOException e ) {
					// ignore
				}
			}
		}
	}


	static class FakeReaderAcceptor implements ChannelAcceptor {
		private final CountDownLatch start_latch;
		private final CountDownLatch exit_latch;
		private final AtomicReference<FakeChannelReader> reader_slot;

		FakeReaderAcceptor( CountDownLatch start_latch, CountDownLatch exit_latch,
			AtomicReference<FakeChannelReader> reader_slot ) {

			this.start_latch = start_latch;
			this.exit_latch = exit_latch;
			this.reader_slot = reader_slot;
		}

		@Override
		public void newChannel( ByteChannel channel, VMID source_vmid,
			Serializable attachment ) throws ChannelRejectedException {

			FakeChannelReader reader = new FakeChannelReader( channel, exit_latch );
			reader_slot.set( reader );
			SharedThreadPool.INSTANCE.execute( reader );
			start_latch.countDown();
		}
	}
}

