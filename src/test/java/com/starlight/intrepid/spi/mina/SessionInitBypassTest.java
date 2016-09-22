package com.starlight.intrepid.spi.mina;

import com.starlight.intrepid.*;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.InvokeIMessage;
import gnu.trove.map.TIntObjectMap;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public class SessionInitBypassTest {
	private Intrepid server;
	private AtomicReference<IMessage> received_message;

	@Before
	public void setUp() throws Exception {
		received_message = new AtomicReference<>();

		PerformanceListener perf_listener = new PerformanceListener() {
			@Override
			public void messageReceived( VMID source_vmid, IMessage message ) {
				received_message.set( message );
			}
		};

		server = Intrepid.create( new IntrepidSetup()
			.openServer()
			.performanceListener( perf_listener ) );
	}

	@After
	public void tearDown() {
		received_message = null;
		server.close();
		server = null;
	}


	@Test
	public void testCovertInvoke() throws Exception {
		VMID vmid = VMID.createForTesting();
		IntrepidCodecFactory codec =
			new IntrepidCodecFactory( vmid, new ThreadLocal<>() );
		NioSocketConnector connector = new NioSocketConnector();
		connector.getFilterChain().addLast( "intrepid", new ProtocolCodecFilter( codec ) );
		connector.setHandler( new IoHandlerAdapter() );


		ConnectFuture connect_future = connector.connect( new InetSocketAddress(
			InetAddress.getLoopbackAddress(), server.getServerPort() ) );
		connect_future.await();

		IoSession session = connect_future.getSession();
		Assert.assertNotNull( session );
		Assert.assertTrue( session.isConnected() );

		InvokeIMessage invoke_message =
			new InvokeIMessage( 0, 1234, null, 4321, new Object[] {}, null, false );
		session.write( invoke_message );

		Thread.sleep( 1000 );

		Assert.assertNull( "Received message", received_message.get() );
		Assert.assertFalse( "Still connected", session.isConnected() );
	}
}
