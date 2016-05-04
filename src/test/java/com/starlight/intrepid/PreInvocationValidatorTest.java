package com.starlight.intrepid;

import com.starlight.intrepid.auth.MethodInvocationRefusedException;
import com.starlight.intrepid.auth.PreInvocationValidator;
import junit.framework.TestCase;

import java.net.InetAddress;

/**
 *
 */
public class PreInvocationValidatorTest extends TestCase {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	@Override
	protected void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}


	public void testRejectedCall() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		InetAddress localhost = InetAddress.getByName( "127.0.0.1" );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		PreInvocationValidator validator =
			( instance, calling_vmid, calling_host, user_context, method, target, args ) -> {

			assertEquals( client_instance.getLocalVMID(), calling_vmid );
			assertEquals( localhost, calling_host );

			if ( !method.getName().equals( "doForLove" ) ) return;

			if ( args[ 0 ].equals( "Foo" ) ) {
				throw new MethodInvocationRefusedException(
					"I would do anything for love, but I won't do that" );
			}
		};


		server_instance = Intrepid.create(
			new IntrepidSetup()
				.vmidHint( "server" )
				.serverPort( 11751 )
				.openServer()
				.preInvocationValidator( validator ) );
		ServerImpl original_instance = new ServerImpl();
		server_instance.getLocalRegistry().bind( "server", original_instance );

		// Connect to the server
		VMID server_vmid = client_instance.connect( localhost,
			11751, null, null );
		assertNotNull( server_vmid );

		assertEquals( server_instance.getLocalVMID(), server_vmid );
		assertFalse( client_instance.getLocalVMID().equals( server_vmid ) );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		Server server = ( Server ) server_registry.lookup( "server" );
		assertNotNull( server );

		server.doForLove( "Bar" );

		try {
			server.doForLove( "Foo" );
			fail( "Shouldn't have worked" );
		}
		catch( MethodInvocationRefusedException ex ) {
			// this is good
		}
	}


	public interface Server {
		void doForLove( String thing );
	}


	public static class ServerImpl implements Server {
		@Override
		public void doForLove( String thing ) {}
	}
}
