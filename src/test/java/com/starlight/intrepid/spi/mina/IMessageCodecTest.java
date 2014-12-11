package com.starlight.intrepid.spi.mina;

import com.starlight.intrepid.message.ChannelInitIMessage;
import com.starlight.intrepid.message.ChannelInitResponseIMessage;
import com.starlight.locale.UnlocalizableTextResourceKey;
import junit.framework.TestCase;
import com.starlight.intrepid.spi.mina.IMessageDecoder;
import com.starlight.intrepid.spi.mina.IMessageEncoder;
import org.apache.mina.core.buffer.IoBuffer;

import java.util.ArrayList;
import java.util.List;


/**
 *
 */
public class IMessageCodecTest extends TestCase {
	public void testChannelInitResponseMessage() throws Exception {
		IoBuffer buffer = IoBuffer.allocate( 10240 );
		buffer.setAutoExpand( true );

		List<ChannelInitResponseIMessage> test_cases = 
			new ArrayList<ChannelInitResponseIMessage>();
		
		test_cases.add( new ChannelInitResponseIMessage( 0 ) );
		test_cases.add( new ChannelInitResponseIMessage( 10 ) );
		test_cases.add( new ChannelInitResponseIMessage( Integer.MAX_VALUE ) );
		test_cases.add( new ChannelInitResponseIMessage( 5, null ) );
		test_cases.add( new ChannelInitResponseIMessage( 5,
			new UnlocalizableTextResourceKey( "Blah" ) ) );

		for( ChannelInitResponseIMessage message : test_cases ) {
			buffer.clear();

			IMessageEncoder.encodeChannelInitResponse( message, buffer );
			buffer.flip();
			
			ChannelInitResponseIMessage cloned_message =
				IMessageDecoder.decodeChannelInitResponse( buffer );
			
			assertEquals( message, cloned_message );
		}
	}
	
	public void testChannelInitMessage() throws Exception {
		IoBuffer buffer = IoBuffer.allocate( 10240 );
		buffer.setAutoExpand( true );

		List<ChannelInitIMessage> test_cases = 
			new ArrayList<ChannelInitIMessage>();
		
		test_cases.add( new ChannelInitIMessage( 0, null, ( short ) 0 ) );
		test_cases.add( new ChannelInitIMessage( 0, null, Short.MAX_VALUE ) );
		test_cases.add( new ChannelInitIMessage( Integer.MAX_VALUE, null, ( short ) 0 ) );
		test_cases.add( new ChannelInitIMessage( Integer.MAX_VALUE, null, Short.MAX_VALUE ) );
		test_cases.add( new ChannelInitIMessage( 300, null, ( short ) 3 ) );
		test_cases.add( new ChannelInitIMessage( 300, "Hello", ( short ) 3 ) );

		for( ChannelInitIMessage message : test_cases ) {
			buffer.clear();

			IMessageEncoder.encodeChannelInit( message, buffer );
			buffer.flip();
			
			ChannelInitIMessage cloned_message = 
				IMessageDecoder.decodeChannelInit( buffer, null );
			
			assertEquals( message, cloned_message );
		}
	}
}
