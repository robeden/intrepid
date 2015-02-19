package com.starlight.intrepid;

import com.starlight.NotNull;
import com.starlight.Nullable;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.thread.ObjectSlot;
import junit.framework.TestCase;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;


/**
 *
 */
public class BidirectionalConnectionsTest extends TestCase {
	private Intrepid a_instance = null;
	private Intrepid b_instance = null;


	@Override
	protected void tearDown() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( a_instance != null ) a_instance.close();
		if ( b_instance != null ) b_instance.close();
	}


	public void testConnectFromBothSides_noBridge() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );
		_testConnectFromBothSides();
	}

	public void testConnectFromBothSides_bridge() throws Exception {
		_testConnectFromBothSides();
	}


	private void _testConnectFromBothSides() throws Exception {
		System.out.println( "---- BEGIN TEST ----" );
		System.out.println();
		System.out.println();
		ConnectionListener connection_listener = new ConnectionListener() {
			@Override
			public void connectionOpened( @NotNull InetAddress host, int port,
				Object attachment, @NotNull VMID source_vmid, @NotNull VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				@NotNull Object connection_type_description, byte ack_rate_sec ) {

				System.out.println( "Connection Opened (" + vmid + "): " + host + ":" + port );
			}

			@Override
			public void connectionClosed( @NotNull InetAddress host, int port,
				@NotNull VMID source_vmid, @Nullable VMID vmid,
				@Nullable Object attachment,
				boolean will_attempt_reconnect, @Nullable UserContextInfo user_context ) {}

			@Override
			public void connectionOpening( @NotNull InetAddress host, int port,
				Object attachment, ConnectionArgs args,
				@NotNull Object connection_type_description ) {}

			@Override
			public void connectionOpenFailed( @NotNull InetAddress host, int port,
				Object attachment, Exception error, boolean will_retry ) {}
		};


		a_instance = Intrepid.create( new IntrepidSetup().openServer().vmidHint( "---A---" ) );
		a_instance.addConnectionListener( connection_listener );


		b_instance = Intrepid.create( new IntrepidSetup().openServer().vmidHint( "---B---" ) );
		b_instance.addConnectionListener( connection_listener );


		// Bind server instances
		a_instance.getLocalRegistry().bind( "a", ( A ) port -> {
			final ObjectSlot<Object> slot = new ObjectSlot<>();

			new Thread() {
				@Override
				public void run() {
					try {
						VMID b_vmid = a_instance.connect( InetAddress.getLocalHost(),
							port, null, null );

						Registry registry = a_instance.getRemoteRegistry( b_vmid );

						B b = ( B ) registry.lookup( "b" );

						b.call();

						slot.set( Boolean.TRUE );
					}
					catch( Exception ex ) {
						slot.set( ex );
					}
				}
			}.start();

			Object result = slot.waitForValue();
			if ( result instanceof Exception ) throw ( Exception ) result;
		} );

		final CountDownLatch call_latch = new CountDownLatch( 1 );
		B b_impl = call_latch::countDown;
		b_instance.getLocalRegistry().bind( "b", b_impl );


		B b_proxy = ( B ) b_instance.createProxy( b_impl );
		assertEquals( b_proxy, b_instance.getLocalRegistry().lookup( "b" ) );

		VMID a_vmid = b_instance.connect( InetAddress.getLocalHost(),
			a_instance.getServerPort().intValue(), null, null );

		A a = ( A ) b_instance.getRemoteRegistry( a_vmid ).lookup( "a" );

		a.registerB( b_instance.getServerPort().intValue() );

		System.out.println();
		System.out.println();
		System.out.println( "---- END TEST ----" );
	}


	public static interface A {
		public void registerB( int port ) throws Exception;
	}

	public static interface B {
		public void call();
	}
}
