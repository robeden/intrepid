package com.logicartisan.intrepid;


import junit.framework.TestCase;
import org.apache.mina.core.buffer.IoBuffer;


public class MINABufferSerializationTest extends TestCase {
	public void testGetObject() throws Exception {
		IoBuffer buffer = IoBuffer.allocate( 8 * 1024 );
		buffer.putObject( System.class );
		buffer.flip();

		Object obj = buffer.getObject();
		System.out.println( "Object is: " + obj );
		assertNotNull( obj );
		assertEquals( System.class, obj );
	}
}
