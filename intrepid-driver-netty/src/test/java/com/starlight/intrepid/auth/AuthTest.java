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

package com.starlight.intrepid.auth;

import com.starlight.intrepid.CommTest;
import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.exception.ConnectionFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 *
 */
public class AuthTest {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;

	@AfterEach
	public void tearDown() throws Exception {
		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}

	@BeforeEach
	public void setUp() throws Exception {
		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.serverAddress( new InetSocketAddress( 0 ) )
			.authHandler(new UserTestAuthenticationHandler())
			.build();
		CommTest.ServerImpl original_instance =
			new CommTest.ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.newBuilder().vmidHint( "client" ).build();
	}


	@Test
	public void testUserAuth_noCredentials() {
		assertThrows(ConnectionFailureException.class,
			() -> client_instance.connect(InetAddress.getLoopbackAddress(),
				server_instance.getServerPort(), null, null));
	}

	@Test
	public void testUserAuth_badUser() throws Exception {
		assertThrows(ConnectionFailureException.class,
			() -> client_instance.connect( InetAddress.getLoopbackAddress(),
				server_instance.getServerPort(),
				new UserCredentialsConnectionArgs( "baduser", "blah".toCharArray() ), null ));
	}

	@Test
	public void testUserAuth_badPassword() throws Exception {
		assertThrows(ConnectionFailureException.class,
			() -> client_instance.connect( InetAddress.getLoopbackAddress(),
				server_instance.getServerPort(),
				new UserCredentialsConnectionArgs( "reden", "badpass".toCharArray() ), null ));
	}

	@Test
	public void testUserAuth_succeed() throws Exception {
		VMID server_vmid = client_instance.connect(
			InetAddress.getLoopbackAddress(), server_instance.getServerPort(),
			new UserCredentialsConnectionArgs( "reden", "12345".toCharArray() ),
			null );
		assertEquals( server_instance.getLocalVMID(), server_vmid );
	}


	private static class UserTestAuthenticationHandler implements AuthenticationHandler {
		@Override
		public UserContextInfo checkConnection( ConnectionArgs connection_args,
			SocketAddress remote_address, Object session_source )
			throws ConnectionAuthFailureException {

			if ( !( connection_args instanceof UserCredentialsConnectionArgs ) ) {
				throw new ConnectionAuthFailureException(
					"Bad args type: " + connection_args );
			}

			UserCredentialsConnectionArgs credentials =
				( UserCredentialsConnectionArgs ) connection_args;

			if ( !"reden".equals( credentials.getUser() ) ) {
				throw new ConnectionAuthFailureException( "Bad user" );
			}

			if ( !"12345".equals( new String( credentials.getPassword() ) ) ) {
				throw new ConnectionAuthFailureException( "Bad password" );
			}

			return new SimpleUserContextInfo( credentials.getUser() );
		}
	}
}
