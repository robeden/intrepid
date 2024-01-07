package com.starlight.intrepid.tools;

import com.logicartisan.common.core.IOKit;
import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.IntrepidTesting;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.exception.ChannelRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


/**
 *
 */
public class ObjectDropTest {
	private static final int MAP_SIZE = 500000;

	private Intrepid server = null;
	private Intrepid client = null;


	@AfterEach
	public void tearDown() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client != null ) client.close();
		if ( server != null ) server.close();
	}


	@Test
	public void testMemoryRequirements() {
		long max_mem = Runtime.getRuntime().maxMemory();
		assertTrue(max_mem >= 256L * 1000L * 1000L, "Max memory should be set >= 256M");
	}


	@Test
	public void testWithoutBridgeUncompressedToCaller() throws Exception {
		// Disable inter-instance bridge
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		doBasicTest( false, true );
	}

	@Test
	public void testWithBridgeUncompressedToCaller() throws Exception {
		doBasicTest( false, true );
	}

	@Test
	public void testWithoutBridgeCompressedToCaller() throws Exception {
		// Disable inter-instance bridge
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		doBasicTest( true, true );
	}

	@Test
	public void testWithBridgeCompressedToCaller() throws Exception {
		doBasicTest( true, true );
	}


	@Test
	public void testWithoutBridgeUncompressedExplicitDestination() throws Exception {
		// Disable inter-instance bridge
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		doBasicTest( false, false );
	}

	@Test
	public void testWithBridgeUncompressedExplicitDestination() throws Exception {
		doBasicTest( false, false );
	}

	@Test
	public void testWithoutBridgeCompressedExplicitDestination() throws Exception {
		// Disable inter-instance bridge
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		doBasicTest( true, false );
	}

	@Test
	public void testWithBridgeCompressedExplicitDestination() throws Exception {
		doBasicTest( true, false );
	}

	
	private void doBasicTest( boolean compress, boolean drop_to_caller ) throws Exception {
		server = Intrepid.newBuilder().openServer().build();
		server.getLocalRegistry().bind( "lib/test", new ServerImpl( server, compress ) );

		ObjectDrop client_drop = new ObjectDrop();
		client = Intrepid.newBuilder().channelAcceptor( client_drop ).build();
		VMID server_vmid = client.connect( InetAddress.getByName( "127.0.0.1" ),
			server.getServerPort().intValue(), null, null );

		Server proxy = ( Server ) client.getRemoteRegistry( server_vmid ).lookup(
			"lib/test" );

		ObjectDrop.ID<Map<Integer,String>> id = client_drop.prepareForDrop();
		System.out.println( "pre setData" );
		proxy.setData( client.getLocalVMID(), id, drop_to_caller );
		System.out.println( "post setData" );

		Map<Integer,String> result = client_drop.retrieve( id );
		System.out.println( "retrieve complete" );
		assertNotNull(result);
		assertEquals(MAP_SIZE, result.size());
		for( int i = 0; i < MAP_SIZE; i++ ) {
			Integer key = i;
			assertEquals(String.valueOf( i ), result.get( key ));
		}
	}

	public static interface Server {
		public void setData( VMID destination, ObjectDrop.ID<Map<Integer,String>> drop_id,
			boolean drop_to_caller ) throws IOException;
	}

	private static class ServerImpl implements Server {
		private final Intrepid instance;
		private final boolean compress;

		ServerImpl( Intrepid instance, boolean compress ) {
			this.instance = instance;
			this.compress = compress;
		}


		@Override
		public void setData( VMID destination, ObjectDrop.ID<Map<Integer,String>> drop_id,
			boolean drop_to_caller ) throws IOException {

			Map<Integer,String> map = new HashMap<Integer, String>();
			for( int i = 0; i < MAP_SIZE; i++ ) {
				map.put(i, String.valueOf( i ) );
			}

			int size = IOKit.serialize( map ).length;

			try {
				System.out.println( "starting server drop: " + size );
				long start = System.currentTimeMillis();

				if ( drop_to_caller ) {
					ObjectDrop.dropToCaller( drop_id, map, compress );
				}
				else {
					ObjectDrop.drop( destination, drop_id, instance,
						map, compress );
				}
				System.out.println( "finished with server drop: " +
					( System.currentTimeMillis() - start ) );
			}
			catch( ChannelRejectedException ex ) {
				throw new IOException( ex );
			}
		}
	}
}
