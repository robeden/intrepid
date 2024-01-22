package com.starlight.intrepid;

import com.logicartisan.common.core.IOKit;
import com.logicartisan.common.core.thread.ObjectSlot;
import com.logicartisan.common.core.thread.SharedThreadPool;
import com.starlight.intrepid.exception.IntrepidRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.NotSerializableException;
import java.io.Serializable;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;


/**
 *
 */
public class NonSerializableResultTest {
	private Intrepid client = null;
	private Intrepid server = null;

	@AfterEach
	public void tearDown() throws Exception {
		if ( client != null ) client.close();
		if ( server != null ) server.close();
	}


	@Test
	public void testKillerClass() throws Exception {
		try {
			IOKit.serialize( new SerializationKiller() );
			fail("Shouldn't get here");
		}
		catch( NotSerializableException ex ) {
			// this is good
		}
	}

	@Test
	public void testReturnValue() throws Exception {
		server = Intrepid.newBuilder().openServer().build();
		client = Intrepid.newBuilder().build();

		server.getLocalRegistry().bind( "server", new MyIfc() {
			@Override
			public Object call() {
				return new SerializationKiller();
			}

            @Override
            public void throwException() throws ExceptionOfDeath {
                throw new ExceptionOfDeath();
            }
        } );

		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );
		final MyIfc callable =
			( MyIfc ) client.getRemoteRegistry( server_vmid ).lookup( "server" );

		final ObjectSlot<Throwable> result_slot = new ObjectSlot<>();

		SharedThreadPool.INSTANCE.execute( new Runnable() {
			@Override
			public void run() {
				try {
					Object result = callable.call();
					System.out.println( "Got unexpected result: " + result );
					result_slot.set(
						new Exception( "Shouldn't have gotten result!: " + result ) );
				}
				catch( Throwable t ) {
					result_slot.set( t );
				}
			}
		} );

		@SuppressWarnings( "ThrowableResultOfMethodCallIgnored" )
		Throwable t = result_slot.waitForValue( 10000 );
        assertNotNull(t, "Timed out while waiting for value!");
		System.out.println( "Got this exception (possibly expected): " + t );

		t.printStackTrace( System.out );
		if ( t instanceof IntrepidRuntimeException ) {
			// this is good
		}
		else {
			fail("Unexpected exception type: " + t);
		}
	}

	@Test
	public void testThrow() throws Exception {
		server = Intrepid.newBuilder().openServer().build();
		client = Intrepid.newBuilder().build();

		server.getLocalRegistry().bind( "server", new MyIfc() {
			@Override
			public Object call() {
				return new SerializationKiller();
			}

            @Override
            public void throwException() throws ExceptionOfDeath {
                throw new ExceptionOfDeath();
            }
        } );

		VMID server_vmid = client.connect( InetAddress.getLoopbackAddress(),
			server.getServerPort().intValue(), null, null );
		final MyIfc callable =
			( MyIfc ) client.getRemoteRegistry( server_vmid ).lookup( "server" );

		final ObjectSlot<Throwable> result_slot = new ObjectSlot<>();

		SharedThreadPool.INSTANCE.execute( new Runnable() {
			@Override
			public void run() {
				try {
                    callable.throwException();
					result_slot.set( new Exception( "Should have thrown exception!" ) );
				}
				catch( Throwable t ) {
					result_slot.set( t );
				}
			}
		} );

        //noinspection ThrowableResultOfMethodCallIgnored
		Throwable t = result_slot.waitForValue( 10000 );
        assertNotNull(t, "Timed out while waiting for value!");
		System.out.println( "Got this exception (possibly expected): " + t );

		t.printStackTrace( System.out );
		if ( t instanceof IntrepidRuntimeException ) {
			// this is good
		}
		else {
			fail("Unexpected exception type: " + t);
		}
	}


	static class SerializationKiller implements Serializable {
		private final Object diediedie = new Object();
	}
    
    static class ExceptionOfDeath extends Exception {
        private final SerializationKiller diediedie = new SerializationKiller();
    }
    

	public interface MyIfc {
		// Not using Callable because I don't want it to be able to throw
		// NotSerializableException.
        Object call();
        
        void throwException() throws ExceptionOfDeath;
	}
}
