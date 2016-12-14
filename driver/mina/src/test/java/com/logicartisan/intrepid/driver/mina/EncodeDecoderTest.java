package com.logicartisan.intrepid.driver.mina;

import com.logicartisan.intrepid.auth.SimpleUserContextInfo;
import com.logicartisan.intrepid.auth.UserContextInfo;
import com.logicartisan.intrepid.message.IMessage;
import com.logicartisan.intrepid.message.InvokeIMessage;
import junit.framework.TestCase;
import org.apache.mina.core.buffer.IoBuffer;

import java.util.ArrayList;
import java.util.List;


/**
 *
 */
public class EncodeDecoderTest extends TestCase {
	public void testInvokeEncode() throws Exception {
		List<InvokeIMessage> test_messages = new ArrayList<InvokeIMessage>();

		test_messages.add( new InvokeIMessage( 1, 2, null, 3, null, null, false ) );
		test_messages.add( new InvokeIMessage( 1, 2, null, 3, null, null, true ) );
		test_messages.add( new InvokeIMessage( 1, 2, "Name", 3, null, null, false ) );
		test_messages.add( new InvokeIMessage( 1, 2, "Name", 3, null, null, true ) );

		test_messages.add( new InvokeIMessage( 1, 2, null, 3,
			new Object[] { "foo", "bar" }, null, false ) );
		test_messages.add( new InvokeIMessage( 1, 2, null, 3,
			new Object[] { "foo", "bar" }, null, true ) );
		test_messages.add( new InvokeIMessage( 1, 2, "Name", 3,
			new Object[] { "foo", "bar" }, null, false ) );
		test_messages.add( new InvokeIMessage( 1, 2, "Name", 3,
			new Object[] { "foo", "bar" }, null, true ) );

		UserContextInfo info = new SimpleUserContextInfo( "bob" );
		test_messages.add( new InvokeIMessage( 1, 2, null, 3, null, info, false ) );
		test_messages.add( new InvokeIMessage( 1, 2, null, 3, null, info, true ) );
		test_messages.add( new InvokeIMessage( 1, 2, "Name", 3, null, info, false ) );
		test_messages.add( new InvokeIMessage( 1, 2, "Name", 3, null, info, true ) );

		test_messages.add( new InvokeIMessage( 1, 2, null, 3,
			new Object[] { "foo", "bar" }, info, false ) );
		test_messages.add( new InvokeIMessage( 1, 2, null, 3,
			new Object[] { "foo", "bar" }, info, true ) );
		test_messages.add( new InvokeIMessage( 1, 2, "Name", 3,
			new Object[] { "foo", "bar" }, info, false ) );
		test_messages.add( new InvokeIMessage( 1, 2, "Name", 3,
			new Object[] { "foo", "bar" }, info, true ) );


		IoBuffer buffer = IoBuffer.allocate( 1024 * 100 );
		int index = -1;
		for( InvokeIMessage message : test_messages ) {
			index++;

			buffer.clear();

			IMessage new_message = null;
			try {
				IMessageEncoder.encode( message, buffer );
				// NOTE: the buffer comes out ready for writing, so don't flip
				new_message = IMessageDecoder.decode( buffer, null );
			}
			catch( Exception ex ) {
				ex.printStackTrace();
				fail( "Error on message index: " + index );
			}

			assertEquals( "Message index: " + index, message, new_message );
		}
	}
}
