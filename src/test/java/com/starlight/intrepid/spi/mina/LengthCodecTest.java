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

package com.starlight.intrepid.spi.mina;

import com.starlight.thread.ObjectSlot;
import junit.framework.TestCase;
import com.starlight.intrepid.spi.mina.IMessageDecoder;
import com.starlight.intrepid.spi.mina.IMessageEncoder;
import org.apache.mina.core.buffer.IoBuffer;

import java.util.concurrent.CountDownLatch;


/**
 * Test encoding/decoding length using the dual short method.
 */
public class LengthCodecTest extends TestCase {
	private static final Throwable NO_PROBLEMS = new Throwable( "NOTHING TO SEE HERE" );

	public void testDualShortEncoding() throws Exception {
		// This is a long test, so provide ability to skip
		if ( System.getProperty( "intrepid.test.skip.testDualShortEncoding" ) != null ) {
			return;
		}

		Thread[] threads = new Thread[ Runtime.getRuntime().availableProcessors() ];

		ObjectSlot<Throwable> error = new ObjectSlot<Throwable>();
		CountDownLatch latch = new CountDownLatch( threads.length );

		int range = Integer.MAX_VALUE / threads.length;
		int next_index = 0;
		for( int i = 0; i < threads.length; i++ ) {
			int high = next_index + range;
			if ( i == threads.length - 1 ) high = Integer.MAX_VALUE;

			threads[ i ] = new EncodeTestThread( next_index, high, latch, error );
			threads[ i ].start();
			next_index += range;
		}

		Throwable t = error.waitForValue();
		if ( t instanceof Exception ) throw ( Exception ) t;
		else if ( t instanceof Error ) throw ( Error ) t;
	}


	private class EncodeTestThread extends Thread {
		private final int min;
		private final int max;
		private final CountDownLatch latch;
		private final ObjectSlot<Throwable> error;

		EncodeTestThread( int min, int max, CountDownLatch latch,
			ObjectSlot<Throwable> error ) {

			this.min = min;
			this.max = max;
			this.latch = latch;
			this.error = error;
		}

		@Override
		public void run() {
			try {
				IoBuffer buffer = IoBuffer.allocate( 4 );
				for( int i = min; i < max; i++ ) {
					assertEquals( i, doEncodeDecode( i, buffer ) );
				}
			}
			catch( Throwable t ) {
				error.set( t );
			}
			finally {
				latch.countDown();
				if ( latch.getCount() == 0 ) error.set( NO_PROBLEMS );
			}
		}
	}


	private int doEncodeDecode( int value, IoBuffer buffer ) {
		buffer.clear();

		buffer.position( 4 );
		boolean used_int = IMessageEncoder.putDualShortLength( buffer, value, 0 );
		assertEquals( value > 0x7FFF, used_int );
		buffer.flip();
//		System.out.println( "Hex: " + buffer.getHexDump() );
		try {
			return IMessageDecoder.getDualShortLength(
				used_int ? buffer : buffer.position( 2 ) );
		}
		catch( RuntimeException ex ) {
			System.err.println( "Buffer at error: " + buffer );
			System.err.println( "Value at error: " + value );
			throw ex;
		}
	}
}
