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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.text.NumberFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 *
 */
public class LargeTransferTest {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	@AfterEach
	public void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}

	@Test public void testLargeTransfer() throws Exception {
		byte[] data = new byte[ 1024 * 1024 * 16 ];	// 16 M
		new Random().nextBytes( data );

		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.openServer()
			.build();
		Integer server_port = server_instance.getServerPort();
		assertNotNull(server_port);
		ServerImpl original_instance = new ServerImpl();
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.newBuilder().vmidHint( "client" ).build();

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_port.intValue(), null, null );
		assertNotNull(server_vmid);

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		Server server = ( Server ) server_registry.lookup( "server" );

		double total_bps = 0;
		for ( int i = 0; i < 5; i++ ) {
			long start = System.currentTimeMillis();
			byte[] data_copy = server.send( data, start );
			long transfer_time = System.currentTimeMillis() - start;
			double bps = ( ( data.length * 2 ) / ( double ) transfer_time ) * 1000.0;
			total_bps += bps;
			System.out.println( "Method round-trip time: " + transfer_time +
				"  - " + NumberFormat.getNumberInstance().format( bps ) + " Bps" );

            assertArrayEquals(data, data_copy);
		}
		System.out.println( "Average Bps: " +
			NumberFormat.getNumberInstance().format( total_bps / 5.0 ) );
	}


	public interface Server {
		byte[] send( byte[] data, long send_time );
	}

	public class ServerImpl implements Server {
		@Override
		public byte[] send( byte[] data, long send_time ) {
			long transfer_time = System.currentTimeMillis() - send_time;

			double bps = ( data.length / ( double ) transfer_time ) * 1000.0;


			System.out.println( "Got data is length: " + data.length +
				"  - " + NumberFormat.getNumberInstance().format( bps ) + " Bps" );
			return data;
		}
	}
}
