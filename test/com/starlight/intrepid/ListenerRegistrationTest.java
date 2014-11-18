package com.starlight.intrepid;

import com.starlight.thread.ThreadKit;
import junit.framework.TestCase;

import java.net.InetAddress;

import static org.mockito.Mockito.*;


/**
 *
 */
public class ListenerRegistrationTest extends TestCase {
	public void testKeepListenerRegistered() throws Exception {
		Intrepid server = Intrepid.create( new IntrepidSetup().openServer() );
		int server_port = server.getServerPort().intValue();

		Server server_mock = mock( Server.class );
		server.getLocalRegistry().bind( "server", server_mock );


		Intrepid client = Intrepid.create( new IntrepidSetup() );
		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );

		assertEquals( server.getLocalVMID(), server_vmid );

		Server server_proxy =
			( Server ) client.getRemoteRegistry( server_vmid ).lookup( "server" );

		Listener listener = new Listener() {};

		// Try to use a non-proxy in the proxy arg
		try {
			client.keepListenerRegistered( listener, server_mock, Server::addListener,
				Server::removeListener );
			fail( "Shouldn't have been able to use non-proxy" );
		}
		catch( IllegalArgumentException ex ) {
			// expected
		}

		// Initial registration
		ListenerRegistration listener_reg = client.keepListenerRegistered( listener,
			server_proxy, Server::addListener, Server::removeListener );

		// Should immediately be added
		verify( server_mock, times( 1 ) ).addListener( any( Listener.class ) );
		reset( server_mock );

		for( int i = 0; i < 5; i++ ) {
			// Close the server
			server.close();

			// Should indicate we're NOT connected
			assertNotConnected( listener_reg );

			// Possibly wait a bit
			if ( i > 0 ) ThreadKit.sleep( 500 * i );

			// Should indicate we're NOT connected
			assertNotConnected( listener_reg );
			verifyNoMoreInteractions( server_mock );

			// Bring the server back
			server = Intrepid.create(
				new IntrepidSetup().openServer().serverPort( server_port ) );
			server.getLocalRegistry().bind( "server", server_mock );

			// Make sure we get the add call
			long start = System.currentTimeMillis();
			// NOTE: for some reason the "connection opened" messages can take a while to
			//       come in, so need a big timeout
			verify( server_mock, timeout( 10000 ).times( 1 ) )
				.addListener( any( Listener.class ) );
			System.out.println( "Verify time: " + ( System.currentTimeMillis() - start ) );
			reset( server_mock );

			// Should indicate we're connected
			assertTrue( listener_reg.isCurrentlyConnected() );

			System.out.println( "Pass " + i + " succeeded" );
		}

		// Cancel the registration
		listener_reg.remove();

		verify( server_mock, times( 1 ) ).removeListener( any( Listener.class ) );

		// Close the server
		server.close();

		// Wait 5 seconds
		ThreadKit.sleep( 5000 );

		// Make sure no methods were called on the server
		verifyNoMoreInteractions( server_mock );
	}


	private void assertNotConnected( ListenerRegistration registration ) {
		for( int i = 0; i < 10; i++ ) {
			if ( !registration.isCurrentlyConnected() ) {
				return;
			}
			else ThreadKit.sleep( 200 );
		}

		fail( "ListenerRegistration still indicates connection" );
	}


	public static interface Server {
		public void addListener( Listener listener );
		public void removeListener( Listener listener );
	}

	public static interface Listener {

	}
}
