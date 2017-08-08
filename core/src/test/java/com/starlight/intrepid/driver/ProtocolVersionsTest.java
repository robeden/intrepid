package com.starlight.intrepid.driver;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.*;


/**
 *
 */
public class ProtocolVersionsTest {
	@Test
	public void currentVersion() {
		assertEquals( OptionalInt.of( 3 ),
			ProtocolVersions.negotiateProtocolVersion( (byte) 3, (byte) 3 ) );
	}

	@Test
	public void disallowedOldVersions() throws Exception {
		Assume.assumeTrue(
			"Version checks won't work properly when the 'min_supported_protocol' " +
				"system property is set.",
			System.getProperty( "intrepid.min_supported_protocol" ) == null );

		assertEquals( OptionalInt.empty(),
			ProtocolVersions.negotiateProtocolVersion( (byte) 0, (byte) 0 ) );

		assertEquals( OptionalInt.empty(),
			ProtocolVersions.negotiateProtocolVersion( (byte) 1, (byte) 0 ) );

		assertEquals( OptionalInt.empty(),
			ProtocolVersions.negotiateProtocolVersion( (byte) 2, (byte) 0 ) );
	}



	@Test
	public void supportsReconnectTokens() throws Exception {
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 1 ) );
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 2 ) );
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 3 ) );

		assertFalse( ProtocolVersions.supportsReconnectTokens( (byte) 0 ) );
	}


	@Test
	public void supportsMethodAck() throws Exception {
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 2 ) );
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 3 ) );

		assertFalse( ProtocolVersions.supportsReconnectTokens( (byte) 0 ) );
		assertFalse( ProtocolVersions.supportsReconnectTokens( (byte) 1 ) );
	}


	@Test
	public void supportChannelDataRxWindow() throws Exception {
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 3 ) );
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 4 ) );

		assertFalse( ProtocolVersions.supportsReconnectTokens( (byte) 0 ) );
		assertFalse( ProtocolVersions.supportsReconnectTokens( (byte) 1 ) );
		assertFalse( ProtocolVersions.supportsReconnectTokens( (byte) 2 ) );
	}
}