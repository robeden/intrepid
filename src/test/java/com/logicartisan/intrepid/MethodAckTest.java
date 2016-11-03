package com.logicartisan.intrepid;

import com.logicartisan.common.core.thread.ThreadKit;
import com.logicartisan.intrepid.auth.UserContextInfo;
import com.logicartisan.intrepid.exception.MethodInvocationFailedException;
import com.logicartisan.intrepid.message.IMessage;
import com.logicartisan.intrepid.message.InvokeAckIMessage;
import com.logicartisan.intrepid.message.InvokeIMessage;
import com.logicartisan.intrepid.spi.UnitTestHook;
import junit.framework.TestCase;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 *
 */
public class MethodAckTest extends TestCase {
	public void testSystemProperty() {
		assertEquals( "System property 'intrepid.req_invoke_ack_rate_sec' must be set " +
			"to '1' when running unit tests.", "1",
			System.getProperty( "intrepid.req_invoke_ack_rate_sec" ) );
	}


	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	@Override
	protected void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}


	// This test will drop incoming Invoke messages to the server, so calls should hang
	// on the client. Since an ack is not sent, the call should blow out.
	public void testDroppedInitialAck() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

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

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer().unitTestHook( hook ) );

		final AtomicBoolean run_called = new AtomicBoolean( false );
		Runnable server_impl = new Runnable() {
			@Override
			public void run() {
				run_called.set( true );
			}
		};
		server_instance.getLocalRegistry().bind( "server", server_impl );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );


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
			assertEquals( "Initial message acknowledgement timeout exceeded",
				ex.getMessage() );
		}

		long duration = System.currentTimeMillis() - start;
		System.out.println( "Ack abort duration: " + duration );

		assertFalse( "Server method was invoked (should have been dropped)",
			run_called.get() );
		assertTrue( "Duration < 2500 or > 5000: " + duration,
			duration >= 2500 && duration < 5000 );
	}


	// This test will only send the FIRST ACK message from the server. So, the method call
	// should blow out after the second missing ack
	public void testDroppedSecondAck() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

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

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer().unitTestHook( hook ) );

		Runnable server_impl = new Runnable() {
			@Override
			public void run() {
				ThreadKit.sleep( 10, TimeUnit.SECONDS );
			}
		};
		server_instance.getLocalRegistry().bind( "server", server_impl );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );


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
			assertEquals( "Message acknowledgement timeout exceeded", ex.getMessage() );
		}

		long duration = System.currentTimeMillis() - start;
		System.out.println( "Ack abort duration: " + duration );

		assertTrue( "Duration < 3500 or > 6000: " + duration,
			duration >= 3500 && duration < 6000 );
	}


	// Test a normal method call that blocks long enough that it will be aborted by
	// an ack timeout if ack receives aren't being handled properly
	public void testAckedCall() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer() );

		Runnable server_impl = new Runnable() {
			@Override
			public void run() {
				ThreadKit.sleep( 5, TimeUnit.SECONDS );
			}
		};
		server_instance.getLocalRegistry().bind( "server", server_impl );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );


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
			assertEquals( "Initial message acknowledgement timeout exceeded",
				ex.getMessage() );
		}

		long duration = System.currentTimeMillis() - start;

		assertTrue( "Duration < 5000 or > 6000: " + duration,
			duration >= 5000 && duration < 6000 );
	}


	// Test a fast-returning method call to ensure no ack message is sent.
	public void testFastReturn() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		final AtomicBoolean sent_ack = new AtomicBoolean( false );
		PerformanceListener perf_listener = new PerformanceListener() {
			@Override
			public void messageSent( VMID destination_vmid, IMessage message ) {
				if ( message instanceof InvokeAckIMessage ) {
					sent_ack.set( true );
				}
			}

			@Override
			public void remoteCallStarted( VMID instance_vmid, long time, int call_id,
				VMID destination_vmid, int object_id, int method_id, Method method,
				Object[] args, UserContextInfo user_context, String persistent_name ) {}

			@Override
			public void remoteCallCompleted( VMID instance_vmid, long time, int call_id,
				Object result, boolean result_was_thrown, Long server_time ) {}

			@Override
			public void inboundRemoteCallStarted( VMID instance_vmid, long time,
				int call_id, VMID source_vmid, int object_id, int method_id,
				Method method, Object[] args, UserContextInfo user_context,
				String persistent_name ) {}

			@Override
			public void inboundRemoteCallCompleted( VMID instance_vmid, long time,
				int call_id, Object result, boolean result_was_thrown ) {}

			@Override
			public void virtualChannelOpened( VMID instance_vmid, VMID peer_vmid,
				short channel_id ) {}

			@Override
			public void virtualChannelClosed( VMID instance_vmid, VMID peer_vmid,
				short channel_id ) {}

			@Override
			public void virtualChannelDataReceived( VMID instance_vmid, VMID peer_vmid,
				short channel_id, int bytes ) {}

			@Override
			public void virtualChannelDataSent( VMID instance_vmid, VMID peer_vmid,
				short channel_id, int bytes ) {}

			@Override
			public void messageReceived( VMID source_vmid, IMessage message ) {}

			@Override
			public void leaseInfoUpdated( VMID vmid, int object_id,
				String delegate_tostring, boolean holding_strong_reference,
				int leasing_vm_count, boolean renew, boolean release ) {}

			@Override
			public void leasedObjectRemoved( VMID vmid, int object_id ) {}
		};


		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer().performanceListener(
			perf_listener ) );

		Runnable server_impl = new Runnable() {
			@Override
			public void run() {
				// Return immediately
			}
		};
		server_instance.getLocalRegistry().bind( "server", server_impl );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );


		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_instance.getServerPort().intValue(), null, null );
		Runnable proxy = ( Runnable ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );


		long start = System.currentTimeMillis();

		proxy.run();

		long duration = System.currentTimeMillis() - start;

		assertTrue( "Duration > 1000: " + duration, duration < 1000 );
		ThreadKit.sleep( 1, TimeUnit.SECONDS );    // give time for the message to be sent

		assertEquals( false, sent_ack.get() );
	}
}
