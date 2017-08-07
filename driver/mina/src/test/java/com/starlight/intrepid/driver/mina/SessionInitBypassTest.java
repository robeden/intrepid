package com.starlight.intrepid.driver.mina;

import com.starlight.intrepid.*;
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
import org.junit.*;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public class SessionInitBypassTest {
	private Intrepid server;

	private AtomicReference<IMessage> received_message; // from perf listener
	private AtomicReference<IMessage> invalid_received_message; // from perf listener

	private AtomicBoolean method_invoked;
	private int object_id;
	private int method_id;

	private AtomicBoolean indicated_in_call;

	private CountDownLatch session_close_latch;
	private IoSession session;


	@Before
	public void setUp() throws Exception {
		received_message = new AtomicReference<>();
		invalid_received_message = new AtomicReference<>();

		PerformanceListener perf_listener = new PerformanceListener() {
			@Override
			public void messageReceived( VMID source_vmid, IMessage message ) {
				received_message.set( message );
			}

			@Override
			public void invalidMessageReceived( SocketAddress peer,
				IMessage message ) {

				invalid_received_message.set( message );
			}
		};

		server = Intrepid.create( new IntrepidSetup()
			.openServer()
			.performanceListener( perf_listener ) );

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
		Assert.assertEquals( 1, method_map.size() );
		method_id = method_map.keys()[ 0 ];

		System.out.println( "Object ID: " + object_id );
		System.out.println( "Method ID: " + method_id );
	}

	@After
	public void tearDown() {
		received_message = null;
		invalid_received_message = null;
		method_invoked = null;

		server.close();
		server = null;

		if ( session != null ) {
			session.close( true );
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

		Assert.assertNull( "Received message", received_message.get() );
		Assert.assertFalse( "Still connected", session.isConnected() );

		Assert.assertEquals( message, invalid_received_message.get() );
	}

	// Test to see if a message is received at all
	@Test
	public void testCovertMessage_Ping() throws Exception {
		IMessage message = new PingIMessage( ( short ) 123 );

		session = connectAndSend( message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		Assert.assertNull( "Received message", received_message.get() );
		Assert.assertFalse( "Still connected", session.isConnected() );

		Assert.assertEquals( message, invalid_received_message.get() );
	}


	// Test to see if a method can be invoked (full stack through server)
	@Test
	public void testCovertInvoke() throws Exception {
		InvokeIMessage invoke_message = new InvokeIMessage(
			0, object_id, null, method_id, new Object[] {}, null, false );

		session = connectAndSend( invoke_message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		Assert.assertFalse( "Method invoked", method_invoked.get() );

		Assert.assertEquals( invoke_message, invalid_received_message.get() );
	}


	// NOTE: This ONLY applies if the covert invocation happens. I'm putting it here
	//       because I'm fixing issues in order, starting with this one. It also won't hit
	//       with assertions enabled.
	@Test
	public void testCovertInvokeIndicateInCall() throws Exception {
		Assume.assumeFalse( "Assertions need to be disabled",
			SessionInitBypassTest.class.desiredAssertionStatus() );

		InvokeIMessage invoke_message = new InvokeIMessage(
			0, object_id, null, method_id, new Object[] {}, null, false );

		session = connectAndSend( invoke_message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		Assume.assumeTrue( "Convert invocation didn't happen (bug #5 fixed, hopefully)",
			method_invoked.get() );
		Assert.assertTrue( "During call, IntrepidContext indicated we weren't in a call",
			indicated_in_call.get() );

		Assert.assertEquals( invoke_message, invalid_received_message.get() );
	}



	private IoSession connectAndSend( IMessage message ) throws InterruptedException {
		VMID vmid = VMID.createForTesting();
		IntrepidCodecFactory codec =
			new IntrepidCodecFactory( vmid, new ThreadLocal<>() );
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
		connect_future.await();

		IoSession session = connect_future.getSession();
		Assert.assertNotNull( session );
		Assert.assertTrue( session.isConnected() );

		WriteFuture write_future = session.write( message );
		write_future.await();

		return session;
	}
}
