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

package com.starlight.intrepid.demo.basic.server;

import com.logicartisan.common.core.thread.ThreadKit;
import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.IntrepidSetup;
import com.starlight.intrepid.PerformanceListener;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.demo.basic.ClientInterface;
import com.starlight.intrepid.demo.basic.ServerInterface;

import java.lang.reflect.Method;


/**
 *
 */
public class TestServer implements ServerInterface {

	private TestServer() {}

	@Override
	public String getMessage() {
		return "Hello from the server!";
	}

	@Override
	public void setClient( ClientInterface client ) {
		System.out.println( "Client was set. Message is: " +
			client.getMessage( "This is a callback from the server" ) );
		System.out.println( "Client is: " + client );
	}


	public static void main( String[] args ) throws Exception {
		int port = 11751;
		if ( args.length > 0 ) {
			port = Integer.parseInt( args[ 0 ] );
		}

		System.out.print( "Initializing..." );
		Intrepid intrepid = Intrepid.create(
			new IntrepidSetup().serverPort( port ).openServer() );
		intrepid.addPerformanceListener( new PerformanceListener() {
			@Override
			public void inboundRemoteCallCompleted( VMID instance_vmid, long time,
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
		System.out.println( "done." );

		System.out.print( "Binding to registry..." );
		intrepid.getLocalRegistry().bind( ServerInterface.class.getName(), new TestServer() );
		System.out.println( "done." );

		System.out.println( "Server ready!" );

		new Thread() {
			@Override
			public void run() {
				while( true ) {
					ThreadKit.sleep( 10000 );
					System.gc();
				}
			}
		}.start();
	}
}
