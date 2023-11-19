package com.starlight.intrepid;

import com.logicartisan.common.core.IOKit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;


/**
 *
 */
public class ProxyInLocalVM {
	private Intrepid server_instance = null;


	@Before
	public void setUp() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.serverAddress( new InetSocketAddress( 11751 ) )
			.openServer()
			.build();
	}

	@After
	public void tearDown() {
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( server_instance != null ) server_instance.close();
	}

	@Test
	public void callLocalProxyWithoutSerialization() throws Exception {
		List<String> real_instance = new ArrayList<>();
		real_instance.add( "One" );

		//noinspection unchecked
		List<String> proxy =
			( List<String> ) server_instance.createProxy( real_instance );

		assertEquals( "One", proxy.get( 0 ) );
	}

	@Test
	public void callLocalProxyWithSerialization() throws Exception {
		List<String> real_instance = new ArrayList<>();
		real_instance.add( "One" );

		//noinspection unchecked
		List<String> proxy = ( List<String> )
			IOKit.deserialize(
				IOKit.serialize(
					server_instance.createProxy( real_instance ) ) );

		assertEquals( "One", proxy.get( 0 ) );
	}
}