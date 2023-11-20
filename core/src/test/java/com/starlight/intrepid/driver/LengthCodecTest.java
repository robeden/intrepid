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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;


/**
 * Test encoding/decoding length using the dual short method.
 */
public class LengthCodecTest {
	public static Stream<Arguments> values() {
		return Stream.of(
			arguments( 0x7FFE, false ),
			arguments( 0x7FFF, false ),
			arguments( 0x8000, true ),
			arguments( 0x8001, true ),
			arguments( Integer.MAX_VALUE, true ),
			arguments( Integer.MAX_VALUE - 1, true ),
			arguments( 1000000000, true ),
			arguments( 100000000, true ),
			arguments( 10000000, true ),
			arguments( 1000000, true ),
			arguments( 100000, true ),
			arguments( 10000, false ),
			arguments( 1000, false ),
			arguments( 100, false ),
			arguments( 10, false ),
			arguments( 9, false ),
			arguments( 8, false ),
			arguments( 7, false ),
			arguments( 6, false ),
			arguments( 5, false ),
			arguments( 4, false ),
			arguments( 3, false ),
			arguments( 2, false ),
			arguments( 1, false ),
			arguments( 0, false )
		);
	}


	@ParameterizedTest
	@MethodSource("values")
	public void testDualShortEncoding(int expect_value, boolean expect_full_int) {
		Buffer buffer = new Buffer();

		OkioBufferData data = new OkioBufferData( buffer );


		boolean used_full_int = MessageEncoder.putDualShortLength( data, expect_value );
		assertEquals( expect_full_int, used_full_int );


		int value = MessageDecoder.getDualShortLength( data );

		assertEquals( value, value );
	}
}
