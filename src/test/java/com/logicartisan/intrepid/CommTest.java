// Copyright (c) 2010 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Intrepid nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.logicartisan.intrepid;

import com.logicartisan.common.core.IOKit;
import com.logicartisan.common.core.thread.SharedThreadPool;
import com.logicartisan.common.core.thread.ThreadKit;
import com.logicartisan.intrepid.exception.InterruptedCallException;
import com.logicartisan.intrepid.exception.IntrepidRuntimeException;
import com.logicartisan.intrepid.exception.NotConnectedException;
import com.logicartisan.intrepid.exception.ServerException;
import com.logicartisan.intrepid.spi.IntrepidSPI;
import junit.framework.TestCase;

import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class CommTest extends TestCase {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	protected IntrepidSPI createSPI( boolean server ) throws Exception {
		// Default
		return null;
	}


	@Override
	protected void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}


	public void testPortReleaseOnShutdown() throws Exception {
		for( int i = 0; i < 100; i++ ) {
			Intrepid instance = Intrepid.create( new IntrepidSetup().serverPort(
				11751 ).openServer().spi( createSPI( true ) ) );
			instance.close();
		}
	}


	public void testSimpleComm() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).serverPort( 11751 ).openServer().spi(
				createSPI( true ) ) );
		ServerImpl original_instance =
			new ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).spi(
			createSPI( false ) ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			11751, null, null );
		assertNotNull( server_vmid );

		assertEquals( server_instance.getLocalVMID(), server_vmid );
		assertFalse( client_instance.getLocalVMID().equals( server_vmid ) );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		Server server = ( Server ) server_registry.lookup( "server" );
		assertNotNull( server );

		// NOTE: Local delegate isn't available here (because it was serialized in the
		//       lookup from the registry since we have the inter-instance bridge disabled)
		assertNull( server_instance.getLocalProxyDelegate( server ) );

		assertTrue( client_instance.isProxy( server ) );
		assertFalse( client_instance.isProxyLocal( server ) );
		assertNull( client_instance.getLocalProxyDelegate( server ) );
		assertNull( client_instance.getLocalProxyDelegate( "Junk" ) );
		assertEquals( server_instance.getLocalVMID(),
			client_instance.getRemoteProxyVMID( server ) );

		assertTrue( server_instance.isProxy( server ) );
		assertTrue( server_instance.isProxyLocal( server ) );

		// NOTE: local won't be able to get local delegate because the instance has been
		//       serialized and the inter-instance bridge is disabled.
		assertNull( server_instance.getLocalProxyDelegate( server ) );


		// Simple call
		assertEquals( "Message from server", server.getMessage() );

		// Declared exception
		try {
			server.testDeclaredException();
			fail( "Should have thrown an exception" );
		}
		catch( IOException ex ) {
			// This is good
			assertEquals( "Test IOException", ex.getMessage() );
		}
		catch( Throwable t ) {
			t.printStackTrace();
			fail( "Unexpected exception: " + t );
		}

		// Undeclared RuntimeException
		try {
			server.testUndeclaredRuntimeException();
			fail( "Should have thrown an exception" );
		}
		catch( NullPointerException ex ) {
			// This is good
		}
		catch( Throwable t ) {
			t.printStackTrace();
			fail( "Unexpected exception: " + t );
		}

		// Undeclared Error
		try {
			server.testUndeclaredError();
			fail( "Should have thrown an error" );
		}
		catch( ServerException ex ) {
			// This is good
			assertNotNull( ex.getCause() );
			assertTrue( Error.class.equals( ex.getCause().getClass() ) );
			assertEquals( "Test Error", ex.getCause().getMessage() );
		}
		catch( Throwable t ) {
			t.printStackTrace();
			fail( "Unexpected exception: " + t );
		}

		// Callback
		ClientImpl original_client = new ClientImpl( true );
		// Should auto-wrap
		assertNull( original_client.input_message );
		Server server_copy =
			server.testCallback( original_client, client_instance.getLocalVMID() );
		assertEquals( "Callback message from server", original_client.input_message );

		assertTrue( Intrepid.isProxy( server_copy ) );
		assertFalse( client_instance.isProxyLocal( server_copy ) );
		assertTrue( server_instance.isProxyLocal( server_copy ) );


		// Disconnect
		client_instance.disconnect( server_vmid );

		try {
			server.getMessage();
			fail( "Shouldn't have worked" );
		}
		catch( NotConnectedException ex ) {
			// this is good
		}
		catch( Exception ex ) {
			ex.printStackTrace();
			fail( "Expected NotConnectedException: " + ex );
		}
	}


	// Same as above, but the inter-instance bridge is enabled so calls to the server
	// will use local handlers. This means the exceptions will not be wrapped in
	// ServerException's.
	public void testInterInstanceBridge() throws Exception {
		// NOTE: leave inter-instance bridge enabled

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).serverPort( 11751 ).openServer().spi(
				createSPI( true ) ) );
		ServerImpl original_instance =
			new ServerImpl( false, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).spi(
			createSPI( false ) ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			11751, null, null );
		assertNotNull( server_vmid );

		assertEquals( server_instance.getLocalVMID(), server_vmid );
		assertFalse( client_instance.getLocalVMID().equals( server_vmid ) );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		Server server = ( Server ) server_registry.lookup( "server" );
		assertNotNull( server );

		assertSame( original_instance, server_instance.getLocalProxyDelegate( server ) );

		// NOTE: Serialize the proxy. We're doing this to ensure that it doesn't have a
		//       local delegate (since this is all being done in the same VM.
		server = ( Server ) IOKit.deserialize( IOKit.serialize( server ) );
		assertTrue( client_instance.isProxy( server ) );
		assertFalse( client_instance.isProxyLocal( server ) );
		assertNull( client_instance.getLocalProxyDelegate( server ) );
		assertNull( client_instance.getLocalProxyDelegate( "Junk" ) );
		assertEquals( server_instance.getLocalVMID(),
			client_instance.getRemoteProxyVMID( server ) );

		assertTrue( server_instance.isProxy( server ) );
		assertTrue( server_instance.isProxyLocal( server ) );
		// NOTE: should be able to get local delegate here (since the inter-instance
		//       bridge is enabled).
		assertSame( original_instance, server_instance.getLocalProxyDelegate( server ) );


		// Simple call
		assertEquals( "Message from server", server.getMessage() );

		// Declared exception
		try {
			server.testDeclaredException();
			fail( "Should have thrown an exception" );
		}
		catch( IOException ex ) {
			// This is good
			assertEquals( "Test IOException", ex.getMessage() );
		}
		catch( Throwable t ) {
			t.printStackTrace();
			fail( "Unexpected exception: " + t );
		}

		// Undeclared RuntimeException
		try {
			server.testUndeclaredRuntimeException();
			fail( "Should have thrown an exception" );
		}
		catch( NullPointerException ex ) {
			// This is good
			assertEquals( "Test NullPointerException", ex.getMessage() );
		}
		catch( Throwable t ) {
			t.printStackTrace();
			fail( "Unexpected exception: " + t );
		}

		// Undeclared Error
		try {
			server.testUndeclaredError();
			fail( "Should have thrown an error" );
		}
		catch( Error ex ) {
			// This is good
			assertTrue( Error.class.equals( ex.getClass() ) );
			assertEquals( "Test Error", ex.getMessage() );
		}
		catch( Throwable t ) {
			t.printStackTrace();
			fail( "Unexpected exception: " + t );
		}

		// Callback
		ClientImpl original_client = new ClientImpl( false );
		// Should auto-wrap
		assertNull( original_client.input_message );
		Server server_copy =
			server.testCallback( original_client, client_instance.getLocalVMID() );
		assertEquals( "Callback message from server", original_client.input_message );
		assertNotNull( server_copy );

		// Won't be a proxy since the bridge is active
		assertFalse( Intrepid.isProxy( server_copy ) );
	}

	public void testSerializationErrors() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).serverPort( 11751 ).openServer().spi(
				createSPI( true ) ) );
		ServerImpl original_instance =
			new ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).spi(
				createSPI( false ) ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			11751, null, null );
		assertNotNull( server_vmid );

		assertEquals( server_instance.getLocalVMID(), server_vmid );
		assertFalse( client_instance.getLocalVMID().equals( server_vmid ) );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		Server server = ( Server ) server_registry.lookup( "server" );
		assertNotNull( server );


		try {
			server.testUnserializable( new UnserializableClass() );
			fail( "Shouldn't have been serializable" );
		}
		catch( IntrepidRuntimeException ex ) {
			// This is good
			assertNotNull( ex.getCause() );
			assertTrue( ex.getCause() instanceof NotSerializableException );
		}
		catch( Exception ex ) {
			ex.printStackTrace();
			fail( "Unexpected exception type: " + ex );
		}

		try {
			server.testSerializationError( new SerializationErrorClass() );
			fail( "Shouldn't have been serializable" );
		}
		catch( IntrepidRuntimeException ex ) {
			// This is good
			assertNotNull( ex.getCause() );
			assertEquals( "Test IOException", ex.getCause().getMessage() );
		}

		try {
			server.testSerializationError2( new SerializationErrorClass2() );
			fail( "Shouldn't have been serializable" );
		}
		catch( IntrepidRuntimeException ex ) {
			// This is good
			assertNotNull( ex.getCause() );
			assertTrue( ex.getCause() instanceof IOException );
			assertNotNull( ex.getCause().getCause() );
			assertEquals( "Test NullPointerException",
				ex.getCause().getCause().getMessage() );
		}

		try {
			UnserializableClass ret = server.testUnserializableReturn();
			fail( "Shouldn't have been serializable" );
		}
		catch( IntrepidRuntimeException ex ) {
			// This is good
			assertNotNull( ex.getCause() );
			assertTrue( ex.getCause() instanceof NotSerializableException );
		}
		catch( Exception ex ) {
			ex.printStackTrace();
			fail( "Unexpected exception type: " + ex );
		}

		try {
			SerializationErrorClass ret = server.testSerializationReturnError();
			fail( "Shouldn't have been serializable" );
		}
		catch( IntrepidRuntimeException ex ) {
			// This is good
			assertNotNull( ex.getCause() );
			assertEquals( "Test IOException", ex.getCause().getMessage() );
		}

		try {
			SerializationErrorClass2 ret = server.testSerializationReturnError2();
			fail( "Shouldn't have been serializable" );
		}
		catch( IntrepidRuntimeException ex ) {
			// This is good
			assertNotNull( ex.getCause() );
			assertTrue( ex.getCause() instanceof IOException );
			assertNotNull( ex.getCause().getCause() );
			assertEquals( "Test NullPointerException",
				ex.getCause().getCause().getMessage() );
		}
	}


	public void testConnectFailure() throws Exception {
		Intrepid client = Intrepid.create( new IntrepidSetup().spi( createSPI( false ) ) );
		try {
			client.connect( InetAddress.getByName( "127.0.0.1" ), 11751, null, null );
			fail( "Shouldn't have worked" );
		}
		catch( ConnectException ex ) {
			// This is good
			System.out.println( "Exception was: " + ex );
		}
		catch( IOException ex ) {
			ex.printStackTrace();
			fail( "Should have been a ConnectException: " + ex );
		}
	}


	// TODO: this test is proving to be problematic
//	public void testConnectInterrupt() throws Exception {
//		Intrepid client = Intrepid.create( new IntrepidSetup().spi( createSPI( false ) ) );
//		try {
//			final Thread test_thread = Thread.currentThread();
//			new Thread() {
//				@Override
//				public void run() {
//					ThreadKit.sleep( 2000 );
//
//					test_thread.interrupt();
//				}
//			}.start();
//
//			// Should time out
//			// This is a server known to drop packets on this port
//			client.connect( InetAddress.getByName( "europa-house.starlight-systems.com" ),
//				11751, null, null );
//			fail( "Shouldn't have worked" );
//		}
//		catch( InterruptedIOException ex ) {
//			// This is good
//			System.out.println( "Exception was: " + ex );
//		}
//		catch( IOException ex ) {
//			ex.printStackTrace();
//			fail( "Should have been an InterruptedIOException" + ex );
//		}
//	}


	public void testTryConnect() throws Exception {
		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).spi(
			createSPI( false ) ) );

		// Connect and fail immediately, since the server isn't there
		try {
			client_instance.connect( InetAddress.getByName( "127.0.0.1" ), 11751, null,
				null );
			fail( "Shouldn't have been able to connect" );
		}
		catch( ConnectException ex ) {
			// this is good
		}

		// Try to connect, but never start the server
		long time = System.currentTimeMillis();
		try {
			client_instance.tryConnect( InetAddress.getByName( "127.0.0.1" ), 11751, null,
				null, 3, TimeUnit.SECONDS );
		}
		catch ( ConnectException ex ) {
			// this is good
			time = System.currentTimeMillis() - time;
		}

		assertTrue( time + " < 3000", time >= 3000 );
		assertTrue( time + " > 5000", time <= 5000 );

		// Start the server in 3 seconds
		SharedThreadPool.INSTANCE.schedule( new Runnable() {
			@Override
			public void run() {
				try {
					server_instance = Intrepid.create( new IntrepidSetup().vmidHint(
						"server" ).serverPort( 11751 ).openServer().spi(
						createSPI( true ) ) );
				}
				catch( Exception ex ) {
					ex.printStackTrace();
					fail( "Unexpected exception: " + ex );
				}
			}
		}, 3100, TimeUnit.MILLISECONDS );

		// Try to connect, the server should be available part way through
		time = System.currentTimeMillis();
		VMID server_vmid = null;
		try {
			server_vmid = client_instance.tryConnect(
				InetAddress.getByName( "127.0.0.1" ), 11751, null, null, 6,
				TimeUnit.SECONDS );
		}
		catch (  ConnectException ex ) {
			// this is good
		}
		finally {
			time = System.currentTimeMillis() - time;
		}

		assertTrue( time + " <= 3000", time > 3000 );
		assertTrue( time + " > 5000", time <= 5000 );

		assertNotNull( server_vmid );
		assertEquals( server_instance.getLocalVMID(), server_vmid );
	}


	public void testNestedInterfaces() throws Exception {
		ClientImpl2 client_impl = new ClientImpl2();

		client_instance = Intrepid.create( new IntrepidSetup().spi( createSPI( false ) ) );

		try {
			Client client = ( Client ) client_instance.createProxy( client_impl );
			// This is good
		}
		catch( ClassCastException ex ) {
			ex.printStackTrace();
			fail( "Unable to cast proxy to Client" );
		}
	}


	public void testInterrupt() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer().spi(
			createSPI( true ) ) );
		Integer server_port = server_instance.getServerPort();
		assertNotNull( server_port );
		ServerImpl original_instance =
			new ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).spi(
			createSPI( false ) ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_port.intValue(), null, null );
		assertNotNull( server_vmid );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		Server server = ( Server ) server_registry.lookup( "server" );

		final Thread to_interrupt = Thread.currentThread();

		SharedThreadPool.INSTANCE.schedule( to_interrupt::interrupt, 2, TimeUnit.SECONDS );

		try {
			server.waitALongTime();
			fail( "Should have been interrupted" );
		}
		catch( InterruptedCallException ex ) {
			// this is good
		}
		catch( IntrepidRuntimeException ex ) {
			ex.printStackTrace();
			fail( "Unexpected exception: " + ex );
		}

		assertTrue( server.wasInterrupted() );
	}


	public void testServerCloseInMethod() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer().spi( createSPI( true ) ) );
		Integer server_port = server_instance.getServerPort();
		assertNotNull( server_port );
		ServerImpl original_instance =
			new ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).spi(
			createSPI( false ) ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_port.intValue(), null, null );
		assertNotNull( server_vmid );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		Server server = ( Server ) server_registry.lookup( "server" );

		SharedThreadPool.INSTANCE.schedule( server_instance::close, 2, TimeUnit.SECONDS );

		try {
			server.waitALongTime();
			fail( "Should have been interrupted" );
		}
		catch( InterruptedCallException ex ) {
			// this is good
		}
		catch( IntrepidRuntimeException ex ) {
			ex.printStackTrace();
			fail( "Unexpected exception: " + ex );
		}
	}


	public void testSendNonSerializableClass() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).openServer().spi( createSPI( true ) ) );
		Integer server_port = server_instance.getServerPort();
		assertNotNull( server_port );
		ServerImpl original_instance =
			new ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).spi(
			createSPI( false ) ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_port.intValue(), null, null );
		assertNotNull( server_vmid );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		Server server = ( Server ) server_registry.lookup( "server" );

		Class system_class = server.copyClass( System.class );
		assertEquals( System.class, system_class );
	}


	public void testFirstConnectFail() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );



		// Create the client first and try to connect to the server, which won't be there
		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ).spi(
			createSPI( false ) ) );
		try {
			client_instance.connect( InetAddress.getLocalHost(), 11751, null, null );
			fail( "Shouldn't have been able to connect" );
		}
		catch( IOException ex ) {
			// this is expected
		}

		// Set up the server
		server_instance = Intrepid.create( new IntrepidSetup().vmidHint(
			"server" ).openServer().serverPort( 11751 ).spi( createSPI( true ) ) );

		// Try to connect again, should work this time
		VMID server_vmid =
			client_instance.connect( InetAddress.getLocalHost(), 11751, null, null );
		assertEquals( server_instance.getLocalVMID(), server_vmid );

		long time = client_instance.ping( server_vmid, 1, TimeUnit.SECONDS );
		assertTrue( time < 1000 );
	}


	private static void checkForRemoteCall( boolean expect_remote_call ) {
		// Make sure InvokeRunner is (or isn't) in the call stack
		boolean found_it = false;
		StackTraceElement[] stack = new Throwable().getStackTrace();
		for( StackTraceElement element : stack ) {
			if ( element.getClassName().equals( InvokeRunner.class.getName() ) ) {
				found_it = true;
				break;
			}
		}
		assertEquals( expect_remote_call, found_it );
	}


	public static interface Server {
		public String getMessage();
		public Class copyClass( Class clazz );

		public Server testCallback( Client client, VMID client_vmid );

		public void testDeclaredException() throws IOException;
		public void testUndeclaredRuntimeException();
		public void testUndeclaredError();

		public void testUnserializable( UnserializableClass obj );
		public void testSerializationError( SerializationErrorClass obj );
		public void testSerializationError2( SerializationErrorClass2 obj );

		public UnserializableClass testUnserializableReturn();
		public SerializationErrorClass testSerializationReturnError();
		public SerializationErrorClass2 testSerializationReturnError2();

		public void waitALongTime();
		public boolean wasInterrupted();
	}


	public static interface Client {
		public String getMessage( String message, VMID server_vmid );
	}


	public static class ServerImpl implements Server {
		private final boolean expect_remote_call;
		private final VMID server_vmid;

		private boolean interrupted = false;

		public ServerImpl( boolean expect_remote_call, VMID server_vmid ) {
			this.expect_remote_call = expect_remote_call;
			this.server_vmid = server_vmid;
		}

		@Override
		public String getMessage() {
			assertTrue( IntrepidContext.isCall() );
			checkForRemoteCall( expect_remote_call );
			return "Message from server";
		}

		@Override
		public Class copyClass( Class clazz ) {
			return clazz;
		}

		@Override
		public Server testCallback( Client client, VMID client_vmid ) {
			assertTrue( IntrepidContext.isCall() );
			if ( expect_remote_call ) {
				assertEquals( client_vmid, IntrepidContext.getCallingVMID() );
				assertNotNull( IntrepidContext.getCallingHost() );
			}
			else {
				assertEquals( server_vmid, IntrepidContext.getCallingVMID() );
				assertNull( IntrepidContext.getCallingHost() );
			}

			checkForRemoteCall( expect_remote_call );

			if ( expect_remote_call ) {
				assertTrue( client instanceof Proxy );	// make sure it's a proxy
			}

			String message =
				client.getMessage( "Callback message from server", server_vmid );
			assertEquals( "Message from client", message );

			// Should auto-wrap here
			return this;
		}

		@Override
		public void testDeclaredException() throws IOException {
			assertTrue( IntrepidContext.isCall() );
			checkForRemoteCall( expect_remote_call );
			throw new IOException( "Test IOException" );
		}

		@Override
		public void testUndeclaredRuntimeException() {
			assertTrue( IntrepidContext.isCall() );
			checkForRemoteCall( expect_remote_call );
			throw new NullPointerException( "Test NullPointerException" );
		}

		@Override
		public void testUndeclaredError() {
			assertTrue( IntrepidContext.isCall() );
			checkForRemoteCall( expect_remote_call );
			throw new Error( "Test Error" );
		}

		@Override
		public void testSerializationError( SerializationErrorClass obj ) {}

		@Override
		public void testUnserializable( UnserializableClass obj ) {}

		@Override
		public void testSerializationError2( SerializationErrorClass2 obj ) {}

		@Override
		public SerializationErrorClass testSerializationReturnError() {
			return new SerializationErrorClass();
		}

		@Override
		public UnserializableClass testUnserializableReturn() {
			return new UnserializableClass();
		}

		@Override
		public SerializationErrorClass2 testSerializationReturnError2() {
			return new SerializationErrorClass2();
		}

		@Override
		public void waitALongTime() {
//			System.out.println( "waiting..." );
			interrupted = !ThreadKit.sleep( 10000 );
//			System.out.println( "done waiting" );
//			System.out.println( "waitALongTime INTERRUPTED: " + interrupted );
		}

		@Override
		public boolean wasInterrupted() {
			return interrupted;
		}
	}


	public static class ClientImpl implements Client {
		private final boolean expect_remote_call;

		public volatile String input_message = null;

		ClientImpl( boolean expect_remote_call ) {
			this.expect_remote_call = expect_remote_call;
		}

		@Override
		public String getMessage( String message, VMID server_vmid ) {
			assertTrue( IntrepidContext.isCall() );
			assertEquals( server_vmid, IntrepidContext.getCallingVMID() );

			checkForRemoteCall( expect_remote_call );

			input_message = message;
			return "Message from client";
		}
	}


	public static class UnserializableClass {}

	public static class SerializationErrorClass implements Externalizable {
		@Override
		public void readExternal( ObjectInput in )
			throws IOException, ClassNotFoundException {}

		@Override
		public void writeExternal( ObjectOutput out ) throws IOException {
			throw new IOException( "Test IOException" );
		}
	}

	public static class SerializationErrorClass2 implements Externalizable {
		@Override
		public void readExternal( ObjectInput in )
			throws IOException, ClassNotFoundException {}

		@Override
		public void writeExternal( ObjectOutput out ) throws IOException {
			throw new NullPointerException( "Test NullPointerException" );
		}
	}


	public static abstract class AbstractClient implements Client {}


	public static class ClientImpl2 extends AbstractClient {
		@Override
		public String getMessage( String message, VMID server_vmid ) {
			return message;
		}
	}
}
