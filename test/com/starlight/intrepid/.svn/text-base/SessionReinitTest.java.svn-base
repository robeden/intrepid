package com.starlight.intrepid;

import com.starlight.intrepid.auth.*;
import com.starlight.intrepid.spi.NoAuthenticationHandler;
import com.starlight.locale.ResourceKey;
import com.starlight.locale.UnlocalizableTextResourceKey;
import com.starlight.thread.ObjectSlot;
import com.starlight.thread.SharedThreadPool;
import com.starlight.thread.ThreadKit;
import junit.framework.TestCase;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Test user session re-init (see
 * {@link com.starlight.intrepid.auth.RequestUserCredentialReinit} for more info).
 */
public class SessionReinitTest extends TestCase {
	Intrepid server;
	Intrepid client;

	@Override
	protected void tearDown() throws Exception {
		if ( server != null ) server.close();
		if ( client != null ) client.close();
	}


	public void testNonsupportingAuthHandler() throws Exception {
		// Just use a normal auth handler
		server = Intrepid.create( new IntrepidSetup().vmidHint(
			"server" ).authHandler( new NoAuthenticationHandler() ).serverPort( 0 ) );
		client = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		try {
			client.connect( InetAddress.getLocalHost(),
				server.getServerPort().intValue(), new RequestUserCredentialReinit(),
				null );
			fail( "Shouldn't have been able to connect" );
		}
		catch( IOException ex ) {
			// This is good...
			assertEquals(
				Resources.ERROR_USER_REINIT_CONNECTIONS_NOT_ALLOWED.getValue(),
				ex.getMessage() );
		}
	}


	public void testRejectedConnection() throws Exception {
		AuthenticationHandler handler = new UserCredentialReinitAuthenticationHandler() {
			@Override
			public UserCredentialsConnectionArgs getUserCredentials(
				SocketAddress remote_address,
				Object session_source ) throws ConnectionAuthFailureException {

				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Test rejection" ) );
			}

			@Override
			public UserContextInfo checkConnection( ConnectionArgs connection_args,
				SocketAddress remote_address, Object session_source )
				throws ConnectionAuthFailureException {

				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Shouldn't get here" ) );
			}

			@Override
			public void notifyUserCredentialFailure( ResourceKey<String> error ) {}
		};

		// Just use a normal auth handler
		server = Intrepid.create( new IntrepidSetup().vmidHint(
			"server" ).authHandler( handler ).serverPort( 0 ) );
		client = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		try {
			client.connect( InetAddress.getLocalHost(),
				server.getServerPort().intValue(), new RequestUserCredentialReinit(),
				null );
			fail( "Shouldn't have been able to connect" );
		}
		catch( IOException ex ) {
			// This is good...
			assertEquals( "Test rejection", ex.getMessage() );
		}
	}


	public void testSuccessfulConnection() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );


		//     Client                Server
		//        |---- connect ------->|  (re-init request)
		//        |                     |
		//        |<--- session init ---|  (user credentials)
		//        |                     |
		//        |---- session ack --->|

		AuthenticationHandler handler = new UserCredentialReinitAuthenticationHandler() {
			@Override
			public UserCredentialsConnectionArgs getUserCredentials(
				SocketAddress remote_address,
				Object session_source ) throws ConnectionAuthFailureException {

//				System.out.println( "--- getUserCredentials(" + remote_address + "," +
//					session_source + ")" );

				return new UserCredentialsConnectionArgs( "reden",
					"password".toCharArray() );
			}

			@Override
			public UserContextInfo checkConnection( ConnectionArgs connection_args,
				SocketAddress remote_address, Object session_source )
				throws ConnectionAuthFailureException {

				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Shouldn't get here" ) );
			}

			@Override
			public void notifyUserCredentialFailure( ResourceKey<String> error ) {}
		};

		// Just use a normal auth handler
		server = Intrepid.create( new IntrepidSetup().vmidHint(
			"server" ).authHandler( handler ).serverPort( 0 ) );
		client = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).authHandler(
			new AuthenticationHandler() {
				@Override
				public UserContextInfo checkConnection( ConnectionArgs connection_args,
					SocketAddress remote_address, Object session_source )
					throws ConnectionAuthFailureException {

//					System.out.println( "--- checkConnection(" + connection_args +
//						"," + remote_address + "," + session_source + ")" );

					if ( connection_args instanceof UserCredentialsConnectionArgs ) {
						UserCredentialsConnectionArgs uargs =
							( UserCredentialsConnectionArgs ) connection_args;
						return new SimpleUserContextInfo( uargs.getUser() );
					}

					return null;
				}
			} ) );

		final AtomicReference<UserContextInfo> context_slot =
			new AtomicReference<UserContextInfo>();
		client.getLocalRegistry().bind( "test", new Runnable() {
			@Override
			public void run() {
				System.out.println( "Proxy run() called: " + IntrepidContext.getUserInfo() );
				System.out.println( "  Active instance: " + IntrepidContext.getActiveInstance());
				System.out.println( "  Calling VMID: " + IntrepidContext.getCallingVMID());
				context_slot.set( IntrepidContext.getUserInfo() );
			}
		} );

		// Indicate that we want to use the server instance
		Intrepid.setThreadInstance( server );

		final ObjectSlot<VMID> server_connection_slot = new ObjectSlot<VMID>();
		server.addConnectionListener( new ConnectionListener() {
			@Override
			public void connectionOpened( InetAddress host, int port, Object attachment,
				VMID source_vmid,
				VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				Object connection_type_description ) {

				System.out.println( "Server connection opened: " + vmid );
				server_connection_slot.set( vmid );
			}

			@Override
			public void connectionClosed( InetAddress host, int port, VMID source_vmid,
				VMID vmid, Object attachment, boolean will_attempt_reconnect ) {}

			@Override
			public void connectionOpenFailed( InetAddress host, int port,
				Object attachment, Exception error, boolean will_retry ) {}

			@Override
			public void connectionOpening( InetAddress host, int port, Object attachment,
				ConnectionArgs args, Object connection_type_description ) {}
		} );

		VMID server_vmid;
		try {
			server_vmid = client.connect( InetAddress.getLocalHost(),
				server.getServerPort().intValue(), new RequestUserCredentialReinit(),
				null );

			System.out.println( "Connected to: " + server_vmid );
			assertEquals( server.getLocalVMID(), server_vmid );
		}
		catch( IOException ex ) {
			ex.printStackTrace();

			fail( "Should have been able to connect: " + ex );
			return;
		}

		VMID client_vmid = server_connection_slot.waitForValue();
//		System.out.println( "Got client VMID: " + client_vmid );
		assertEquals( client.getLocalVMID(), client_vmid );
		Registry registry = server.getRemoteRegistry( client_vmid );

//		System.out.println( "Got remote registry: " + registry );
		Runnable server_proxy = ( Runnable ) registry.lookup( "test" );
//		System.out.println( "calling proxy...");
		server_proxy.run();
//		System.out.println( "proxy called" );

		UserContextInfo context = context_slot.get();
		System.out.println( "User context: " + context );

		assertNotNull( context );
		assertEquals( "reden", context.getUserName() );
	}


	public void testSuccessfulConnectionLongWait() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );


		//     Client                Server
		//        |---- connect ------->|  (re-init request)
		//        |                     |
		//        |<--- session init ---|  (user credentials)
		//        |                     |
		//        |---- session ack --->|

		AuthenticationHandler handler = new UserCredentialReinitAuthenticationHandler() {
			@Override
			public UserCredentialsConnectionArgs getUserCredentials(
				SocketAddress remote_address,
				Object session_source ) throws ConnectionAuthFailureException {

//				System.out.println( "--- getUserCredentials(" + remote_address + "," +
//					session_source + ")" );

				ThreadKit.sleep( 40000 );

				return new UserCredentialsConnectionArgs( "reden",
					"password".toCharArray() );
			}

			@Override
			public UserContextInfo checkConnection( ConnectionArgs connection_args,
				SocketAddress remote_address, Object session_source )
				throws ConnectionAuthFailureException {

				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Shouldn't get here" ) );
			}

			@Override
			public void notifyUserCredentialFailure( ResourceKey<String> error ) {}
		};

		// Just use a normal auth handler
		server = Intrepid.create( new IntrepidSetup().vmidHint(
			"server" ).authHandler( handler ).serverPort( 0 ) );
		client = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).authHandler(
			new AuthenticationHandler() {
				@Override
				public UserContextInfo checkConnection( ConnectionArgs connection_args,
					SocketAddress remote_address, Object session_source )
					throws ConnectionAuthFailureException {

//					System.out.println( "--- checkConnection(" + connection_args +
//						"," + remote_address + "," + session_source + ")" );

					if ( connection_args instanceof UserCredentialsConnectionArgs ) {
						UserCredentialsConnectionArgs uargs =
							( UserCredentialsConnectionArgs ) connection_args;
						return new SimpleUserContextInfo( uargs.getUser() );
					}

					return null;
				}
			} ) );

		final AtomicReference<UserContextInfo> context_slot =
			new AtomicReference<UserContextInfo>();
		client.getLocalRegistry().bind( "test", new Runnable() {
			@Override
			public void run() {
				System.out.println( "Proxy run() called: " + IntrepidContext.getUserInfo() );
				System.out.println( "  Active instance: " + IntrepidContext.getActiveInstance());
				System.out.println( "  Calling VMID: " + IntrepidContext.getCallingVMID());
				context_slot.set( IntrepidContext.getUserInfo() );
			}
		} );

		// Indicate that we want to use the server instance
		Intrepid.setThreadInstance( server );

		final ObjectSlot<VMID> server_connection_slot = new ObjectSlot<VMID>();
		server.addConnectionListener( new ConnectionListener() {
			@Override
			public void connectionOpened( InetAddress host, int port, Object attachment,
				VMID source_vmid,
				VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				Object connection_type_description ) {
				System.out.println( "Server connection opened: " + vmid );
				server_connection_slot.set( vmid );
			}

			@Override
			public void connectionClosed( InetAddress host, int port, VMID source_vmid,
				VMID vmid, Object attachment, boolean will_attempt_reconnect ) {}

			@Override
			public void connectionOpenFailed( InetAddress host, int port,
				Object attachment, Exception error, boolean will_retry ) {}

			@Override
			public void connectionOpening( InetAddress host, int port, Object attachment,
				ConnectionArgs args, Object connection_type_description ) {}
		} );

		VMID server_vmid;
		try {
			server_vmid = client.connect( InetAddress.getLocalHost(),
				server.getServerPort().intValue(), new RequestUserCredentialReinit(),
				null );

			System.out.println( "Connected to: " + server_vmid );
			assertEquals( server.getLocalVMID(), server_vmid );
		}
		catch( IOException ex ) {
			ex.printStackTrace();

			fail( "Should have been able to connect: " + ex );
			return;
		}

		VMID client_vmid = server_connection_slot.waitForValue();
//		System.out.println( "Got client VMID: " + client_vmid );
		assertEquals( client.getLocalVMID(), client_vmid );
		Registry registry = server.getRemoteRegistry( client_vmid );

//		System.out.println( "Got remote registry: " + registry );
		Runnable server_proxy = ( Runnable ) registry.lookup( "test" );
//		System.out.println( "calling proxy...");
		server_proxy.run();
//		System.out.println( "proxy called" );

		UserContextInfo context = context_slot.get();
		System.out.println( "User context: " + context );

		assertNotNull( context );
		assertEquals( "reden", context.getUserName() );
	}


	public void testSuccessfulConnectionLongWaitTryConnect() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );


		//     Client                Server
		//        |---- connect ------->|  (re-init request)
		//        |                     |
		//        |<--- session init ---|  (user credentials)
		//        |                     |
		//        |---- session ack --->|

		final AuthenticationHandler handler = new UserCredentialReinitAuthenticationHandler() {
			@Override
			public UserCredentialsConnectionArgs getUserCredentials(
				SocketAddress remote_address,
				Object session_source ) throws ConnectionAuthFailureException {

//				System.out.println( "--- getUserCredentials(" + remote_address + "," +
//					session_source + ")" );

				ThreadKit.sleep( 40000 );

				return new UserCredentialsConnectionArgs( "reden",
					"password".toCharArray() );
			}

			@Override
			public UserContextInfo checkConnection( ConnectionArgs connection_args,
				SocketAddress remote_address, Object session_source )
				throws ConnectionAuthFailureException {

				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Shouldn't get here" ) );
			}

			@Override
			public void notifyUserCredentialFailure( ResourceKey<String> error ) {}
		};

		// NOTE: Don't start the server yet

		client = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).authHandler(
			new AuthenticationHandler() {
				@Override
				public UserContextInfo checkConnection( ConnectionArgs connection_args,
					SocketAddress remote_address, Object session_source )
					throws ConnectionAuthFailureException {

//					System.out.println( "--- checkConnection(" + connection_args +
//						"," + remote_address + "," + session_source + ")" );

					if ( connection_args instanceof UserCredentialsConnectionArgs ) {
						UserCredentialsConnectionArgs uargs =
							( UserCredentialsConnectionArgs ) connection_args;
						return new SimpleUserContextInfo( uargs.getUser() );
					}

					return null;
				}
			} ) );

		final AtomicReference<UserContextInfo> context_slot =
			new AtomicReference<UserContextInfo>();
		client.getLocalRegistry().bind( "test", new Runnable() {
			@Override
			public void run() {
				System.out.println( "Proxy run() called: " + IntrepidContext.getUserInfo() );
				System.out.println( "  Active instance: " + IntrepidContext.getActiveInstance());
				System.out.println( "  Calling VMID: " + IntrepidContext.getCallingVMID());
				context_slot.set( IntrepidContext.getUserInfo() );
			}
		} );

		// Indicate that we want to use the server instance
		Intrepid.setThreadInstance( server );

		final ObjectSlot<VMID> server_connection_slot = new ObjectSlot<VMID>();

		SharedThreadPool.INSTANCE.schedule( new Runnable() {
				@Override
				public void run() {
					try {
						server = Intrepid.create( new IntrepidSetup().vmidHint(
							"server" ).authHandler( handler ).serverPort( 12345 ) );

						server.addConnectionListener( new ConnectionListener() {
							@Override
							public void connectionOpened( InetAddress host,
								int port, Object attachment, VMID source_vmid, VMID vmid,
								UserContextInfo user_context, VMID previous_vmid,
								Object connection_type_description ) {
								System.out.println( "Server connection opened: " + vmid );
								server_connection_slot.set( vmid );
							}

							@Override
							public void connectionClosed( InetAddress host, int port,
								VMID source_vmid, VMID vmid, Object attachment,
								boolean will_attempt_reconnect ) {}

							@Override
							public void connectionOpenFailed( InetAddress host, int port,
								Object attachment, Exception error, boolean will_retry ) {}

							@Override
							public void connectionOpening( InetAddress host, int port,
								Object attachment, ConnectionArgs args,
								Object connection_type_description ) {}
						} );
					}
					catch ( IOException e ) {
						e.printStackTrace();
					}
				}
			}, 15, TimeUnit.SECONDS );

		VMID server_vmid;
		try {
			server_vmid = client.tryConnect( InetAddress.getLocalHost(),
				12345, new RequestUserCredentialReinit(), null, 1, TimeUnit.MINUTES );

			System.out.println( "Connected to: " + server_vmid );
			assertEquals( server.getLocalVMID(), server_vmid );
		}
		catch( IOException ex ) {
			ex.printStackTrace();

			fail( "Should have been able to connect: " + ex );
			return;
		}

		VMID client_vmid = server_connection_slot.waitForValue();
//		System.out.println( "Got client VMID: " + client_vmid );
		assertEquals( client.getLocalVMID(), client_vmid );
		Registry registry = server.getRemoteRegistry( client_vmid );

//		System.out.println( "Got remote registry: " + registry );
		Runnable server_proxy = ( Runnable ) registry.lookup( "test" );
//		System.out.println( "calling proxy...");
		server_proxy.run();
//		System.out.println( "proxy called" );

		UserContextInfo context = context_slot.get();
		System.out.println( "User context: " + context );

		assertNotNull( context );
		assertEquals( "reden", context.getUserName() );
	}


	public void testReconnection() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );


		//     Client                Server
		//        |---- connect ------->|  (re-init request)
		//        |                     |
		//        |<--- session init ---|  (user credentials)
		//        |                     |
		//        |---- session ack --->|

		AuthenticationHandler handler = new UserCredentialReinitAuthenticationHandler() {
			@Override
			public UserCredentialsConnectionArgs getUserCredentials(
				SocketAddress remote_address,
				Object session_source ) throws ConnectionAuthFailureException {

//				System.out.println( "--- getUserCredentials(" + remote_address + "," +
//					session_source + ")" );

				return new UserCredentialsConnectionArgs( "reden",
					"password".toCharArray() );
			}

			@Override
			public UserContextInfo checkConnection( ConnectionArgs connection_args,
				SocketAddress remote_address, Object session_source )
				throws ConnectionAuthFailureException {

				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Shouldn't get here" ) );
			}

			@Override
			public void notifyUserCredentialFailure( ResourceKey<String> error ) {}
		};

		// Just use a normal auth handler
		server = Intrepid.create( new IntrepidSetup().vmidHint(
			"server" ).authHandler( handler ).serverPort( 0 ) );
		final int server_port = server.getServerPort().intValue();
		VMID original_server_vmid = server.getLocalVMID();
		System.out.println( "Server VMID: " + original_server_vmid );
		client = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).authHandler(
			new AuthenticationHandler() {
				@Override
				public UserContextInfo checkConnection( ConnectionArgs connection_args,
					SocketAddress remote_address, Object session_source )
					throws ConnectionAuthFailureException {

//					System.out.println( "--- checkConnection(" + connection_args +
//						"," + remote_address + "," + session_source + ")" );

					if ( connection_args instanceof UserCredentialsConnectionArgs ) {
						String user =
							( ( UserCredentialsConnectionArgs ) connection_args ).getUser();
						if ( !user.equals( "reden" ) ) {
							throw new ConnectionAuthFailureException(
								new UnlocalizableTextResourceKey( "Bad user: " + user ) );
						}

						UserCredentialsConnectionArgs uargs =
							( UserCredentialsConnectionArgs ) connection_args;
						return new SimpleUserContextInfo( uargs.getUser() );
					}

					return null;
				}
			} ) );
		System.out.println( "Client VMID: " + client.getLocalVMID() );

		final AtomicReference<UserContextInfo> context_slot =
			new AtomicReference<UserContextInfo>();
		client.getLocalRegistry().bind( "test", new Runnable() {
			@Override
			public void run() {
				System.out.println( "Proxy run() called: " + IntrepidContext.getUserInfo() );
				System.out.println( "  Active instance: " + IntrepidContext.getActiveInstance());
				System.out.println( "  Calling VMID: " + IntrepidContext.getCallingVMID());
				context_slot.set( IntrepidContext.getUserInfo() );
			}
		} );

		// Indicate that we want to use the server instance
		Intrepid.setThreadInstance( server );

		final ObjectSlot<VMID> server_connection_slot = new ObjectSlot<VMID>();
		ConnectionListener server_listener = new ConnectionListener() {
			@Override
			public void connectionOpened( InetAddress host, int port, Object attachment,
				VMID source_vmid,
				VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				Object connection_type_description ) {
				System.out.println( "Server connection opened: " + vmid );
				server_connection_slot.set( vmid );
			}

			@Override
			public void connectionClosed( InetAddress host, int port, VMID source_vmid,
				VMID vmid, Object attachment, boolean will_attempt_reconnect ) {}

			@Override
			public void connectionOpenFailed( InetAddress host, int port,
				Object attachment, Exception error, boolean will_retry ) {}

			@Override
			public void connectionOpening( InetAddress host, int port, Object attachment,
				ConnectionArgs args, Object connection_type_description ) {}
		};
		server.addConnectionListener( server_listener );

		VMID server_vmid;
		try {
			server_vmid = client.connect( InetAddress.getLocalHost(),
				server_port, new RequestUserCredentialReinit(),
				null );

			System.out.println( "Connected to: " + server_vmid );
			assertEquals( server.getLocalVMID(), server_vmid );
		}
		catch( IOException ex ) {
			ex.printStackTrace();

			fail( "Should have been able to connect: " + ex );
			return;
		}

		VMID client_vmid = server_connection_slot.waitForValue();
//		System.out.println( "Got client VMID: " + client_vmid );
		assertEquals( client.getLocalVMID(), client_vmid );
		Registry registry = server.getRemoteRegistry( client_vmid );

//		System.out.println( "Got remote registry: " + registry );
		Runnable server_proxy = ( Runnable ) registry.lookup( "test" );
//		System.out.println( "calling proxy...");
		server_proxy.run();
//		System.out.println( "proxy called" );

		UserContextInfo context = context_slot.get();
		System.out.println( "User context: " + context );

		assertNotNull( context );
		assertEquals( "reden", context.getUserName() );

		System.out.println( "Shutting down connection from server side..." );

		final ObjectSlot<VMID> client_connection_slot = new ObjectSlot<VMID>();
		final AtomicBoolean connection_closed_flag = new AtomicBoolean( false );
		client.addConnectionListener( new ConnectionListener() {
			@Override
			public void connectionOpened( InetAddress host, int port, Object attachment,
				VMID source_vmid,
				VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				Object connection_type_description ) {
				client_connection_slot.set( vmid );
			}

			@Override
			public void connectionClosed( InetAddress host, int port, VMID source_vmid,
				VMID vmid,
				Object attachment,
				boolean will_attempt_reconnect ) {

				connection_closed_flag.set( true );

				System.out.println( "Client connection (" + vmid + ") closed: " +
					will_attempt_reconnect );
			}

			@Override
			public void connectionOpenFailed( InetAddress host, int port,
				Object attachment, Exception error, boolean will_retry ) {}

			@Override
			public void connectionOpening( InetAddress host, int port, Object attachment,
				ConnectionArgs args, Object connection_type_description ) {}
		} );

		// Now break the connection from the server side
		server.close();
		server_connection_slot.clear();

		// Make sure the client saw the connection go down
		ThreadKit.sleep( 1000 );
		assertTrue( connection_closed_flag.get() );

		ThreadKit.sleep( 30000 );

		// Make sure the client doesn't think it's connected yet
		assertNull( client_connection_slot.get() );

		System.out.println( "restarting server..." );
		server = Intrepid.create( new IntrepidSetup().vmidHint(
			"server" ).authHandler( handler ).serverPort( server_port ) );
		assertFalse( server.getLocalVMID().equals( original_server_vmid ) );
		server.addConnectionListener( server_listener );
		assertEquals( server_port, server.getServerPort().intValue() );

		client_vmid = server_connection_slot.waitForValue( 30000 );
		assertNotNull( client_vmid );
		System.out.println( "Got RECONNECTED client VMID: " + client_vmid );
		assertEquals( client.getLocalVMID(), client_vmid );

		assertEquals( server.getLocalVMID(), client_connection_slot.waitForValue( 2000 ) );
	}


	public void testReconnectionWithBadUser() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );


		//     Client                Server
		//        |---- connect ------->|  (re-init request)
		//        |                     |
		//        |<--- session init ---|  (user credentials)
		//        |                     |
		//        |---- session ack --->|

		final AtomicReference<String> auth_user = new AtomicReference<String>( "reden" );
		final ObjectSlot<ResourceKey<String>> auth_error_slot =
			new ObjectSlot<ResourceKey<String>>();
		AuthenticationHandler handler = new UserCredentialReinitAuthenticationHandler() {
			@Override
			public UserCredentialsConnectionArgs getUserCredentials(
				SocketAddress remote_address,
				Object session_source ) throws ConnectionAuthFailureException {

//				System.out.println( "--- getUserCredentials(" + remote_address + "," +
//					session_source + ")" );

				return new UserCredentialsConnectionArgs( auth_user.get(),
					"password".toCharArray() );
			}

			@Override
			public UserContextInfo checkConnection( ConnectionArgs connection_args,
				SocketAddress remote_address, Object session_source )
				throws ConnectionAuthFailureException {

				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Shouldn't get here" ) );
			}


			@Override
			public void notifyUserCredentialFailure( ResourceKey<String> error ) {
				auth_error_slot.set( error );
			}
		};

		// Just use a normal auth handler
		server = Intrepid.create( new IntrepidSetup().vmidHint(
			"server" ).authHandler( handler ).serverPort( 0 ) );
		final int server_port = server.getServerPort().intValue();
		VMID original_server_vmid = server.getLocalVMID();
		System.out.println( "Server VMID: " + original_server_vmid );
		client = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).authHandler(
			new AuthenticationHandler() {
				@Override
				public UserContextInfo checkConnection( ConnectionArgs connection_args,
					SocketAddress remote_address, Object session_source )
					throws ConnectionAuthFailureException {

//					System.out.println( "--- checkConnection(" + connection_args +
//						"," + remote_address + "," + session_source + ")" );

					if ( connection_args instanceof UserCredentialsConnectionArgs ) {
						String user =
							( ( UserCredentialsConnectionArgs ) connection_args ).getUser();
						if ( !user.equals( "reden" ) ) {
							throw new ConnectionAuthFailureException(
								new UnlocalizableTextResourceKey( "Bad user: " + user ) );
						}

						UserCredentialsConnectionArgs uargs =
							( UserCredentialsConnectionArgs ) connection_args;
						return new SimpleUserContextInfo( uargs.getUser() );
					}

					return null;
				}
			} ) );
		System.out.println( "Client VMID: " + client.getLocalVMID() );

		final AtomicReference<UserContextInfo> context_slot =
			new AtomicReference<UserContextInfo>();
		client.getLocalRegistry().bind( "test", new Runnable() {
			@Override
			public void run() {
				System.out.println( "Proxy run() called: " + IntrepidContext.getUserInfo() );
				System.out.println( "  Active instance: " + IntrepidContext.getActiveInstance());
				System.out.println( "  Calling VMID: " + IntrepidContext.getCallingVMID());
				context_slot.set( IntrepidContext.getUserInfo() );
			}
		} );

		// Indicate that we want to use the server instance
		Intrepid.setThreadInstance( server );

		final ObjectSlot<VMID> server_connection_slot = new ObjectSlot<VMID>();
		ConnectionListener server_listener = new ConnectionListener() {
			@Override
			public void connectionOpened( InetAddress host, int port, Object attachment,
				VMID source_vmid,
				VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				Object connection_type_description ) {
				System.out.println( "Server connection opened: " + vmid );
				server_connection_slot.set( vmid );
			}

			@Override
			public void connectionClosed( InetAddress host, int port, VMID source_vmid,
				VMID vmid, Object attachment, boolean will_attempt_reconnect ) {}

			@Override
			public void connectionOpenFailed( InetAddress host, int port,
				Object attachment, Exception error, boolean will_retry ) {}

			@Override
			public void connectionOpening( InetAddress host, int port, Object attachment,
				ConnectionArgs args, Object connection_type_description ) {}
		};
		server.addConnectionListener( server_listener );

		VMID server_vmid;
		try {
			server_vmid = client.connect( InetAddress.getLocalHost(),
				server_port, new RequestUserCredentialReinit(),
				null );

			System.out.println( "Connected to: " + server_vmid );
			assertEquals( server.getLocalVMID(), server_vmid );
		}
		catch( IOException ex ) {
			ex.printStackTrace();

			fail( "Should have been able to connect: " + ex );
			return;
		}

		VMID client_vmid = server_connection_slot.waitForValue();
//		System.out.println( "Got client VMID: " + client_vmid );
		assertEquals( client.getLocalVMID(), client_vmid );
		Registry registry = server.getRemoteRegistry( client_vmid );

//		System.out.println( "Got remote registry: " + registry );
		Runnable server_proxy = ( Runnable ) registry.lookup( "test" );
//		System.out.println( "calling proxy...");
		server_proxy.run();
//		System.out.println( "proxy called" );

		UserContextInfo context = context_slot.get();
		System.out.println( "User context: " + context );

		assertNotNull( context );
		assertEquals( "reden", context.getUserName() );

		System.out.println( "Shutting down connection from server side..." );

		final ObjectSlot<VMID> client_connection_slot = new ObjectSlot<VMID>();
		final AtomicBoolean connection_closed_flag = new AtomicBoolean( false );
		client.addConnectionListener( new ConnectionListener() {
			@Override
			public void connectionOpened( InetAddress host, int port, Object attachment,
				VMID source_vmid,
				VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				Object connection_type_description ) {
				client_connection_slot.set( vmid );
			}

			@Override
			public void connectionClosed( InetAddress host, int port, VMID source_vmid,
				VMID vmid,
				Object attachment,
				boolean will_attempt_reconnect ) {

				connection_closed_flag.set( true );

				System.out.println( "Client connection (" + vmid + ") closed: " +
					will_attempt_reconnect );
			}

			@Override
			public void connectionOpenFailed( InetAddress host, int port,
				Object attachment, Exception error, boolean will_retry ) {}

			@Override
			public void connectionOpening( InetAddress host, int port, Object attachment,
				ConnectionArgs args, Object connection_type_description ) {}
		} );

		//noinspection ThrowableResultOfMethodCallIgnored
		assertNull( auth_error_slot.get() );

		// Now break the connection from the server side
		server.close();
		server_connection_slot.clear();

		auth_user.set( "this_will_fail" );

		// Make sure the client saw the connection go down
		ThreadKit.sleep( 1000 );
		assertTrue( connection_closed_flag.get() );

		System.out.println( "restarting server..." );
		server = Intrepid.create( new IntrepidSetup().vmidHint(
			"server" ).authHandler( handler ).serverPort( server_port ) );
		assertFalse( server.getLocalVMID().equals( original_server_vmid ) );
		server.addConnectionListener( server_listener );
		assertEquals( server_port, server.getServerPort().intValue() );

		// Should get an auth error
		//noinspection ThrowableResultOfMethodCallIgnored
		assertNotNull( auth_error_slot.waitForValue( 10000 ) );

		// Shouldn't see a connection
		client_vmid = server_connection_slot.waitForValue( 2000 );
		assertNull( client_vmid );
	}
}
