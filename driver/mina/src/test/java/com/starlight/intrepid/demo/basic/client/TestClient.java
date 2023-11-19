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

package com.starlight.intrepid.demo.basic.client;

import com.logicartisan.common.core.thread.ThreadKit;
import com.starlight.intrepid.*;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.demo.basic.ClientInterface;
import com.starlight.intrepid.demo.basic.ServerInterface;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class TestClient implements ClientInterface {
	@Override
	public String getMessage( String message ) {
		System.out.print( message );
		return "Hello from the client!";
	}


	public static void main( String[] args ) throws Exception {
		if ( args.length < 1 ) {
			printUsage();
			System.exit( -1 );
		}

		String host = args[ 0 ];
		int port = 11751;
		if ( args.length > 1 ) {
			port = Integer.parseInt( args[ 1 ] );
		}

		Intrepid intrepid = Intrepid.newBuilder().build();	// init as client
		intrepid.addPerformanceListener( new PerformanceListener() {
			@Override
			public void inboundRemoteCallCompleted(VMID instance_vmid, long time,
												   int call_id, Object result, boolean result_was_thrown ) {

				System.out.println( "INBOUND call completed: " + call_id );
			}

			@Override
			public void remoteCallStarted( VMID instance_vmid, long time, int call_id,
				VMID destination_vmid, int object_id, int method_id, Method method,
				Object[] args, UserContextInfo user_context, String persistent_name ) {

				System.out.println( "call started: " + call_id + " - " + method );
			}

			@Override
			public void remoteCallCompleted( VMID instance_vmid, long time, int call_id,
				Object result, boolean result_was_thrown, Long server_time ) {

				System.out.println( "call completed: " + call_id );
			}

			@Override
			public void inboundRemoteCallStarted( VMID instance_vmid, long time,
				int call_id, VMID source_vmid, int object_id, int method_id,
				Method method, Object[] args, UserContextInfo user_context,
				String persistent_name ) {

				System.out.println( "INBOUND call started: " + call_id );
			}
		} );
		intrepid.addConnectionListener( new ConnectionListener() {
			@Override
			public void connectionOpened( @Nonnull SocketAddress socket_address,
				Object attachment, @Nonnull VMID source_vmid, @Nonnull VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				@Nonnull Object connection_type_description, byte ack_rate_sec ) {

				System.out.println( ">>> Connection OPEN: " + vmid + " (" + attachment +
					")" );
			}

			@Override
			public void connectionClosed( @Nonnull SocketAddress socket_address,
				@Nonnull VMID source_vmid, @Nullable VMID vmid,
				@Nullable Object attachment, boolean will_attempt_reconnect,
				@Nullable UserContextInfo user_context ) {

				System.out.println( ">>> Connection CLOSE: " + vmid + " (" + attachment +
					") - " + ( will_attempt_reconnect ? " WILL reconnect" :
					"will NOT reconnect" ) );
			}

			@Override
			public void connectionOpenFailed( @Nonnull SocketAddress socket_address,
				Object attachment, Exception error, boolean will_retry ) {}

			@Override
			public void connectionOpening( @Nonnull SocketAddress socket_address,
				Object attachment, ConnectionArgs args,
				@Nonnull Object connection_type_description ) {}
		} );

		System.out.print( "Connecting..." );
		VMID vmid = intrepid.tryConnect( InetAddress.getByName( host ), port, null, null, 
			30, TimeUnit.SECONDS );
		System.out.println( "done: " + vmid );

		System.out.print( "Getting remote registry..." );
		Registry registry = intrepid.getRemoteRegistry( vmid );
		System.out.println( "done: " + registry );

		System.out.print( "Looking up TestServer..." );
		ServerInterface server =
			( ServerInterface ) registry.lookup( ServerInterface.class.getName() );
		System.out.println( "done: " + server );

		System.out.println();

		System.out.println( "Server message is: " + server.getMessage() );

		System.out.print( "Sending callback to server... \"" );
		server.setClient( ( ClientInterface ) intrepid.createProxy( new TestClient() ) );
//		server.setClient( new TestClient() );
		System.out.println( "\"... done." );


		System.out.println(
			"Now we'll loop forever to test reconnection and persistent names:" );
		while( true ) {
			try {
				server.getMessage();
				System.out.println( "." );
			}
			catch( Exception ex ) {
				System.out.println( "Error: " + ex );
				if ( ex instanceof ClassCastException ) {
					ex.printStackTrace();
				}
			}

			ThreadKit.sleep( 2000 );
		}
	}


	private static void printUsage() {
		System.out.println( "Usage: TestClient <host> [<port>]" );
	}
}
