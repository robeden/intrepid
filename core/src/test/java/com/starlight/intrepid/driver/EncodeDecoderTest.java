package com.starlight.intrepid.driver;

import com.starlight.intrepid.OkioBufferData;
import com.starlight.intrepid.auth.SimpleUserContextInfo;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.message.*;
import okio.Buffer;
import okio.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;


/**
 *
 */
@RunWith( Parameterized.class )
public class EncodeDecoderTest {
	@Parameterized.Parameters
	public static List<IMessage> testMessages() {
		UserContextInfo info = new SimpleUserContextInfo( "bob" );
		return Arrays.asList(
			new InvokeIMessage( 1, 2, null, 3, null, null, false ),
			new InvokeIMessage( 1, 2, null, 3, null, null, true ),
			new InvokeIMessage( 1, 2, "Name", 3, null, null, false ),
			new InvokeIMessage( 1, 2, "Name", 3, null, null, true ),

			new InvokeIMessage( 1, 2, null, 3,
				new Object[] { "foo", "bar" }, null, false ),
			new InvokeIMessage( 1, 2, null, 3,
				new Object[] { "foo", "bar" }, null, true ),
			new InvokeIMessage( 1, 2, "Name", 3,
				new Object[] { "foo", "bar" }, null, false ),
			new InvokeIMessage( 1, 2, "Name", 3,
				new Object[] { "foo", "bar" }, null, true ),

			new InvokeIMessage( 1, 2, null, 3, null, info, false ),
			new InvokeIMessage( 1, 2, null, 3, null, info, true ),
			new InvokeIMessage( 1, 2, "Name", 3, null, info, false ),
			new InvokeIMessage( 1, 2, "Name", 3, null, info, true ),

			new InvokeIMessage( 1, 2, null, 3,
				new Object[] { "foo", "bar" }, info, false ),
			new InvokeIMessage( 1, 2, null, 3,
				new Object[] { "foo", "bar" }, info, true ),
			new InvokeIMessage( 1, 2, "Name", 3,
				new Object[] { "foo", "bar" }, info, false ),
			new InvokeIMessage( 1, 2, "Name", 3,
				new Object[] { "foo", "bar" }, info, true ),


			new ChannelInitResponseIMessage( 0, 0 ),
			new ChannelInitResponseIMessage( 10, Integer.MAX_VALUE ),
			new ChannelInitResponseIMessage( Integer.MAX_VALUE, 9000 ),
			new ChannelInitResponseIMessage( 5, null ),
			new ChannelInitResponseIMessage( 5, "Blah" ),


			new ChannelInitIMessage( 0, null, ( short ) 0, 0 ),
			new ChannelInitIMessage( 0, null, Short.MAX_VALUE, Integer.MAX_VALUE ),
			new ChannelInitIMessage( Integer.MAX_VALUE, null, ( short ) 0, 9000 ),
			new ChannelInitIMessage( Integer.MAX_VALUE, null, Short.MAX_VALUE, 12345 ),
			new ChannelInitIMessage( 300, null, ( short ) 3, 987654321 ),
			new ChannelInitIMessage( 300, "Hello", ( short ) 3, 10 ),


			new ChannelDataAckIMessage( ( short ) 0, ( short ) 0, 0 ),
			new ChannelDataAckIMessage( Short.MAX_VALUE, Short.MAX_VALUE,
				Integer.MAX_VALUE ),
			new ChannelDataAckIMessage( Short.MIN_VALUE, Short.MIN_VALUE, 9000 ),


			ChannelDataIMessage.create( ( short ) 0, ( short ) 0,
				ByteBuffer.wrap( ByteString.decodeHex( "123456789ABC" ).toByteArray() ) ),
			ChannelDataIMessage.create( Short.MAX_VALUE, Short.MAX_VALUE,
				ByteBuffer.wrap( ByteString.decodeHex( "123456789ABC" ).toByteArray() ) ),
			ChannelDataIMessage.create( Short.MIN_VALUE, Short.MIN_VALUE,
				ByteBuffer.wrap( ByteString.decodeHex( "123456789ABC" ).toByteArray() ) )
		);
	}


	private final IMessage message;

	public EncodeDecoderTest( IMessage message ) {
		this.message = message;
	}



	@Test
	public void testInvokeEncode() throws Exception {
		Buffer length_buffer = new Buffer();
		Buffer data_buffer = new Buffer();

		OkioBufferData length_data = new OkioBufferData( length_buffer );
		OkioBufferData data = new OkioBufferData( data_buffer );



		MessageEncoder.encode( message, ProtocolVersions.PROTOCOL_VERSION,
			length_data, data );

		Buffer complete = new Buffer();
		length_buffer.readAll( complete );
		data_buffer.readAll( complete );

		IMessage new_message = MessageDecoder.decode( new OkioBufferData( complete ),
			ProtocolVersions.PROTOCOL_VERSION, ( response, close ) -> {},
			// NOTE: Not currently testing any SessionInit/Response message so shouldn't
			//       be needed...
			( uuid, s ) -> {
				throw new AssertionError( "Shouldn't be called" );
			} );

		assertEquals( message, new_message );
	}
}
