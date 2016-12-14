package com.logicartisan.intrepid;

import com.logicartisan.common.core.IOKit;
import com.logicartisan.intrepid.exception.IntrepidRuntimeException;
import com.logicartisan.common.core.thread.ObjectSlot;
import com.logicartisan.common.core.thread.SharedThreadPool;
import junit.framework.TestCase;

import java.io.NotSerializableException;
import java.io.Serializable;
import java.net.InetAddress;


/**
 *
 */
public class NonSerializableResultTest extends TestCase {
	private Intrepid client = null;
	private Intrepid server = null;

	@Override
	protected void tearDown() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client != null ) client.close();
		if ( server != null ) server.close();
	}


	public void testKillerClass() throws Exception {
		try {
			IOKit.serialize( new SerializationKiller() );
			fail( "Shouldn't get here" );
		}
		catch( NotSerializableException ex ) {
			// this is good
		}
	}

	public void testReturnValue() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server = Intrepid.create( new IntrepidSetup().openServer() );
		client = Intrepid.create( null );

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

		VMID server_vmid = client.connect( InetAddress.getLocalHost(),
			server.getServerPort().intValue(), null, null );
		final MyIfc callable =
			( MyIfc ) client.getRemoteRegistry( server_vmid ).lookup( "server" );

		final ObjectSlot<Throwable> result_slot = new ObjectSlot<Throwable>();

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
        assertNotNull( "Timed out while waiting for value!", t );
		System.out.println( "Got this exception (possibly expected): " + t );

		t.printStackTrace( System.out );
		if ( t instanceof IntrepidRuntimeException ) {
			// this is good
		}
		else {
			fail( "Unexpected exception type: " + t );
		}
	}

	public void testThrow() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server = Intrepid.create( new IntrepidSetup().openServer() );
		client = Intrepid.create( null );

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

		VMID server_vmid = client.connect( InetAddress.getLocalHost(),
			server.getServerPort().intValue(), null, null );
		final MyIfc callable =
			( MyIfc ) client.getRemoteRegistry( server_vmid ).lookup( "server" );

		final ObjectSlot<Throwable> result_slot = new ObjectSlot<Throwable>();

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
        assertNotNull( "Timed out while waiting for value!", t );
		System.out.println( "Got this exception (possibly expected): " + t );

		t.printStackTrace( System.out );
		if ( t instanceof IntrepidRuntimeException ) {
			// this is good
		}
		else {
			fail( "Unexpected exception type: " + t );
		}
	}


	static class SerializationKiller implements Serializable {
		private final Object diediedie = new Object();
	}
    
    static class ExceptionOfDeath extends Exception {
        private final SerializationKiller diediedie = new SerializationKiller();
    }
    

	public static interface MyIfc {
		// Not using Callable because I don't want it to be able to throw
		// NotSerializableException.
		public Object call();
        
        public void throwException() throws ExceptionOfDeath;
	}
}
