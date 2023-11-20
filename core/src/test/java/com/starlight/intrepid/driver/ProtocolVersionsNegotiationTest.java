package com.starlight.intrepid.driver;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;


/**
 *
 */
public class ProtocolVersionsNegotiationTest {

	public static Stream<Arguments> args() {
		return Stream.of(
			// Exact
			arguments( 0, 0, 0, 0, OptionalInt.of( 0 ) ),
			arguments( 3, 3, 3, 3, OptionalInt.of( 3 ) ),

			// Same range
			arguments( 0, 2, 0, 2, OptionalInt.of( 2 ) ),

			// Subset
			arguments( 0, 2, 0, 1, OptionalInt.of( 1 ) ),
			arguments( 0, 2, 0, 0, OptionalInt.of( 0 ) ),
			arguments( 0, 1, 0, 2, OptionalInt.of( 1 ) ),
			arguments( 0, 0, 0, 2, OptionalInt.of( 0 ) ),

			// Discontiguous
			arguments( 0, 2, 3, 3, OptionalInt.empty() ),
			arguments( 0, 2, 3, 4, OptionalInt.empty() ),
			arguments( 3, 3, 0, 2, OptionalInt.empty() ),
			arguments( 3, 4, 0, 2, OptionalInt.empty() )
		);
	}



	@ParameterizedTest
	@MethodSource("args")
	public void testNegotiation(int peer_min, int peer_pref, int our_min, int our_pref,
								OptionalInt expect) {
		assertEquals( expect,
			ProtocolVersions.negotiateProtocolVersion(
				(byte) peer_min, (byte) peer_pref,
				(byte) our_min, (byte) our_pref ) );

	}
}