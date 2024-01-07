package com.starlight.intrepid;

import com.logicartisan.common.core.thread.ThreadKit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


/**
 *
 */
public class ListenerRegistrationTest {
	private Intrepid server;
	private Intrepid client;



	private boolean inter_instance_bridge_state;

	@BeforeEach
	public void setUp() throws Exception {

		inter_instance_bridge_state = Intrepid.disable_inter_instance_bridge;
		IntrepidTesting.setInterInstanceBridgeDisabled( true );
	}


	@AfterEach
	public void tearDown() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( inter_instance_bridge_state );

		if ( server != null ) server.close();
		if ( client != null ) client.close();
	}


	@Test
	public void testKeepListenerRegistered() throws Exception {
		server = Intrepid.newBuilder().openServer().build();
		int server_port = server.getServerPort().intValue();

		Server server_mock = Mockito.mock( Server.class );
		server.getLocalRegistry().bind( "server", server_mock );


		client = Intrepid.newBuilder().build();
		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );

		assertEquals(server.getLocalVMID(), server_vmid);

		Server server_proxy =
			( Server ) client.getRemoteRegistry( server_vmid ).lookup( "server" );

		Listener listener = new Listener() {};

		// Try to use a non-proxy in the proxy arg
		try {
			client.keepListenerRegistered( listener, server_mock, Server::addListener,
				Server::removeListener );
			fail("Shouldn't have been able to use non-proxy");
		}
		catch( IllegalArgumentException ex ) {
			// expected
		}

		// Initial registration
		ListenerRegistration listener_reg = client.keepListenerRegistered( listener,
			server_proxy, Server::addListener, Server::removeListener );

		// Should immediately be added
		Mockito.verify( server_mock, Mockito.times( 1 ) ).addListener( ArgumentMatchers.any( Listener.class ) );
		Mockito.reset( server_mock );

		for( int i = 0; i < 5; i++ ) {
			// Close the server
			server.close();

			// Should indicate we're NOT connected
			assertNotConnected( listener_reg );

			// Possibly wait a bit
			if ( i > 0 ) ThreadKit.sleep( 500 * i );

			// Should indicate we're NOT connected
			assertNotConnected( listener_reg );
			Mockito.verifyNoMoreInteractions( server_mock );

			// Bring the server back
			server = Intrepid.newBuilder()
				.openServer()
				.serverAddress( new InetSocketAddress( server_port ) )
				.build();
			server.getLocalRegistry().bind( "server", server_mock );

			// Make sure we get the add call
			long start = System.currentTimeMillis();
			// NOTE: for some reason the "connection opened" messages can take a while to
			//       come in, so need a big timeout
			Mockito.verify( server_mock, Mockito.timeout( 10000 ).times( 1 ) )
				.addListener( ArgumentMatchers.any( Listener.class ) );
			System.out.println( "Verify time: " + ( System.currentTimeMillis() - start ) );
			Mockito.reset( server_mock );

			// NOTE: There's a race condition because the add method has to be called
			//       before the currently connected flag can be set. It should follow
			//       very quickly... but never underestimate TeamCity's ability to fail
			//       on all the race conditions.
			waitForTrue( listener_reg::isCurrentlyConnected, 1000 );

			System.out.println( "Pass " + i + " succeeded" );
		}

		// Cancel the registration
		listener_reg.remove();

		Mockito.verify( server_mock, Mockito.times( 1 ) ).removeListener( ArgumentMatchers.any( Listener.class ) );

		// Close the server
		server.close();

		// Wait 5 seconds
		ThreadKit.sleep( 5000 );

		// Make sure no methods were called on the server
		Mockito.verifyNoMoreInteractions( server_mock );
	}


	@Test
	@SuppressWarnings( "AutoBoxing" )
	public void testKeepListenerRegisteredWithReturn() throws Exception {
		server = Intrepid.newBuilder().openServer().build();
		int server_port = server.getServerPort().intValue();

		Server server_mock = Mockito.mock( Server.class );
		server.getLocalRegistry().bind( "server", server_mock );

		Mockito.when( server_mock.addListenerWithReturn( ArgumentMatchers.any( Listener.class ) ) )
			.thenReturn( 0, 1, 2, 3, 4, 5 );

		//noinspection unchecked
		Consumer<Integer> consumer_mock = Mockito.mock( Consumer.class );

		client = Intrepid.newBuilder().build();
		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );

		assertEquals(server.getLocalVMID(), server_vmid);

		Server server_proxy =
			( Server ) client.getRemoteRegistry( server_vmid ).lookup( "server" );

		Listener listener = new Listener() {};

		// Try to use a non-proxy in the proxy arg
		try {
			client.keepListenerRegistered( listener, server_mock,
				Server::addListenerWithReturn, Server::removeListener );
			fail("Shouldn't have been able to use non-proxy");
		}
		catch( IllegalArgumentException ex ) {
			// expected
		}

		// Initial registration
		ListenerRegistration listener_reg = client.keepListenerRegistered( listener,
			server_proxy, Server::addListenerWithReturn, Server::removeListener,
			consumer_mock );

		// Should immediately be added
		Mockito.verify( server_mock, Mockito.times( 1 ) ).addListenerWithReturn( ArgumentMatchers.any( Listener.class ) );
		Mockito.verify( consumer_mock, Mockito.times( 1 ) ).accept( 0 );

		for( int i = 0; i < 5; i++ ) {
			// Close the server
			server.close();

			// Should indicate we're NOT connected
			assertNotConnected( listener_reg );

			// Possibly wait a bit
			if ( i > 0 ) ThreadKit.sleep( 500 * i );

			// Should indicate we're NOT connected
			assertNotConnected( listener_reg );
			Mockito.verifyNoMoreInteractions( server_mock );
			Mockito.verifyNoMoreInteractions( consumer_mock );

			// Bring the server back
			server = Intrepid.newBuilder()
				.openServer()
				.serverAddress( new InetSocketAddress( server_port ) )
				.build();
			server.getLocalRegistry().bind( "server", server_mock );

			// Make sure we get the add call
			long start = System.currentTimeMillis();
			// NOTE: for some reason the "connection opened" messages can take a while to
			//       come in, so need a big timeout
			Mockito.verify( server_mock, Mockito.timeout( 10000 ).times( i + 2 ) )
				.addListenerWithReturn( ArgumentMatchers.any( Listener.class ) );
			Mockito.verify( consumer_mock, Mockito.timeout( 1000 ).times( 1 ) ).accept( i + 1 );
			System.out.println( "Verify time: " + ( System.currentTimeMillis() - start ) );

			// NOTE: There's a race condition because the add method has to be called
			//       before the currently connected flag can be set. It should follow
			//       very quickly... but never underestimate TeamCity's ability to fail
			//       on all the race conditions.
			waitForTrue( listener_reg::isCurrentlyConnected, 1000 );

			System.out.println( "Pass " + i + " succeeded" );
		}

		// Cancel the registration
		listener_reg.remove();

		Mockito.verify( server_mock, Mockito.times( 1 ) ).removeListener( ArgumentMatchers.any( Listener.class ) );
		Mockito.verifyNoMoreInteractions( consumer_mock );

		// Close the server
		server.close();

		ThreadKit.sleep( 5000 );

		// Make sure no methods were called on the server
		Mockito.verifyNoMoreInteractions( server_mock );
		Mockito.verifyNoMoreInteractions( consumer_mock );
	}


	@Test
	public void testKeepListenerRegistered_exceptionRetry() throws Exception {
		server = Intrepid.newBuilder().openServer().build();
		int server_port = server.getServerPort().intValue();

		AtomicBoolean temp_unbound_from_registry = new AtomicBoolean( false );

		AtomicBoolean throw_error_on_add_flag = new AtomicBoolean( false );
		AtomicInteger successful_add_count = new AtomicInteger( 0 );
		AtomicInteger unsuccessful_add_count = new AtomicInteger( 0 );
		Server server_impl = listener -> {
			if ( temp_unbound_from_registry.get() ) {
				fail("Shouldn't have been able to call this");
			}

			if ( throw_error_on_add_flag.get() ) {
				unsuccessful_add_count.incrementAndGet();
				throw new RuntimeException( "Go away" );
			}
			successful_add_count.incrementAndGet();
		};
		server.getLocalRegistry().bind( "server", server_impl );


		client = Intrepid.newBuilder().build();
		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );

		assertEquals(server.getLocalVMID(), server_vmid);

		Server server_proxy =
			( Server ) client.getRemoteRegistry( server_vmid ).lookup( "server" );

		Listener listener = new Listener() {};

		// Initial registration
		ListenerRegistration listener_reg = client.keepListenerRegistered( listener,
			server_proxy, Server::addListener, Server::removeListener );

		// Should immediately be added
		assertEquals(1, successful_add_count.getAndSet( 0 ));
		assertEquals(0, unsuccessful_add_count.get());

		// Close the server
		server.close();

		// Should indicate we're NOT connected
		assertNotConnected( listener_reg );

		// Wait a bit
		// Should indicate we're NOT connected
		assertNotConnected( listener_reg );
		assertEquals(0, successful_add_count.get());
		assertEquals(0, unsuccessful_add_count.get());


		// Bring the server back... BUT DON'T SERVER BIND TO REGISTRY
		temp_unbound_from_registry.set( true );
		server = Intrepid.newBuilder().openServer().serverAddress( new InetSocketAddress(server_port) ).build();

		// Wait a while and make sure we're still not connected
		for( int i = 0; i < 5; i++ ) {     // 5 seconds
			assertNotConnected( listener_reg );
			assertEquals(0, successful_add_count.get());
			assertEquals(0, unsuccessful_add_count.get());

			ThreadKit.sleep( 1000 );
		}


		// Now we'll bind the server but throw errors on add
		throw_error_on_add_flag.set( true );
		temp_unbound_from_registry.set( false );
		server.getLocalRegistry().bind( "server", server_impl );

		waitForCounterValue( unsuccessful_add_count, 2, 10000 );
		assertNotConnected( listener_reg );


		// Now allow addition
		throw_error_on_add_flag.set( false );

		waitForCounterValue( successful_add_count, 1, 2000 );
		unsuccessful_add_count.set( 0 );    // may be more than 2 (last check), don't care

		ThreadKit.sleep( 2000 );
		assertEquals(1, successful_add_count.getAndSet( 0 )); // should still be 1
		assertEquals(0, unsuccessful_add_count.get());        // should still be 0
	}


	private void waitForCounterValue( AtomicInteger counter, int desired_value,
		long timeout_ms ) {

		long duration = 0;
		while( duration < timeout_ms ) {
			if ( counter.get() >= desired_value ) return;

			ThreadKit.sleep( 100 );
			duration += 100;
		}

		fail("Desired value (" + desired_value + ") was not received: " + counter.get());
	}


	private void waitForTrue( BooleanSupplier supplier, long timeout_ms ) {
		long duration = 0;
		while( duration < timeout_ms ) {
			if ( supplier.getAsBoolean() ) return;

			ThreadKit.sleep( 100 );
			duration += 100;
		}

		fail("Desired value (true) was not received");
	}


	private void assertNotConnected( ListenerRegistration registration ) {
		for( int i = 0; i < 10; i++ ) {
			if ( !registration.isCurrentlyConnected() ) {
				return;
			}
			else ThreadKit.sleep( 200 );
		}

		fail("ListenerRegistration still indicates connection");
	}


	public interface Server {
		void addListener( Listener listener );

		default int addListenerWithReturn( Listener listener ) {
			throw new UnsupportedOperationException();
		}
		default void removeListener( Listener listener ) {
			throw new UnsupportedOperationException();
		}
	}

	// Must be public
	@SuppressWarnings( "WeakerAccess" )
	public interface Listener {}
}
