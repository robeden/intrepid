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

import com.starlight.intrepid.auth.*;
import junit.framework.TestCase;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class ConnectionListenerTest extends TestCase {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	@Override
	protected void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}

	public void testListener() throws Exception {
		TestConnectionListener s_listener = new TestConnectionListener( "Server" );
		TestConnectionListener c_listener = new TestConnectionListener( "Client" );

		server_instance = Intrepid.create( new IntrepidSetup().vmidHint( "server" )
			.serverPort( 11751 ).openServer().connectionListener( s_listener ) );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" )
			.connectionListener( c_listener ) );

		assertTrue( s_listener.event_queue.isEmpty() );
		assertTrue( c_listener.event_queue.isEmpty() );

		final InetAddress localhost = InetAddress.getByName( "127.0.0.1" );

		// Connect to the server
		String client_attachment = "My Client Attachment";
		VMID server_vmid =
			client_instance.connect( localhost, 11751, null, client_attachment );
		assertNotNull( server_vmid );

		// Make sure both listeners show a connection
		ConnectionEventInfo info = s_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.OPENED, info.type );
		assertEquals( client_instance.getLocalVMID(), info.vmid );
		assertNull( info.attachment );
		assertNull( info.user_context );
		assertEquals( localhost, info.host );
		System.out.println( "client port: " + info.port );

		// OPENING
		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.OPENING, info.type );
		assertEquals( localhost, info.host );
		assertEquals( 11751, info.port );

		// OPENED
		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.OPENED, info.type );
		assertEquals( server_instance.getLocalVMID(), info.vmid );
		assertEquals( client_attachment, info.attachment );
		assertNull( info.user_context );
		assertEquals( localhost, info.host );
		assertEquals( 11751, info.port );

		// Make sure there are no more events
		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNull( info );
		info = s_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNull( info );

		// Disconnect from server
		client_instance.disconnect( server_vmid );

		// Make sure both listeners show the disconnect
		info = s_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.CLOSED, info.type );
		assertEquals( client_instance.getLocalVMID(), info.vmid );
		assertNull( info.attachment );
		assertNull( info.user_context );
		assertEquals( localhost, info.host );
		System.out.println( "client port: " + info.port );

		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.CLOSED, info.type );
		assertEquals( server_instance.getLocalVMID(), info.vmid );
		assertEquals( client_attachment, info.attachment );
		assertNull( info.user_context );
		assertEquals( localhost, info.host );
		assertEquals( 11751, info.port );

		// Make sure there are no more events
		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNull( info );
		info = s_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNull( info );
	}


	public void testListenerWithUserConnection() throws Exception {
		TestConnectionListener s_listener = new TestConnectionListener( "Server" );
		TestConnectionListener c_listener = new TestConnectionListener( "Client" );

		server_instance = Intrepid.create( new IntrepidSetup().vmidHint( "server" )
			.serverPort( 11751 ).connectionListener( s_listener ).authHandler(
			new AuthenticationHandler() {
				@Override
				public UserContextInfo checkConnection( ConnectionArgs connection_args,
					SocketAddress remote_address, Object session_source )
					throws ConnectionAuthFailureException {

					return new SimpleUserContextInfo( "test_user" );
				}
			} ) );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" )
			.connectionListener( c_listener ) );

		assertTrue( s_listener.event_queue.isEmpty() );
		assertTrue( c_listener.event_queue.isEmpty() );

		final InetAddress localhost = InetAddress.getByName( "127.0.0.1" );

		// Connect to the server
		String client_attachment = "My Client Attachment";
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			11751, null, client_attachment );
		assertNotNull( server_vmid );

		// Make sure both listeners show a connection

		ConnectionEventInfo info = s_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.OPENED, info.type );
		assertEquals( client_instance.getLocalVMID(), info.vmid );
		assertNull( info.attachment );
		assertNotNull( info.user_context ); // server will have user context
		assertTrue( info.user_context instanceof SimpleUserContextInfo );

		// OPENING
		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.OPENING, info.type );
		assertEquals( localhost, info.host );
		assertEquals( 11751, info.port );

		// OPENED
		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.OPENED, info.type );
		assertEquals( server_instance.getLocalVMID(), info.vmid );
		assertEquals( client_attachment, info.attachment );
		assertNull( info.user_context );    // client will not have user context

		// Make sure there are no more events
		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNull( info );
		info = s_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNull( info );

		// Disconnect from server
		client_instance.disconnect( server_vmid );

		// Make sure both listeners show the disconnect
		info = s_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.CLOSED, info.type );
		assertEquals( client_instance.getLocalVMID(), info.vmid );
		assertNull( info.attachment );
		assertNull( info.user_context );

		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNotNull( info );
		assertEquals( EventType.CLOSED, info.type );
		assertEquals( server_instance.getLocalVMID(), info.vmid );
		assertEquals( client_attachment, info.attachment );
		assertNull( info.user_context );

		// Make sure there are no more events
		info = c_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNull( info );
		info = s_listener.event_queue.poll( 2, TimeUnit.SECONDS );
		assertNull( info );
	}


	private class TestConnectionListener implements ConnectionListener {
		private final BlockingQueue<ConnectionEventInfo> event_queue =
			new LinkedBlockingQueue<ConnectionEventInfo>();

		private final String id;

		TestConnectionListener( String id ) {
			this.id = id;
		}

		@Override
		public void connectionOpened( InetAddress host, int port, Object attachment,
			VMID source_vmid, VMID vmid, UserContextInfo user_context, VMID previous_vmid,
			Object connection_type_description ) {

			System.out.println( id + " connection opened: " + vmid );
			event_queue.add( new ConnectionEventInfo( EventType.OPENED, vmid, attachment,
				false, user_context, host, port ) );
		}

		@Override
		public void connectionClosed( InetAddress host, int port, VMID source_vmid,
			VMID vmid, Object attachment, boolean will_attempt_reconnect ) {

			System.out.println( id + " connection closed: " + vmid );
			event_queue.add( new ConnectionEventInfo( EventType.CLOSED, vmid, attachment,
				will_attempt_reconnect, null, host, port ) );
		}

		@Override
		public void connectionOpenFailed( InetAddress host, int port, Object attachment,
			Exception error, boolean will_retry ) {

			System.out.println( id + " connection open failed: " + host.getHostAddress() +
				":" + port + " - " + error );
			event_queue.add( new ConnectionEventInfo( EventType.OPEN_FAILED, null,
				attachment, will_retry, null, host, port ) );
		}

		@Override
		public void connectionOpening( InetAddress host, int port, Object attachment,
			ConnectionArgs args, Object connection_type_description ) {

			System.out.println( id + " connection opening: " + host.getHostAddress() +
				":" + port );
			event_queue.add( new ConnectionEventInfo( EventType.OPENING, null,
				attachment, false, null, host, port ) );
		}
	}

	private enum EventType {
		OPENED,
		CLOSED,
		OPENING,
		OPEN_FAILED
	}

	private class ConnectionEventInfo {
		private final EventType type;
		private final VMID vmid;
		private final Object attachment;
		private final boolean will_reconnect;
		private final UserContextInfo user_context;
		private final InetAddress host;
		private final int port;

		ConnectionEventInfo( EventType type, VMID vmid, Object attachment,
			boolean will_reconnect, UserContextInfo user_context, InetAddress host,
			int port ) {

			this.type = type;
			this.vmid = vmid;
			this.attachment = attachment;
			this.will_reconnect = will_reconnect;
			this.user_context = user_context;
			this.host = host;
			this.port = port;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append( "ConnectionEventInfo" );
			sb.append( "{attachment=" ).append( attachment );
			sb.append( ", type=" ).append( type );
			sb.append( ", vmid=" ).append( vmid );
			sb.append( ", will_reconnect=" ).append( will_reconnect );
			sb.append( ", user_context=" ).append( user_context );
			sb.append( ", host=" ).append( host );
			sb.append( ", port=" ).append( port );
			sb.append( '}' );
			return sb.toString();
		}
	}
}