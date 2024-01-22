package com.starlight.intrepid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 *
 */
public class SmallTransferTest {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	@AfterEach
	public void tearDown() throws Exception {
		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}


	@Test
	public void testStreamingUnidirectional() throws Exception {
		BlockingQueue<Long> read_times = new LinkedBlockingQueue<>();

		ChannelAcceptor acceptor = ( channel, source_vmid, attachment ) -> {
			new Thread( () -> {
				try {
					try( DataInputStream in = new DataInputStream( Channels.newInputStream( channel ) ) ) {
						while( true ) {
							long time = in.readLong();
							read_times.add( time );
						}
					}
					catch( EOFException ex ) {
						// ignore
					}
				}
				catch ( IOException e ) {
					e.printStackTrace();
				}
			} ).start();
		};






		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.openServer()
			.channelAcceptor( acceptor )
			.build();
		Integer server_port = server_instance.getServerPort();
		assertNotNull( server_port );

		client_instance = Intrepid.newBuilder().vmidHint( "client" ).build();

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_port.intValue(), null, null );
		assertNotNull( server_vmid );

		List<Long> written_times = new ArrayList<>();
		try( ByteChannel channel = client_instance.createChannel( server_vmid, null );
			DataOutputStream out = new DataOutputStream( Channels.newOutputStream( channel ) ) ) {

			for( int i = 0; i < 100; i++ ) {
				if ( i != 0 ) Thread.sleep( 100 );

				long time = System.nanoTime();

				out.writeLong( time );
				out.flush();

				written_times.add( time );
			}
		}

		assertEquals( 100, written_times.size() );

		System.out.println( "Written: " + written_times );
		System.out.println( "Read: " + read_times );

		while( !written_times.isEmpty() ) {
			Long written = written_times.remove( 0 );
			Long read = read_times.poll( 1, TimeUnit.SECONDS );
			System.out.println( written + "  -->  " + read );

			assertEquals( written, read );
		}
	}
}
