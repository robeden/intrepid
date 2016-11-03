package com.logicartisan.intrepid;

import com.logicartisan.intrepid.exception.InterruptedCallException;
import com.logicartisan.intrepid.exception.ServerException;
import com.logicartisan.common.core.thread.SharedThreadPool;
import com.logicartisan.common.core.thread.ThreadKit;
import junit.framework.TestCase;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * This tests abnormal method termination, such and thrown exceptions, thread termination,
 * etc.
 */
public class AbnormalMethodTerminationTest extends TestCase {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;

	private ServerInterface server;


	@Override
	protected void setUp() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).serverPort( 11751 ).openServer() );
		ServerImpl original_instance = new ServerImpl();
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			11751, null, null );
		assertNotNull( server_vmid );

		assertEquals( server_instance.getLocalVMID(), server_vmid );
		assertFalse( client_instance.getLocalVMID().equals( server_vmid ) );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		server = ( ServerInterface ) server_registry.lookup( "server" );
		assertNotNull( server );
	}



	@Override
	protected void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}


	public void testDieByCaughtException() {
		try {
			server.dieByCaughtException();
		}
		catch( Exception ex ) {
			assertEquals( Exception.class, ex.getClass() );
		}
	}

	public void testDieByRuntimeException() {
		try {
			server.dieByRuntimeException();
		}
		catch( RuntimeException ex ) {
			assertEquals( RuntimeException.class, ex.getClass() );
		}
	}

	public void testDieByUndeclaredRuntimeException() {
		try {
			server.dieByUndeclaredRuntimeException();
		}
		catch( RuntimeException ex ) {
			assertEquals( RuntimeException.class, ex.getClass() );
		}
	}

	public void testDieByDeclaredError() {
		try {
			server.dieByDeclaredError();
		}
		catch( ServerException ex ) {
			assertNotNull( ex.getCause() );
			assertEquals( Error.class, ex.getCause().getClass() );
		}
	}

	public void testDieByUndeclaredError() {
		try {
			server.dieByUndeclaredError();
		}
		catch( ServerException ex ) {
			assertNotNull( ex.getCause() );
			assertEquals( Error.class, ex.getCause().getClass() );
		}
	}

	public void testDieByOutOfMemory() {
		try {
			server.dieByOutOfMemory();
		}
		catch( ServerException ex ) {
			assertNotNull( ex.getCause() );
			assertEquals( OutOfMemoryError.class, ex.getCause().getClass() );
		}
	}

	public void testDieByThreadDeath() {
		try {
			server.dieByThreadDeath();
		}
		catch( ServerException ex ) {
			assertNotNull( ex.getCause() );
			assertEquals( ThreadDeath.class, ex.getCause().getClass() );
		}
	}

	public void testDieBySessionClose() {
		try {
			server.dieBySessionClose();
		}
		catch( InterruptedCallException ex ) {
			// this is good
		}
	}



	public static interface ServerInterface {
		public void dieByCaughtException() throws Exception;
		public void dieByRuntimeException() throws RuntimeException;
		public void dieByUndeclaredRuntimeException();
		public void dieByDeclaredError() throws Error;
		public void dieByUndeclaredError();
		public void dieByOutOfMemory();
		public void dieByThreadDeath();
		public void dieBySessionClose();
	}


	private static class ServerImpl implements ServerInterface {
		@Override
		public void dieByCaughtException() throws Exception {
			throw new Exception();
		}

		@Override
		public void dieByRuntimeException() throws RuntimeException {
			throw new RuntimeException();
		}

		@Override
		public void dieByUndeclaredRuntimeException() {
			throw new RuntimeException();
		}

		@Override
		public void dieByDeclaredError() throws Error {
			throw new Error();
		}

		@Override
		public void dieByUndeclaredError() {
			throw new Error();
		}

		@Override
		public void dieByOutOfMemory() {
			//noinspection MismatchedQueryAndUpdateOfCollection
			List<byte[]> list_of_doom = new LinkedList<>();
			//noinspection InfiniteLoopStatement
			while( true ) {
				list_of_doom.add( new byte[ 102400 ] );
			}
		}

		@SuppressWarnings( "deprecation" )
		@Override
		public void dieByThreadDeath() {
			final Thread thread_to_kill = Thread.currentThread();

			SharedThreadPool.INSTANCE.schedule(
				( Runnable ) thread_to_kill::stop, 1, TimeUnit.SECONDS );

			ThreadKit.sleep( 5000 );

			System.err.println( "Thread didn't die!" );
			fail( "Thread didn't die!" );
		}

		@Override
		public void dieBySessionClose() {
			Intrepid instance = IntrepidContext.getActiveInstance();
			VMID caller_vmid = IntrepidContext.getCallingVMID();

			instance.disconnect( caller_vmid );
		}
	}
}
