package com.starlight.intrepid.driver.netty;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 *
 */
public class MINAIMessageEncoderTest {
	@Test
	public void positionSlicedBuffer() throws Exception {
		byte[] data = "abcdefghijklmnopqrstuvwxyz".getBytes();

		IoBuffer buffer = IoBuffer.allocate( 5 );
		buffer.setAutoExpand( true );                   // tests auto-expansion


		IoBuffer length = IoBuffer.allocate( 4 );



		for( int i = 1; i <= 4; i++ ) {
			buffer.clear();
			buffer.position( 4 );
			length.clear();


			for( int j = 0; j < i; j++ ) {
				length.put( data[ j ] );
			}
			buffer.put( data, i, data.length - i );

			System.out.println( "i: " + i );
			System.out.println( "Before length: " + length );
			System.out.println( "Before buffer: " + buffer );

			MINAIMessageEncoder.prependLength( buffer, length );

			System.out.println( "Input: " + IoBuffer.wrap( data ).getHexDump() );
			System.out.println( "Buffer: " + buffer.getHexDump() );

			assertEquals( data.length, buffer.remaining() );
			byte[] copy = new byte[ buffer.remaining() ];
			buffer.get( copy );
			assertArrayEquals( data, copy );
		}
	}

}