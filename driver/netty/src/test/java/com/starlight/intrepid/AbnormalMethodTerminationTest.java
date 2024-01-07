package com.starlight.intrepid;

import com.logicartisan.common.core.thread.SharedThreadPool;
import com.logicartisan.common.core.thread.ThreadKit;
import com.starlight.intrepid.exception.InterruptedCallException;
import com.starlight.intrepid.exception.ServerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


/**
 * This tests abnormal method termination, such and thrown exceptions, thread termination,
 * etc.
 */
public class AbnormalMethodTerminationTest {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;

	private ServerInterface server;


	@BeforeEach
	public void setUp() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.newBuilder()
			.vmidHint( "server" )
			.serverAddress( new InetSocketAddress( 11751 ) )
			.openServer()
			.build();
		ServerImpl original_instance = new ServerImpl();
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.newBuilder().vmidHint( "client" ).build();

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			11751, null, null );
		assertNotNull(server_vmid);

		assertEquals(server_instance.getLocalVMID(), server_vmid);
        assertNotEquals(client_instance.getLocalVMID(), server_vmid);

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );
		server = ( ServerInterface ) server_registry.lookup( "server" );
		assertNotNull(server);
	}


	@AfterEach
	public void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}


	@Test
	public void testDieByCaughtException() {
		try {
			server.dieByCaughtException();
		}
		catch( Exception ex ) {
			assertEquals(Exception.class, ex.getClass());
		}
	}

	@Test
	public void testDieByRuntimeException() {
		try {
			server.dieByRuntimeException();
		}
		catch( RuntimeException ex ) {
			assertEquals(RuntimeException.class, ex.getClass());
		}
	}

	@Test
	public void testDieByUndeclaredRuntimeException() {
		try {
			server.dieByUndeclaredRuntimeException();
		}
		catch( RuntimeException ex ) {
			assertEquals(RuntimeException.class, ex.getClass());
		}
	}

	@Test
	public void testDieByDeclaredError() {
		try {
			server.dieByDeclaredError();
		}
		catch( ServerException ex ) {
			assertNotNull(ex.getCause());
			assertEquals(Error.class, ex.getCause().getClass());
		}
	}

	@Test
	public void testDieByUndeclaredError() {
		try {
			server.dieByUndeclaredError();
		}
		catch( ServerException ex ) {
			assertNotNull(ex.getCause());
			assertEquals(Error.class, ex.getCause().getClass());
		}
	}

	@Test
	public void testDieByOutOfMemory() {
		try {
			server.dieByOutOfMemory();
		}
		catch( ServerException ex ) {
			assertNotNull(ex.getCause());
			assertEquals(OutOfMemoryError.class, ex.getCause().getClass());
		}
	}

	@Test
	public void testDieByThreadDeath() {
		try {
			server.dieByThreadDeath();
		}
		catch( ServerException ex ) {
			assertNotNull(ex.getCause());
			assertEquals(ThreadDeath.class, ex.getCause().getClass());
		}
	}

	@Test
	public void testDieBySessionClose() {
		try {
			server.dieBySessionClose();
		}
		catch( InterruptedCallException ex ) {
			// this is good
		}
	}



	public interface ServerInterface {
		void dieByCaughtException() throws Exception;
		void dieByRuntimeException() throws RuntimeException;
		void dieByUndeclaredRuntimeException();
		void dieByDeclaredError() throws Error;
		void dieByUndeclaredError();
		void dieByOutOfMemory();
		void dieByThreadDeath();
		void dieBySessionClose();
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
				list_of_doom.add( new byte[ 1024000 ] );
			}
		}

		@SuppressWarnings( "deprecation" )
		@Override
		public void dieByThreadDeath() {
			final Thread thread_to_kill = Thread.currentThread();

			SharedThreadPool.INSTANCE.schedule(
                thread_to_kill::stop, 1, TimeUnit.SECONDS );

			ThreadKit.sleep( 5000 );

			System.err.println( "Thread didn't die!" );
			Assertions.fail("Thread didn't die!");
		}

		@Override
		public void dieBySessionClose() {
			Intrepid instance = IntrepidContext.getActiveInstance();
			VMID caller_vmid = IntrepidContext.getCallingVMID();

			instance.disconnect( caller_vmid );
		}
	}
}
