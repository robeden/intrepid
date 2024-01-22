package com.starlight.intrepid.driver.mina;

import com.starlight.intrepid.*;
import com.starlight.intrepid.driver.ProtocolVersions;
import com.starlight.intrepid.driver.SessionInfo;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.InvokeIMessage;
import com.starlight.intrepid.message.PingIMessage;
import gnu.trove.map.TIntObjectMap;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 *
 */
public class SessionInitBypassTest {
	private Intrepid server;

	private AtomicReference<IMessage> received_message; // from perf listener

	private AtomicBoolean method_invoked;
	private int object_id;
	private int method_id;

	private AtomicBoolean indicated_in_call;

	private CountDownLatch session_close_latch;
	private IoSession session;


	@BeforeEach
	public void setUp() throws Exception {
		received_message = new AtomicReference<>();

		PerformanceListener perf_listener = new PerformanceListener() {
			@Override
			public void messageReceived( VMID source_vmid, IMessage message ) {
				received_message.set( message );
			}
		};

		server = Intrepid.newBuilder()
			.openServer()
			.performanceListener( perf_listener )
			.build();

		method_invoked = new AtomicBoolean( false );
		indicated_in_call = new AtomicBoolean( false );
		Runnable delegate = () -> {
			method_invoked.set( true );
			System.out.println( "Server method invoked:" );
			try {
				indicated_in_call.set( IntrepidContext.isCall() );
				System.out.println( "  In call: " + IntrepidContext.isCall() );
				System.out.println( "  Instance: " + IntrepidContext.getActiveInstance() );
				System.out.println( "  VMID: " + IntrepidContext.getCallingVMID() );
				System.out.println( "  Host: " + IntrepidContext.getCallingHost() );
				System.out.println( "  User Info: " + IntrepidContext.getUserInfo() );
			}
			catch( Exception ex ) {
				ex.printStackTrace();
			}
		};
		Runnable proxy = ( Runnable ) server.createProxy( delegate );
		object_id = IntrepidTestProxyAccess.getObjectID( proxy );

		final TIntObjectMap<Method> method_map =
			IntrepidTestProxyAccess.generateMethodMap( Runnable.class );
		assertEquals( 1, method_map.size() );
		method_id = method_map.keys()[ 0 ];

		System.out.println( "Object ID: " + object_id );
		System.out.println( "Method ID: " + method_id );
	}

	@AfterEach
	public void tearDown() {
		received_message = null;
		method_invoked = null;

		server.close();
		server = null;

		if ( session != null ) {
			session.closeNow();
			session = null;
		}
	}


	// Test to see if a message is received at all
	@Test
	public void testCovertMessage_Invoke() throws Exception {
		IMessage message =
			new InvokeIMessage( 0, 1234, null, 4321, new Object[] {}, null, false );

		session = connectAndSend( message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		assertNull( received_message.get() );
		assertFalse( session.isConnected() );
	}

	// Test to see if a message is received at all
	@Test
	public void testCovertMessage_Ping() throws Exception {
		IMessage message = new PingIMessage( ( short ) 123 );

		session = connectAndSend( message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		assertNull( received_message.get() );
		assertFalse( session.isConnected() );
	}


	// Test to see if a method can be invoked (full stack through server)
	@Test
	public void testCovertInvoke() throws Exception {
		InvokeIMessage invoke_message = new InvokeIMessage(
			0, object_id, null, method_id, new Object[] {}, null, false );

		session = connectAndSend( invoke_message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		assertFalse( method_invoked.get() );
	}


	// NOTE: This ONLY applies if the covert invocation happens. I'm putting it here
	//       because I'm fixing issues in order, starting with this one. It also won't hit
	//       with assertions enabled.
	@Test
	public void testCovertInvokeIndicateInCall() throws Exception {
		assumeFalse( SessionInitBypassTest.class.desiredAssertionStatus(),
			"Assertions need to be disabled");

		InvokeIMessage invoke_message = new InvokeIMessage(
			0, object_id, null, method_id, new Object[] {}, null, false );

		session = connectAndSend( invoke_message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		Assumptions.assumeTrue(method_invoked.get(),
			"Convert invocation didn't happen (bug #5 fixed, hopefully)");
		assertTrue( indicated_in_call.get(),
			"During call, IntrepidContext indicated we weren't in a call" );
	}



	private IoSession connectAndSend( IMessage message ) throws InterruptedException {
		VMID vmid = VMID.createForTesting();
		IntrepidCodecFactory codec =
			new IntrepidCodecFactory( vmid, new ThreadLocal<>(),
				( uuid, s ) -> {
					throw new AssertionError( "Shouldn't be called" );
				}, ObjectCodec.DEFAULT );
		NioSocketConnector connector = new NioSocketConnector();
		connector.getFilterChain().addLast( "intrepid", new ProtocolCodecFilter( codec ) );

		session_close_latch = new CountDownLatch( 1 );
		connector.setHandler( new IoHandlerAdapter() {
			@Override
			public void messageReceived( IoSession session, Object message )
				throws Exception {
				System.out.println( "Received: " + message );
			}

			@Override
			public void sessionClosed( IoSession session ) throws Exception {
				System.out.println( "Session closed" );
				session_close_latch.countDown();
			}
		} );


		ConnectFuture connect_future = connector.connect( new InetSocketAddress(
			InetAddress.getLoopbackAddress(), server.getServerPort() ) );
		assertTrue( connect_future.await( 30, TimeUnit.SECONDS ) );

		SessionInfo mock_info = Mockito.mock( SessionInfo.class );
		Mockito.when( mock_info.getProtocolVersion() )
			.thenReturn( ProtocolVersions.PROTOCOL_VERSION );


		IoSession session = connect_future.getSession();

		// Need to manually set the SessionInfo, otherwise we can't send this message
		// due to version checks.
		session.setAttribute( MINAIntrepidDriver.SESSION_INFO_KEY, mock_info );

		assertNotNull( session );
		assertTrue( session.isConnected() );

		WriteFuture write_future = session.write( message );
		assertTrue( write_future.await( 30, TimeUnit.SECONDS ) );

		return session;
	}
}
