package com.starlight.intrepid.spi;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

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
			new Args( 0, 0, 0, 0,     0 ),
			new Args( 3, 3, 3, 3,     3 ),

			// Same range
			new Args( 0, 2, 0, 2,     2 ),

			// Subset
			new Args( 0, 2, 0, 1,     1 ),
			new Args( 0, 2, 0, 0,     0 ),
			new Args( 0, 1, 0, 2,     1 ),
			new Args( 0, 0, 0, 2,     0 ),

			// Discontiguous
			new Args( 0, 2, 3, 3,     -1 ),
			new Args( 0, 2, 3, 4,     -1 ),
			new Args( 3, 3, 0, 2,     -1 ),
			new Args( 3, 4, 0, 2,     -1 )
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
		final int expect;



		Args( int peer_min, int peer_pref, int our_min, int our_pref,
			int expect ) {

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