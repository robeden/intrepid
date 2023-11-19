package com.starlight.intrepid;

import com.starlight.intrepid.auth.SimpleUserContextInfo;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.auth.UserCredentialsConnectionArgs;
import junit.framework.TestCase;

import java.net.InetAddress;
import java.net.InetSocketAddress;


/**
 *
 */
public class ChainedCallUserContextTest extends TestCase {
	Intrepid tail_instance;
	Intrepid head_instance;
	Intrepid client_instance;

	@Override
	protected void tearDown() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( tail_instance != null ) tail_instance.close();
		if ( head_instance != null ) head_instance.close();
		if ( client_instance != null ) client_instance.close();
	}

	// Test call through three instances to verify UserContext is passed through
	//
	//  Client -> Head -> Tail
	//
	// The UserContextInfo should come from the Client->Head connection since the
	// Head->Tail connection is a server connection.
	public void testChainedCall() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		// Setup tail
		TailInstance tail_proxy_instance = new TailInstance();
		tail_instance = Intrepid.newBuilder().openServer().build();
		tail_instance.getLocalRegistry().bind( "lib/test", tail_proxy_instance );

		// Setup head & connect to tail
		Intrepid head_instance = Intrepid.newBuilder()
			.serverAddress( new InetSocketAddress( 0 ) )
			.authHandler( ( connection_args, remote_address, session_source ) -> {
				UserCredentialsConnectionArgs user_args =
					( UserCredentialsConnectionArgs ) connection_args;
				return new SimpleUserContextInfo( user_args.getUser() );
			} )
			.build();
		VMID vmid = head_instance.connect( InetAddress.getLoopbackAddress(),
			tail_instance.getServerPort().intValue(), null, null );
		assertEquals( tail_instance.getLocalVMID(), vmid );
		HeadInstance head_proxy_instance = new HeadInstance(
			( Runnable ) head_instance.getRemoteRegistry(
			tail_instance.getLocalVMID() ).lookup( "lib/test" ) );
		head_instance.getLocalRegistry().bind( "lib/test", head_proxy_instance );

		// Setup client and connect to head
		client_instance = Intrepid.newBuilder().build();
		vmid = client_instance.connect( InetAddress.getLoopbackAddress(),
			head_instance.getServerPort().intValue(),
			new UserCredentialsConnectionArgs( "reden", "hello".toCharArray() ), null );
		assertEquals( head_instance.getLocalVMID(), vmid );

		Runnable head_proxy =
			( Runnable ) client_instance.getRemoteRegistry( vmid ).lookup( "lib/test" );

		// Call the proxy. This should propagate down to the tail
		head_proxy.run();

		// Verify the tail had our credentials
		assertNotNull( tail_proxy_instance.context );
		assertEquals( "reden", tail_proxy_instance.context.getUserName() );
	}


	// Same as testChainedCall in setup, except the Head->Tail connection is a user
	// authenticated connection. This means that the UserContextInfo should come from
	// that connection rather than the Client->Head connection.
	public void testChainedCallOverUserAuthConnection() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		// Setup tail
		TailInstance tail_proxy_instance = new TailInstance();
		tail_instance = Intrepid.newBuilder()
			.serverAddress( new InetSocketAddress( 0 ) )
			.authHandler(
				( connection_args, remote_address, session_source ) -> {
					UserCredentialsConnectionArgs user_args =
						( UserCredentialsConnectionArgs ) connection_args;
					return new SimpleUserContextInfo( user_args.getUser() );
				} )
			.build();
		tail_instance.getLocalRegistry().bind( "lib/test", tail_proxy_instance );

		// Setup head & connect to tail
		head_instance = Intrepid.newBuilder()
			.serverAddress( new InetSocketAddress( 0 ) )
			.authHandler( ( connection_args, remote_address, session_source ) -> {
				UserCredentialsConnectionArgs user_args =
					( UserCredentialsConnectionArgs ) connection_args;
				return new SimpleUserContextInfo( user_args.getUser() );
			} )
			.build();
		VMID vmid = head_instance.connect( InetAddress.getLoopbackAddress(),
			tail_instance.getServerPort().intValue(),
			new UserCredentialsConnectionArgs( "**NOT**reden", "blah".toCharArray() ),
			null );
		assertEquals( tail_instance.getLocalVMID(), vmid );
		HeadInstance head_proxy_instance = new HeadInstance(
			( Runnable ) head_instance.getRemoteRegistry(
			tail_instance.getLocalVMID() ).lookup( "lib/test" ) );
		head_instance.getLocalRegistry().bind( "lib/test", head_proxy_instance );

		// Setup client and connect to head
		client_instance = Intrepid.newBuilder().build();
		vmid = client_instance.connect( InetAddress.getLoopbackAddress(),
			head_instance.getServerPort().intValue(),
			new UserCredentialsConnectionArgs( "reden", "hello".toCharArray() ), null );
		assertEquals( head_instance.getLocalVMID(), vmid );

		Runnable head_proxy =
			( Runnable ) client_instance.getRemoteRegistry( vmid ).lookup( "lib/test" );

		// Call the proxy. This should propagate down to the tail
		head_proxy.run();

		// Verify the tail had our credentials
		assertNotNull( tail_proxy_instance.context );
		assertEquals( "**NOT**reden", tail_proxy_instance.context.getUserName() );
	}


	public static class TailInstance implements Runnable {
		public UserContextInfo context = null;

		@Override
		public void run() {
			context = IntrepidContext.getUserInfo();
		}
	}


	public static class HeadInstance implements Runnable {
		Runnable tail;

		HeadInstance( Runnable tail ) {
			this.tail = tail;
		}

		@Override
		public void run() {
			tail.run();
		}
	}
}
