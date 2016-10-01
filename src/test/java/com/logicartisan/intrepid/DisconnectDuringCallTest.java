package com.logicartisan.intrepid;

import com.logicartisan.intrepid.exception.ChannelRejectedException;
import com.logicartisan.intrepid.exception.InterruptedCallException;
import com.starlight.thread.SharedThreadPool;
import com.starlight.thread.ThreadKit;
import junit.framework.TestCase;

import java.io.*;
import java.net.InetAddress;
import java.nio.channels.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


/**
 *
 */
public class DisconnectDuringCallTest extends TestCase {
	private Intrepid server_instance = null;
	private Intrepid client_instance = null;


	@Override
	protected void tearDown() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( server_instance != null ) server_instance.close();
		if ( client_instance != null ) client_instance.close();
	}
	

	public void testDisconnecting() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create( new IntrepidSetup().openServer() );
		server_instance.getLocalRegistry().bind( "server", new ConnectionKillServer() );


		client_instance = Intrepid.create( null );
		VMID server_vmid = client_instance.connect( InetAddress.getLocalHost(),
			server_instance.getServerPort().intValue(), null, null );

		assertEquals( server_instance.getLocalVMID(), server_vmid );


		Runnable runnable = ( Runnable ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );

		for( int i = 0; i < 5; i++ ) {
			try {
				runnable.run();
				fail( "Shouldn't have worked" );
			}
			catch( InterruptedCallException ex ) {
				// This is good
			}

			runnable.run(); // this should work
		}
	}
	
	
	
	public void testVirtualChannelClosing() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		CountDownLatch read_start_latch = new CountDownLatch( 1 );
		CountDownLatch read_exit_latch = new CountDownLatch( 1 );
		AtomicReference<FakeChannelReader> reader_slot =
			new AtomicReference<FakeChannelReader>();

		server_instance = Intrepid.create(
			new IntrepidSetup().openServer().channelAcceptor(
			new FakeReaderAcceptor( read_start_latch, read_exit_latch, reader_slot ) ) );

		client_instance = Intrepid.create( null );
		VMID server_vmid = client_instance.connect( InetAddress.getLocalHost(),
			server_instance.getServerPort().intValue(), null, null );

		assertEquals( server_instance.getLocalVMID(), server_vmid );

		// Open a virtual channel to the server
		ByteChannel channel = client_instance.createChannel( server_vmid, null );

		// Wait for the reader to start, should be quick
		assertTrue( read_start_latch.await( 1, TimeUnit.SECONDS ) );

		CountDownLatch write_exit_latch = new CountDownLatch( 1 );

		FakeChannelWriter writer = new FakeChannelWriter( channel, write_exit_latch );
		SharedThreadPool.INSTANCE.execute( writer );

		// Things should plug merrily along for a bit
		assertFalse( read_exit_latch.await( 2, TimeUnit.SECONDS ) );
		assertFalse( write_exit_latch.await( 1, TimeUnit.MILLISECONDS ) );

		// Now close the main connection
		client_instance.disconnect( server_vmid );

		// Make sure both sides exit
		assertTrue( write_exit_latch.await( 1, TimeUnit.SECONDS ) );
		assertTrue( read_exit_latch.await( 1, TimeUnit.SECONDS ) );

		FakeChannelReader reader = reader_slot.get();
		assertNotNull( reader );
		assertNotNull( reader.getException() );
		//noinspection ThrowableResultOfMethodCallIgnored
		assertTrue( "Reader exception was: " + reader.getException(),
			reader.getException() instanceof ClosedChannelException );

		assertNotNull( writer.getException() );
		//noinspection ThrowableResultOfMethodCallIgnored
		assertTrue( "Writer exception was: " + writer.getException(),
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
			try {
				OutputStream out = Channels.newOutputStream( channel );
				DataOutputStream dout = new DataOutputStream( out );

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
			try {
				InputStream in = Channels.newInputStream( channel );
				DataInputStream din = new DataInputStream( in );

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

