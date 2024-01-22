package com.starlight.intrepid;

import com.logicartisan.common.core.IOKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 *
 */
public class ProxyInLocalVM {
	private Intrepid server_instance = null;


	@BeforeEach
	public void setUp() throws Exception {
		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.serverAddress( new InetSocketAddress( 11751 ) )
			.openServer()
			.build();
	}

	@AfterEach
	public void tearDown() {
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