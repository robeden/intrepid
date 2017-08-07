// Copyright (c) 2010 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Intrepid nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.starlight.intrepid.driver;

import com.starlight.intrepid.OkioBufferData;
import okio.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;


/**
 * Test encoding/decoding length using the dual short method.
 */
@RunWith( Parameterized.class )
public class LengthCodecTest {
	@Parameterized.Parameters
	public static TestArgs[] values() {
		return new TestArgs[] {
			new TestArgs( 0x7FFE, false ),
			new TestArgs( 0x7FFF, false ),
			new TestArgs( 0x8000, true ),
			new TestArgs( 0x8001, true ),
			new TestArgs( Integer.MAX_VALUE, true ),
			new TestArgs( Integer.MAX_VALUE - 1, true ),
			new TestArgs( 1000000000, true ),
			new TestArgs( 100000000, true ),
			new TestArgs( 10000000, true ),
			new TestArgs( 1000000, true ),
			new TestArgs( 100000, true ),
			new TestArgs( 10000, false ),
			new TestArgs( 1000, false ),
			new TestArgs( 100, false ),
			new TestArgs( 10, false ),
			new TestArgs( 9, false ),
			new TestArgs( 8, false ),
			new TestArgs( 7, false ),
			new TestArgs( 6, false ),
			new TestArgs( 5, false ),
			new TestArgs( 4, false ),
			new TestArgs( 3, false ),
			new TestArgs( 2, false ),
			new TestArgs( 1, false ),
			new TestArgs( 0, false )
		};
	}


	private final TestArgs args;

	public LengthCodecTest( TestArgs args ) {
		this.args = args;
	}


	@Test
	public void testDualShortEncoding() throws Exception {
		Buffer buffer = new Buffer();

		OkioBufferData data = new OkioBufferData( buffer );


		boolean used_full_int = MessageEncoder.putDualShortLength( data, args.value );
		assertEquals( args.expect_full_int, used_full_int );


		int value = MessageDecoder.getDualShortLength( data );

		assertEquals( value, args.value );
	}


	private static class TestArgs {
		private final int value;
		private final boolean expect_full_int;

		TestArgs( int value, boolean expect_full_int ) {
			this.value = value;
			this.expect_full_int = expect_full_int;
		}
	}
}
