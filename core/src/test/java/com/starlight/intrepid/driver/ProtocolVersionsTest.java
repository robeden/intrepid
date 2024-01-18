package com.starlight.intrepid.driver;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;


/**
 *
 */
public class ProtocolVersionsTest {
	@Test
	public void currentVersion() {
		assertEquals( OptionalInt.of( 4 ),
			ProtocolVersions.negotiateProtocolVersion( (byte) 4, (byte) 4 ) );
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 3})
	public void disallowedOldVersions(int high_version) {
		Assumptions.assumeTrue(
			System.getProperty( "intrepid.min_supported_protocol" ) == null,
			"Version checks won't work properly when the 'min_supported_protocol' " +
				"system property is set." );

		assertEquals( OptionalInt.empty(),
			ProtocolVersions.negotiateProtocolVersion( (byte) high_version, (byte) 0 ) );
	}



	@Test
	public void supportsReconnectTokens() {
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 1 ) );
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 2 ) );
		assertTrue( ProtocolVersions.supportsReconnectTokens( (byte) 3 ) );

		assertFalse( ProtocolVersions.supportsReconnectTokens( (byte) 0 ) );
	}


	@Test
	public void supportsMethodAck() {
		assertTrue( ProtocolVersions.supportsMethodAck( (byte) 2 ) );
		assertTrue( ProtocolVersions.supportsMethodAck( (byte) 3 ) );

		assertFalse( ProtocolVersions.supportsMethodAck( (byte) 0 ) );
		assertFalse( ProtocolVersions.supportsMethodAck( (byte) 1 ) );
	}


	@Test
	public void supportChannelDataRxWindow() {
		assertTrue( ProtocolVersions.supportsChannelDataRxWindow( (byte) 3 ) );
		assertTrue( ProtocolVersions.supportsChannelDataRxWindow( (byte) 4 ) );

		assertFalse( ProtocolVersions.supportsChannelDataRxWindow( (byte) 0 ) );
		assertFalse( ProtocolVersions.supportsChannelDataRxWindow( (byte) 1 ) );
		assertFalse( ProtocolVersions.supportsChannelDataRxWindow( (byte) 2 ) );
	}
}