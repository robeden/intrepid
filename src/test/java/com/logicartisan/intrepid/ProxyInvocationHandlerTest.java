package com.logicartisan.intrepid;

import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ProxyInvocationHandlerTest {
	@Test
	public void testCanThrow() throws Exception {
		Class clazz = ObjectOutput.class;
		Method method = clazz.getMethod( "writeObject", Object.class );

		assertTrue( ProxyInvocationHandler.canThrow( method, IOException.class ) );
		assertTrue( ProxyInvocationHandler.canThrow( method, InterruptedIOException.class ) );
		assertTrue( ProxyInvocationHandler.canThrow( method, NullPointerException.class ) );
		assertTrue( ProxyInvocationHandler.canThrow( method, NoClassDefFoundError.class ) );
		assertFalse( ProxyInvocationHandler.canThrow( method, InvocationTargetException.class ) );
	}
}