/*
 * Copyright (C) 2017 by Forcepoint Federal LLC
 * All rights reserved
 */

package com.starlight.intrepid.driver;

import com.starlight.intrepid.OkioBufferData;
import com.starlight.intrepid.message.ChannelDataAckIMessage;
import okio.Buffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 *
 */
public class ChannelDataAckMessageCodecTest {
	@Test
	public void testNegativeWindowSize() throws Exception {
		ChannelDataAckIMessage msg =
			new ChannelDataAckIMessage( ( short ) 0x01, ( short ) 0x02, -1 );

		ChannelDataAckIMessage copy = encodeDecode( msg );

		assertEquals( msg, copy );
	}

	@Test
	public void testPositiveWindowSize_FFFF() throws Exception {
		ChannelDataAckIMessage msg =
			new ChannelDataAckIMessage( ( short ) 0x01, ( short ) 0x02, 0xFFFF );

		ChannelDataAckIMessage copy = encodeDecode( msg );

		assertEquals( msg, copy );
	}

	@Test
	public void testPositiveWindowSize_1() throws Exception {
		ChannelDataAckIMessage msg =
			new ChannelDataAckIMessage( ( short ) 0x01, ( short ) 0x02, 1 );

		ChannelDataAckIMessage copy = encodeDecode( msg );

		assertEquals( msg, copy );
	}

	@Test
	public void testPositiveWindowSize_10_000_000() throws Exception {
		ChannelDataAckIMessage msg =
			new ChannelDataAckIMessage( ( short ) 0x01, ( short ) 0x02, 10_000_000 );

		ChannelDataAckIMessage copy = encodeDecode( msg );

		assertEquals( msg, copy );
	}


	private ChannelDataAckIMessage encodeDecode( ChannelDataAckIMessage original )
		throws Exception {

		Buffer length_buffer = new Buffer();
		Buffer data_buffer = new Buffer();
		MessageEncoder.encode( original, ( byte ) 3, new OkioBufferData( length_buffer ),
			new OkioBufferData( data_buffer ) );


		Buffer data = new Buffer();
		data.write( length_buffer, length_buffer.size() );
		data.write( data_buffer, data_buffer.size() );
		return ( ChannelDataAckIMessage ) MessageDecoder.decode(
				new OkioBufferData( data ),
				Byte.valueOf( ( byte ) 3 ), ( message, close ) -> {},
				( __, ___ ) -> { throw new AssertionError( "Don't call me" ); } );
	}
}