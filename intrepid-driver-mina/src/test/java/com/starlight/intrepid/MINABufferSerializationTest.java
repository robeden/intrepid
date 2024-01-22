package com.starlight.intrepid;


import org.apache.mina.core.buffer.IoBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class MINABufferSerializationTest {
	@Test
	public void testGetObject() throws Exception {
		IoBuffer buffer = IoBuffer.allocate( 8 * 1024 );
		buffer.putObject( System.class );
		buffer.flip();

		Object obj = buffer.getObject();
		System.out.println( "Object is: " + obj );
		assertNotNull(obj);
		assertEquals(System.class, obj);
	}
}
