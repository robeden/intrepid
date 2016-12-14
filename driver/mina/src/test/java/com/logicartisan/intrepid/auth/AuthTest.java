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

package com.logicartisan.intrepid.auth;

import com.logicartisan.intrepid.*;
import com.starlight.locale.UnlocalizableTextResourceKey;
import junit.framework.TestCase;
import com.logicartisan.intrepid.IntrepidSetup;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;


/**
 *
 */
public class AuthTest extends TestCase {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;

	@Override
	protected void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}

	
	public void testUserAuthentication() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create( new IntrepidSetup().vmidHint( "server" )
			.serverPort( 11751 ).authHandler( new UserTestAuthenticationHandler() ) );
		CommTest.ServerImpl original_instance =
			new CommTest.ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		// Connect to the server
		VMID server_vmid;

		try {
			client_instance.connect( InetAddress.getByName( "127.0.0.1" ), 11751, null,
				null );
			fail( "Shouldn't succeed" );
		}
		catch( IOException ex ) {
			// This is good... shouldn't allow connections without ConnectionArgs
			System.out.println( "Got exception: " + ex );
		}

		try {
			client_instance.connect( InetAddress.getByName( "127.0.0.1" ), 11751,
				new UserCredentialsConnectionArgs( "baduser", "blah".toCharArray() ),
				null );
			fail( "Shouldn't succeed" );
		}
		catch( IOException ex ) {
			// This is good... shouldn't allow connections without ConnectionArgs
		}

		try {
			client_instance.connect( InetAddress.getByName( "127.0.0.1" ), 11751,
				new UserCredentialsConnectionArgs( "reden", "badpass".toCharArray() ),
				null );
			fail( "Shouldn't succeed" );
		}
		catch( IOException ex ) {
			// This is good... shouldn't allow connections without ConnectionArgs
		}

		try {
			server_vmid = client_instance.connect(
				InetAddress.getByName( "127.0.0.1" ), 11751,
				new UserCredentialsConnectionArgs( "reden", "12345".toCharArray() ),
				null );
			assertEquals( server_instance.getLocalVMID(), server_vmid );
		}
		catch( IOException ex ) {
			ex.printStackTrace();
			fail( "Unexpected exception: " + ex );
		}
	}


	private class UserTestAuthenticationHandler implements AuthenticationHandler {
		@Override
		public UserContextInfo checkConnection( ConnectionArgs connection_args,
			SocketAddress remote_address, Object session_source )
			throws ConnectionAuthFailureException {

			if ( !( connection_args instanceof UserCredentialsConnectionArgs ) ) {
				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Bad args type: " +
					connection_args ) );
			}

			UserCredentialsConnectionArgs credentials =
				( UserCredentialsConnectionArgs ) connection_args;

			if ( !"reden".equals( credentials.getUser() ) ) {
				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Bad user" ) );
			}

			if ( !"12345".equals( new String( credentials.getPassword() ) ) ) {
				throw new ConnectionAuthFailureException(
					new UnlocalizableTextResourceKey( "Bad password" ) );
			}

			return new SimpleUserContextInfo( credentials.getUser() );
		}
	}
}
