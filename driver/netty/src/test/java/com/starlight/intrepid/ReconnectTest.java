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

package com.starlight.intrepid;

import com.logicartisan.common.core.thread.ThreadKit;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.SimpleUserContextInfo;
import com.starlight.intrepid.auth.TokenReconnectAuthenticationHandler;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.exception.IntrepidRuntimeException;
import com.starlight.intrepid.exception.NotConnectedException;
import org.easymock.EasyMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


/**
 *
 */
// TODO!
@Disabled
public class ReconnectTest {
	private Intrepid server_instance = null;
	private Intrepid client_instance = null;

	@AfterEach
	public void tearDown() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( server_instance != null ) server_instance.close();
		if ( client_instance != null ) client_instance.close();
	}

	@Timeout(value = 2, unit = TimeUnit.MINUTES)
	@Test
	public void testReconnect() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.newBuilder().openServer().build();
		Integer port = server_instance.getServerPort();
		assertNotNull(port);

		client_instance = Intrepid.newBuilder().build();
		VMID server_vmid = client_instance.connect( InetAddress.getLoopbackAddress(),
			port.intValue(), null, null );
		assertEquals(server_instance.getLocalVMID(), server_vmid);


		CommTest.ServerImpl server_impl =
			new CommTest.ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", server_impl );

		CommTest.Server proxy = ( CommTest.Server ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );

		// Make a call... should work
		proxy.getMessage();


		// Shut down the server
		server_instance.close();
		System.out.println( "server instance closed" );

		ThreadKit.sleep( 1000 );

		try {
			// Make a call... should fail
			proxy.getMessage();
			fail("Shouldn't have worked");
		}
		catch ( NotConnectedException ex ) {
			// This is good
		}
		catch ( IntrepidRuntimeException ex ) {
			ex.printStackTrace();
			fail("Unexpected exception: " + ex);
		}

		server_instance = Intrepid.newBuilder()
			.serverAddress( new InetSocketAddress( port ) )
			.openServer()
			.build();
		System.out.println( "Server instance recreated: " + port );
		server_instance.getLocalRegistry().bind( "server", server_impl );



		boolean reconnected = false;
		for( int i = 0; i < 40; i++ ) {
			ThreadKit.sleep( 500 );

			try {
				proxy.getMessage();
				reconnected = true;
				break;
			}
			catch ( IntrepidRuntimeException ex ) {
				// expected
				System.out.println( ex );
			}
		}

		assertTrue(reconnected);
		System.out.println( "Reconnected!!" );
	}

	@Timeout(value = 2, unit = TimeUnit.MINUTES)
	@Test
	public void testReconnect2() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.newBuilder().openServer().build();
		Integer port = server_instance.getServerPort();
		assertNotNull(port);

		client_instance = Intrepid.newBuilder().build();
		VMID server_vmid = client_instance.connect( InetAddress.getLoopbackAddress(),
			port.intValue(), null, null );
		assertEquals(server_instance.getLocalVMID(), server_vmid);


		CommTest.ServerImpl server_impl =
			new CommTest.ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", server_impl );

		CommTest.Server proxy = ( CommTest.Server ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );

		// Make a call... should work
		proxy.getMessage();


		// Shut down the server
		server_instance.close();
		System.out.println( "server instance closed" );

		ThreadKit.sleep( 1000 );

		try {
			// Make a call... should fail
			proxy.getMessage();
			fail("Shouldn't have worked");
		}
		catch ( NotConnectedException ex ) {
			// This is good
		}
		catch ( IntrepidRuntimeException ex ) {
			ex.printStackTrace();
			fail("Unexpected exception: " + ex);
		}

		try {
			client_instance.connect(
				InetAddress.getLoopbackAddress(), port.intValue(), null, null );
			fail("Shouldn't work");
		}
		catch( ConnectException ex ) {
			// this is good... timeout
		}

		Registry r = client_instance.getRemoteRegistry( server_vmid );

		try {
			r.lookup( "server" );
			fail("Shouldn't have worked");
		}
		catch( NotConnectedException ex ) {
			// This is good
		}

		server_instance = Intrepid.newBuilder()
			.serverAddress( new InetSocketAddress( port ) )
			.openServer()
			.build();
		System.out.println( "Server instance recreated: " + port );
		server_instance.getLocalRegistry().bind( "server", server_impl );



		boolean reconnected = false;
		for( int i = 0; i < 40; i++ ) {
			ThreadKit.sleep( 500 );

			try {
				proxy.getMessage();
				reconnected = true;
				break;
			}
			catch ( IntrepidRuntimeException ex ) {
				// expected
				System.out.println( ex );
			}
		}

		assertTrue(reconnected);
		System.out.println( "Reconnected!!" );
	}


	@Test
	public void testConnectTwice() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.newBuilder().openServer().build();
		Integer port = server_instance.getServerPort();
		assertNotNull(port);

		client_instance = Intrepid.newBuilder().build();

		final AtomicBoolean new_connection_flag = new AtomicBoolean( false );
		client_instance.addConnectionListener( new ConnectionListener() {
			@Override
			public void connectionOpened( @Nonnull SocketAddress socket_address,
				Object attachment, @Nonnull VMID source_vmid, @Nonnull VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				@Nonnull Object connection_type_description, byte ack_rate_sec ) {

				new_connection_flag.set( true );
			}

			@Override
			public void connectionClosed( @Nonnull SocketAddress socket_address,
				@Nonnull VMID source_vmid, @Nullable VMID vmid,
				@Nullable Object attachment, boolean will_attempt_reconnect,
				@Nullable UserContextInfo user_context ) {}

			@Override
			public void connectionOpenFailed( @Nonnull SocketAddress socket_address,
				Object attachment, Exception error, boolean will_retry ) {}

			@Override
			public void connectionOpening( @Nonnull SocketAddress socket_address,
				Object attachment, ConnectionArgs args,
				@Nonnull Object connection_type_description ) {}
		} );

		VMID server_vmid = client_instance.connect( InetAddress.getLoopbackAddress(),
			port.intValue(), null, null );

		assertTrue(new_connection_flag.getAndSet( false ));

		// Now connect again and make sure we get the same VMID
		VMID server_vmid2 = client_instance.connect( InetAddress.getLoopbackAddress(),
			port.intValue(), null, null );

		assertFalse(new_connection_flag.get());

		assertEquals(server_vmid, server_vmid2);
	}


	@Test
	public void testTwoQuickConnections() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.newBuilder().openServer().build();
		Integer port = server_instance.getServerPort();
		assertNotNull(port);

		client_instance = Intrepid.newBuilder().build();

		final AtomicInteger connections = new AtomicInteger( 0 );
		client_instance.addConnectionListener( new ConnectionListener() {
			@Override
			public void connectionOpened( @Nonnull SocketAddress socket_address,
				Object attachment, @Nonnull VMID source_vmid, @Nonnull VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				@Nonnull Object connection_type_description, byte ack_rate_sec ) {

				connections.incrementAndGet();
			}

			@Override
			public void connectionClosed( @Nonnull SocketAddress socket_address,
				@Nonnull VMID source_vmid, @Nullable VMID vmid,
				@Nullable Object attachment, boolean will_attempt_reconnect,
				@Nullable UserContextInfo user_context ) {}

			@Override
			public void connectionOpenFailed( @Nonnull SocketAddress socket_address,
				Object attachment, Exception error, boolean will_retry ) {}

			@Override
			public void connectionOpening( @Nonnull SocketAddress socket_address,
				Object attachment, ConnectionArgs args,
				@Nonnull Object connection_type_description ) {}
		} );

		VMID server_vmid = client_instance.connect( InetAddress.getLoopbackAddress(),
			port.intValue(), null, null );

		// Now connect again and make sure we get the same VMID
		VMID server_vmid2 = client_instance.connect( InetAddress.getLoopbackAddress(),
			port.intValue(), null, null );

		assertEquals(server_vmid, server_vmid2);
		assertEquals(1, connections.get());
	}


	@Test
	public void testTokenReconnection() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		TokenReconnectAuthenticationHandler mock_auth_handler =
			EasyMock.createMock( TokenReconnectAuthenticationHandler.class );

		UserContextInfo test_info = new SimpleUserContextInfo( "test_user" );

		// Mock for first call - no token should be provided
		EasyMock.expect( mock_auth_handler.checkConnection( EasyMock.<ConnectionArgs>isNull(),
			EasyMock.<SocketAddress>notNull(), EasyMock.<SocketAddress>notNull(),
			EasyMock.<ConnectionArgs>isNull() ) ).andReturn( test_info );
		EasyMock.expect( mock_auth_handler.generateReconnectToken( EasyMock.eq( test_info ),
			EasyMock.<ConnectionArgs>isNull(),
			EasyMock.<SocketAddress>notNull(), EasyMock.<SocketAddress>notNull(),
			EasyMock.<ConnectionArgs>isNull() ) ).andReturn( "my test token" );
		EasyMock.expect(mock_auth_handler.getTokenRegenerationInterval()).andReturn(
            2);
		EasyMock.expect( mock_auth_handler.generateReconnectToken( EasyMock.eq( test_info ),
			EasyMock.<ConnectionArgs>isNull(),
			EasyMock.<SocketAddress>notNull(), EasyMock.<SocketAddress>notNull(),
			EasyMock.eq( "my test token" ) ) ).andReturn( "my test token - TWO" );
		EasyMock.expect(mock_auth_handler.getTokenRegenerationInterval()).andReturn(
            2);
		EasyMock.expect( mock_auth_handler.generateReconnectToken( EasyMock.eq( test_info ),
			EasyMock.<ConnectionArgs>isNull(),
			EasyMock.<SocketAddress>notNull(), EasyMock.<SocketAddress>notNull(),
			EasyMock.eq( "my test token - TWO" ) ) ).andReturn( "my test token - THREE" );
		EasyMock.expect(mock_auth_handler.getTokenRegenerationInterval()).andReturn(
            2);

		EasyMock.replay( mock_auth_handler );

		Intrepid.Builder setup = Intrepid.newBuilder();
		setup.authHandler( mock_auth_handler );
		setup.serverAddress( new InetSocketAddress( 0 ) );
		server_instance = setup.build();
		Integer port = server_instance.getServerPort();
		assertNotNull(port);

		client_instance = Intrepid.newBuilder().build();
		VMID server_vmid = client_instance.connect( InetAddress.getLoopbackAddress(),
			port.intValue(), null, null );
		assertEquals(server_instance.getLocalVMID(), server_vmid);

		CommTest.ServerImpl server_impl =
			new CommTest.ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", server_impl );

		CommTest.Server proxy = ( CommTest.Server ) client_instance.getRemoteRegistry(
			server_vmid ).lookup( "server" );

		// Make a call... should work
		proxy.getMessage();

		ThreadKit.sleep( 5000 );    // time to regen two session tokens

		// Shut down the server
		server_instance.close();
		System.out.println( "server instance closed" );

		// Make sure checkConnection was called once and only once
		EasyMock.verify( mock_auth_handler );

		// Mock for second call - token SHOULD be provided
		EasyMock.reset( mock_auth_handler );
		EasyMock.expect( mock_auth_handler.checkConnection( EasyMock.<ConnectionArgs>isNull(),
			EasyMock.<SocketAddress>notNull(), EasyMock.<SocketAddress>notNull(),
			EasyMock.eq( "my test token - THREE" ) ) ).andReturn( test_info );
		EasyMock.expect( mock_auth_handler.generateReconnectToken( EasyMock.eq( test_info ),
			EasyMock.<ConnectionArgs>isNull(), EasyMock.<SocketAddress>notNull(),
			EasyMock.<SocketAddress>notNull(), EasyMock.eq( "my test token - THREE" ) ) )
			.andReturn( "my NEW test token" );
		EasyMock.expect(mock_auth_handler.getTokenRegenerationInterval()).andReturn(
            60);

		EasyMock.replay( mock_auth_handler );


		ThreadKit.sleep( 1000 );

		setup.serverAddress( new InetSocketAddress( port ) );
		server_instance = setup.build();
		System.out.println( "Server instance recreated: " + port );
		server_instance.getLocalRegistry().bind( "server", server_impl );



		boolean reconnected = false;
		for( int i = 0; i < 40; i++ ) {
			ThreadKit.sleep( 500 );

			try {
				proxy.getMessage();
				reconnected = true;
				break;
			}
			catch ( IntrepidRuntimeException ex ) {
				// expected
				System.out.println( ex );
			}
		}

		assertTrue(reconnected);
		System.out.println( "Reconnected!!" );


		// Make sure checkConnection was called once and only once
		EasyMock.verify( mock_auth_handler );
	}
}
