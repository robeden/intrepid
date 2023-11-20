package com.starlight.intrepid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test serialization of various types of data
 */
public class CallArgSerializationTest {

	private Intrepid client_instance = null;
	private Intrepid server_instance = null;

	private CopyServer copy_server = null;


	@AfterEach
	public void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}


	@BeforeEach
	public void setUp() throws Exception {
		server_instance = Intrepid.newBuilder().openServer().build();
		server_instance.getLocalRegistry().bind( "copy", new BasicCopyServer() );

		client_instance = Intrepid.newBuilder().build();
		client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_instance.getServerPort().intValue(), null, null );
		copy_server = ( CopyServer ) client_instance.getRemoteRegistry(
			server_instance.getLocalVMID() ).lookup( "copy" );
	}


	@Test
	public void testPrimitives() {
		// boolean
        assertFalse(copy_server.copy(false));
        assertTrue(copy_server.copy(true));

		// byte
		assertEquals(( byte ) 0, copy_server.copy( ( byte ) 0 ));
		assertEquals(Byte.MIN_VALUE, copy_server.copy( Byte.MIN_VALUE ));
		assertEquals(Byte.MAX_VALUE, copy_server.copy( Byte.MAX_VALUE ));

		// short
		assertEquals(( short ) 0, copy_server.copy( ( short ) 0 ));
		assertEquals(Short.MIN_VALUE, copy_server.copy( Short.MIN_VALUE ));
		assertEquals(Short.MAX_VALUE, copy_server.copy( Short.MAX_VALUE ));

		// int
		assertEquals(0, copy_server.copy( 0 ));
		assertEquals(Integer.MIN_VALUE, copy_server.copy( Integer.MIN_VALUE ));
		assertEquals(Integer.MAX_VALUE, copy_server.copy( Integer.MAX_VALUE ));

		// long
		assertEquals(0L, copy_server.copy( 0L ));
		assertEquals(Long.MIN_VALUE, copy_server.copy( Long.MIN_VALUE ));
		assertEquals(Long.MAX_VALUE, copy_server.copy( Long.MAX_VALUE ));

		// float
		assertEquals(0.0F, copy_server.copy( 0.0 ), 0);
		assertEquals(Float.MIN_VALUE, copy_server.copy( Float.MIN_VALUE ), 0);
		assertEquals(Float.MAX_VALUE, copy_server.copy( Float.MAX_VALUE ), 0);
		assertEquals(Float.NEGATIVE_INFINITY, copy_server.copy( Float.NEGATIVE_INFINITY ), 0);
		assertEquals(Float.POSITIVE_INFINITY, copy_server.copy( Float.POSITIVE_INFINITY ), 0);
		assertTrue(Float.isNaN( copy_server.copy( Float.NaN ) ));

		// double
		assertEquals(0.0, copy_server.copy( 0.0 ), 0);
		assertEquals(Double.MIN_VALUE, copy_server.copy( Double.MIN_VALUE ), 0);
		assertEquals(Double.MAX_VALUE, copy_server.copy( Double.MAX_VALUE ), 0);
		assertEquals(Double.NEGATIVE_INFINITY, copy_server.copy( Double.NEGATIVE_INFINITY ), 0);
		assertEquals(Double.POSITIVE_INFINITY, copy_server.copy( Double.POSITIVE_INFINITY ), 0);
		assertTrue(Double.isNaN( copy_server.copy( Double.NaN ) ));
	}


	@Test
	public void testBasicObjects() {
		// Primitives (in object form)
		doCopyTest(Boolean.TRUE, copy_server );
		doCopyTest((byte) 1, copy_server );
		doCopyTest(Short.MIN_VALUE, copy_server );
		doCopyTest(10, copy_server );
		doCopyTest(Long.MAX_VALUE, copy_server );
		doCopyTest(Float.NaN, copy_server );
		doCopyTest(Double.NEGATIVE_INFINITY, copy_server );

		// String
		doCopyTest( "This is a test this is only a test", copy_server );
		doCopyTest( "Test of unicode: \uD840 \u0024", copy_server );

		// Collections
		doCopyTest( Arrays.asList( "this", "is", "a", "lib/test" ), copy_server );

		// Array
		doCopyTest( new String[] { "this", "is", "a", "lib/test" }, copy_server );

		// Class objects (MINA IoBuffer.get/putBuffer has issues with these)
		doCopyTest( Object.class, copy_server );
		doCopyTest( String.class, copy_server );
	}


	private void doCopyTest( Object value, CopyServer server ) {
		assertEquals(value, server.copy( value ));
	}



	public interface CopyServer {
		Object copy(Object obj);

		boolean copy(boolean obj);
		byte copy(byte obj);
		short copy(short obj);
		int copy(int obj);
		long copy(long obj);
		float copy(float obj);
		double copy(double obj);
	}

	private class BasicCopyServer implements CopyServer {
		@Override
		public Object copy( Object obj ) {
			return obj;
		}

		@Override
		public boolean copy( boolean obj ) {
			return obj;
		}

		@Override
		public byte copy( byte obj ) {
			return obj;
		}

		@Override
		public short copy( short obj ) {
			return obj;
		}

		@Override
		public int copy( int obj ) {
			return obj;
		}

		@Override
		public long copy( long obj ) {
			return obj;
		}

		@Override
		public float copy( float obj ) {
			return obj;
		}

		@Override
		public double copy( double obj ) {
			return obj;
		}
	}
}
