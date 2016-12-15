package com.logicartisan.intrepid.driver;

import com.logicartisan.intrepid.OkioBufferData;
import com.logicartisan.intrepid.auth.SimpleUserContextInfo;
import com.logicartisan.intrepid.auth.UserContextInfo;
import com.logicartisan.intrepid.message.ChannelInitIMessage;
import com.logicartisan.intrepid.message.ChannelInitResponseIMessage;
import com.logicartisan.intrepid.message.IMessage;
import com.logicartisan.intrepid.message.InvokeIMessage;
import com.starlight.locale.UnlocalizableTextResourceKey;
import okio.Buffer;
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


			new ChannelInitResponseIMessage( 0 ),
			new ChannelInitResponseIMessage( 10 ),
			new ChannelInitResponseIMessage( Integer.MAX_VALUE ),
			new ChannelInitResponseIMessage( 5, null ),
			new ChannelInitResponseIMessage( 5,
				new UnlocalizableTextResourceKey( "Blah" ) ),


			new ChannelInitIMessage( 0, null, ( short ) 0 ),
			new ChannelInitIMessage( 0, null, Short.MAX_VALUE ),
			new ChannelInitIMessage( Integer.MAX_VALUE, null, ( short ) 0 ),
			new ChannelInitIMessage( Integer.MAX_VALUE, null, Short.MAX_VALUE ),
			new ChannelInitIMessage( 300, null, ( short ) 3 ),
			new ChannelInitIMessage( 300, "Hello", ( short ) 3 )
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



		MessageEncoder.encode( message, length_data, data );

		Buffer complete = new Buffer();
		length_buffer.readAll( complete );
		data_buffer.readAll( complete );

		IMessage new_message = MessageDecoder.decode( new OkioBufferData( complete ),
			( response, close ) -> {} );

		assertEquals( message, new_message );
	}
}
