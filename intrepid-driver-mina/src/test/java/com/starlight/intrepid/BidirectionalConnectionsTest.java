package com.starlight.intrepid;

import com.logicartisan.common.core.thread.ObjectSlot;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.UserContextInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 *
 */
public class BidirectionalConnectionsTest {
	private Intrepid a_instance = null;
	private Intrepid b_instance = null;


	@AfterEach
	public void tearDown() throws Exception {
		if ( a_instance != null ) a_instance.close();
		if ( b_instance != null ) b_instance.close();
	}


	@Test
	public void testConnectFromBothSides_noBridge() throws Exception {
		_testConnectFromBothSides();
	}


	private void _testConnectFromBothSides() throws Exception {
		System.out.println( "---- BEGIN TEST ----" );
		System.out.println();
		System.out.println();
		ConnectionListener connection_listener = new ConnectionListener() {
			@Override
			public void connectionOpened( @Nonnull SocketAddress socket_address,
				Object attachment, @Nonnull VMID source_vmid, @Nonnull VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				@Nonnull Object connection_type_description, byte ack_rate_sec ) {

				System.out.println( "Connection Opened (" + vmid + "): " + socket_address );
			}

			@Override
			public void connectionClosed( @Nonnull SocketAddress socket_address,
				@Nonnull VMID source_vmid, @Nullable VMID vmid,
				@Nullable Object attachment,
				boolean will_attempt_reconnect, @Nullable UserContextInfo user_context ) {}

			@Override
			public void connectionOpening( @Nonnull SocketAddress socket_address,
				Object attachment, ConnectionArgs args,
				@Nonnull Object connection_type_description ) {}

			@Override
			public void connectionOpenFailed( @Nonnull SocketAddress socket_address,
				Object attachment, Exception error, boolean will_retry ) {}
		};


		a_instance = Intrepid.newBuilder().openServer().vmidHint( "---A---" ).build();
		a_instance.addConnectionListener( connection_listener );


		b_instance = Intrepid.newBuilder().openServer().vmidHint( "---B---" ).build();
		b_instance.addConnectionListener( connection_listener );


		// Bind server instances
		a_instance.getLocalRegistry().bind( "a", ( A ) port -> {
			final ObjectSlot<Object> slot = new ObjectSlot<>();

			new Thread() {
				@Override
				public void run() {
					try {
						VMID b_vmid = a_instance.connect( InetAddress.getLoopbackAddress(),
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
		assertEquals(b_proxy, b_instance.getLocalRegistry().lookup( "b" ));

		VMID a_vmid = b_instance.connect( InetAddress.getLoopbackAddress(),
            a_instance.getServerPort(), null, null );

		A a = ( A ) b_instance.getRemoteRegistry( a_vmid ).lookup( "a" );

		a.registerB(b_instance.getServerPort());

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
