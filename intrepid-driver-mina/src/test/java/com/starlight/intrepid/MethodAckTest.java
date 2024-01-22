package com.starlight.intrepid;

import com.logicartisan.common.core.thread.ThreadKit;
import com.starlight.intrepid.driver.UnitTestHook;
import com.starlight.intrepid.exception.MethodInvocationFailedException;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.InvokeAckIMessage;
import com.starlight.intrepid.message.InvokeIMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;


/**
 *
 */
public class MethodAckTest {
	@Test
	public void testSystemProperty() {
		assertEquals("1", System.getProperty( "intrepid.req_invoke_ack_rate_sec" ), "System property 'intrepid.req_invoke_ack_rate_sec' must be set " +
			"to '1' when running unit tests.");
	}


	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	@AfterEach
	public void tearDown() throws Exception {
		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}


	// This test will drop incoming Invoke messages to the server, so calls should hang
	// on the client. Since an ack is not sent, the call should blow out.
	@Test
	public void testDroppedInitialAck() throws Exception {
		UnitTestHook hook = new UnitTestHook() {
			@Override
			public boolean dropMessageSend( VMID destination, IMessage message )
				throws IOException {

				return false;
			}

			@Override
			public boolean dropMessageReceive( VMID source, IMessage message )
				throws IOException {

				// Non-invoke messages are okay
				if ( !( message instanceof InvokeIMessage ) ) return false;

				InvokeIMessage invoke = ( InvokeIMessage ) message;
				// Don't receive any invokes (unless it's to the registry)
				return invoke.getObjectID() != 0;
			}
		};

		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.openServer()
			.unitTestHook( hook )
			.build();

		final AtomicBoolean run_called = new AtomicBoolean( false );
		Runnable server_impl = new Runnable() {
			@Override
			public void run() {
				run_called.set( true );
			}
		};
		server_instance.getLocalRegistry().bind( "server", server_impl );

		client_instance = Intrepid.newBuilder().vmidHint( "client" ).build();


		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_instance.getServerPort().intValue(), null, null );
		Runnable proxy = ( Runnable ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );


		long start = System.currentTimeMillis();

		try {
			proxy.run();
		}
		catch( MethodInvocationFailedException ex ) {
			// Never received any acks
			assertEquals("Initial message acknowledgement timeout exceeded", ex.getMessage());
		}

		long duration = System.currentTimeMillis() - start;
		System.out.println( "Ack abort duration: " + duration );

		assertFalse(run_called.get(), "Server method was invoked (should have been dropped)");
		assertTrue(duration >= 2500 && duration < 5000, "Duration < 2500 or > 5000: " + duration);
	}


	// This test will only send the FIRST ACK message from the server. So, the method call
	// should blow out after the second missing ack
	@Test
	public void testDroppedSecondAck() throws Exception {
		UnitTestHook hook = new UnitTestHook() {
			boolean sent_one_ack = false;

			@Override
			public boolean dropMessageSend( VMID destination, IMessage message )
				throws IOException {

				if ( message instanceof InvokeAckIMessage ) {
					System.out.println( "Saw ack message: " + message );
					if ( !sent_one_ack ) {
						sent_one_ack = true;
						return false;
					}
					else return true;
				}

				return false;
			}

			@Override
			public boolean dropMessageReceive( VMID source, IMessage message )
				throws IOException {

				return false;
			}
		};

		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.openServer()
			.unitTestHook( hook )
			.build();

		Runnable server_impl = new Runnable() {
			@Override
			public void run() {
				ThreadKit.sleep( 10, TimeUnit.SECONDS );
			}
		};
		server_instance.getLocalRegistry().bind( "server", server_impl );

		client_instance = Intrepid.newBuilder().vmidHint( "client" ).build();


		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_instance.getServerPort().intValue(), null, null );
		Runnable proxy = ( Runnable ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );


		long start = System.currentTimeMillis();

		try {
			proxy.run();
		}
		catch( MethodInvocationFailedException ex ) {
			// Never received any acks
			assertEquals("Message acknowledgement timeout exceeded", ex.getMessage());
		}

		long duration = System.currentTimeMillis() - start;
		System.out.println( "Ack abort duration: " + duration );

		assertTrue(duration >= 3500 && duration < 6000, "Duration < 3500 or > 6000: " + duration);
	}


	// Test a normal method call that blocks long enough that it will be aborted by
	// an ack timeout if ack receives aren't being handled properly
	@Test
	public void testAckedCall() throws Exception {
		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.openServer()
			.build();

		Runnable server_impl = new Runnable() {
			@Override
			public void run() {
				ThreadKit.sleep( 5, TimeUnit.SECONDS );
			}
		};
		server_instance.getLocalRegistry().bind( "server", server_impl );

		client_instance = Intrepid.newBuilder().vmidHint( "client" ).build();


		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_instance.getServerPort().intValue(), null, null );
		Runnable proxy = ( Runnable ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );


		long start = System.currentTimeMillis();

		try {
			proxy.run();
		}
		catch( MethodInvocationFailedException ex ) {
			// Never received any acks
			assertEquals("Initial message acknowledgement timeout exceeded", ex.getMessage());
		}

		long duration = System.currentTimeMillis() - start;

		assertTrue(duration >= 5000 && duration < 6000, "Duration < 5000 or > 6000: " + duration);
	}


	// Test a fast-returning method call to ensure no ack message is sent.
	@Test
	public void testFastReturn() throws Exception {
		final AtomicBoolean sent_ack = new AtomicBoolean( false );
		PerformanceListener perf_listener = new PerformanceListener() {
			@Override
			public void messageSent( VMID destination_vmid, IMessage message ) {
				if ( message instanceof InvokeAckIMessage ) {
					sent_ack.set( true );
				}
			}
		};


		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.openServer()
			.performanceListener( perf_listener )
			.build();

		Runnable server_impl = () -> {};        // Return immediately

		server_instance.getLocalRegistry().bind( "server", server_impl );

		client_instance = Intrepid.newBuilder().vmidHint( "client" ).build();


		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_instance.getServerPort().intValue(), null, null );
		Runnable proxy = ( Runnable ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );


		long start = System.currentTimeMillis();

		proxy.run();

		long duration = System.currentTimeMillis() - start;

		assertTrue(duration < 1000, "Duration > 1000: " + duration);
		ThreadKit.sleep( 1, TimeUnit.SECONDS );    // give time for the message to be sent

        assertFalse(sent_ack.get());
	}
}
