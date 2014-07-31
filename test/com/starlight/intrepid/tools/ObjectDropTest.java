package com.starlight.intrepid.tools;

import com.starlight.IOKit;
import com.starlight.intrepid.*;
import com.starlight.intrepid.exception.ChannelRejectedException;
import junit.framework.TestCase;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;


/**
 *
 */
public class ObjectDropTest extends TestCase {
	private static final int MAP_SIZE = 500000;

	private Intrepid server = null;
	private Intrepid client = null;


	@Override
	protected void tearDown() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client != null ) client.close();
		if ( server != null ) server.close();
	}


	public void testMemoryRequirements() {
		long max_mem = Runtime.getRuntime().maxMemory();
		assertTrue( "Max memory should be set >= 256M",
			max_mem >= 256L * 1000L * 1000L );
	}


	public void testWithoutBridgeUncompressedToCaller() throws Exception {
		// Disable inter-instance bridge
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		doBasicTest( false, true );
	}

	public void testWithBridgeUncompressedToCaller() throws Exception {
		doBasicTest( false, true );
	}

	public void testWithoutBridgeCompressedToCaller() throws Exception {
		// Disable inter-instance bridge
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		doBasicTest( true, true );
	}

	public void testWithBridgeCompressedToCaller() throws Exception {
		doBasicTest( true, true );
	}


	public void testWithoutBridgeUncompressedExplicitDestination() throws Exception {
		// Disable inter-instance bridge
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		doBasicTest( false, false );
	}

	public void testWithBridgeUncompressedExplicitDestination() throws Exception {
		doBasicTest( false, false );
	}

	public void testWithoutBridgeCompressedExplicitDestination() throws Exception {
		// Disable inter-instance bridge
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		doBasicTest( true, false );
	}

	public void testWithBridgeCompressedExplicitDestination() throws Exception {
		doBasicTest( true, false );
	}

	
	private void doBasicTest( boolean compress, boolean drop_to_caller ) throws Exception {
		server = Intrepid.create( new IntrepidSetup().openServer() );
		server.getLocalRegistry().bind( "test", new ServerImpl( server, compress ) );

		ObjectDrop client_drop = new ObjectDrop();
		client = Intrepid.create( new IntrepidSetup().channelAcceptor( client_drop ) );
		VMID server_vmid = client.connect( InetAddress.getByName( "127.0.0.1" ),
			server.getServerPort().intValue(), null, null );

		Server proxy = ( Server ) client.getRemoteRegistry( server_vmid ).lookup( "test" );

		ObjectDrop.ID<Map<Integer,String>> id = client_drop.prepareForDrop();
		System.out.println( "pre setData" );
		proxy.setData( client.getLocalVMID(), id, drop_to_caller );
		System.out.println( "post setData" );

		@SuppressWarnings( { "unchecked" } )
		Map<Integer,String> result = client_drop.retrieve( id );
		System.out.println( "retrieve complete" );
		assertNotNull( result );
		assertEquals( MAP_SIZE, result.size() );
		for( int i = 0; i < MAP_SIZE; i++ ) {
			Integer key = Integer.valueOf( i );
			assertEquals( String.valueOf( i ), result.get( key ) );
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
				map.put( Integer.valueOf( i ), String.valueOf( i ) );
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
