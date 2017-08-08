package com.starlight.intrepid.driver;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;


/**
 *
 */
@RunWith( Parameterized.class )
public class ProtocolVersionsNegotiationTest {

	@Parameterized.Parameters( name = "{0}" )
	public static List<Args> args() {
		return Arrays.asList(
			// Exact
			new Args( 0, 0, 0, 0, OptionalInt.of( 0 ) ),
			new Args( 3, 3, 3, 3, OptionalInt.of( 3 ) ),

			// Same range
			new Args( 0, 2, 0, 2, OptionalInt.of( 2 ) ),

			// Subset
			new Args( 0, 2, 0, 1, OptionalInt.of( 1 ) ),
			new Args( 0, 2, 0, 0, OptionalInt.of( 0 ) ),
			new Args( 0, 1, 0, 2, OptionalInt.of( 1 ) ),
			new Args( 0, 0, 0, 2, OptionalInt.of( 0 ) ),

			// Discontiguous
			new Args( 0, 2, 3, 3, OptionalInt.empty() ),
			new Args( 0, 2, 3, 4, OptionalInt.empty() ),
			new Args( 3, 3, 0, 2, OptionalInt.empty() ),
			new Args( 3, 4, 0, 2, OptionalInt.empty() )
		);
	}


	private final Args args;

	public ProtocolVersionsNegotiationTest( Args args ) {
		this.args = args;
	}


	@Test
	public void testNegotiation() {
		assertEquals( args.expect,
			ProtocolVersions.negotiateProtocolVersion(
				(byte) args.peer_min, (byte) args.peer_pref,
				(byte) args.our_min, (byte) args.our_pref ) );

	}


	@SuppressWarnings( "OptionalUsedAsFieldOrParameterType" )
	private static class Args {
		final int peer_min, peer_pref;
		final int our_min, our_pref;
		final OptionalInt expect;



		public Args( int peer_min, int peer_pref, int our_min, int our_pref,
			OptionalInt expect ) {

			this.peer_min = peer_min;
			this.peer_pref = peer_pref;
			this.our_min = our_min;
			this.our_pref = our_pref;
			this.expect = expect;
		}



		@Override
		public String toString() {
			return peer_min + "-" + peer_pref + " => " + our_min + "-" + our_pref;
		}
	}
}