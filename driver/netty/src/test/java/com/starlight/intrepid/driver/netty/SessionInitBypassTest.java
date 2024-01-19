package com.starlight.intrepid.driver.netty;

import com.starlight.intrepid.*;
import com.starlight.intrepid.driver.ProtocolVersions;
import com.starlight.intrepid.driver.SessionInfo;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.InvokeIMessage;
import com.starlight.intrepid.message.PingIMessage;
import gnu.trove.map.TIntObjectMap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
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
	private Channel channel;


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

		if ( channel != null ) {
			channel.close();
			channel = null;
		}
	}


	// Test to see if a message is received at all
	@Test
	public void testCovertMessage_Invoke() throws Exception {
		IMessage message =
			new InvokeIMessage( 0, 1234, null, 4321, new Object[] {}, null, false );

		channel = connectAndSend( message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		assertNull( received_message.get() );
		assertFalse( channel.isActive() );
	}

	// Test to see if a message is received at all
	@Test
	public void testCovertMessage_Ping() throws Exception {
		IMessage message = new PingIMessage( ( short ) 123 );

		channel = connectAndSend( message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		assertNull( received_message.get() );
		assertFalse( channel.isActive() );
	}


	// Test to see if a method can be invoked (full stack through server)
	@Test
	public void testCovertInvoke() throws Exception {
		InvokeIMessage invoke_message = new InvokeIMessage(
			0, object_id, null, method_id, new Object[] {}, null, false );

		channel = connectAndSend( invoke_message );

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

		channel = connectAndSend( invoke_message );

		session_close_latch.await( 5, TimeUnit.SECONDS );

		Assumptions.assumeTrue(method_invoked.get(),
			"Convert invocation didn't happen (bug #5 fixed, hopefully)");
		assertTrue( indicated_in_call.get(),
			"During call, IntrepidContext indicated we weren't in a call" );
	}



	private Channel connectAndSend( IMessage message ) throws InterruptedException {
		VMID vmid = VMID.createForTesting();

		EventLoopGroup group = new NioEventLoopGroup();

		Bootstrap bootstrap = new Bootstrap()
			.group(group)
			.channel(NioSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, true)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.option(ChannelOption.SO_LINGER, 0)
			.handler(new ChannelInitializer<>() {
				@Override
				protected void initChannel(Channel ch) throws Exception {
					ch.pipeline().addLast(new NettyIMessageDecoder(vmid, new ThreadLocal<>(),
						( uuid, s ) -> {
							throw new AssertionError( "Shouldn't be called" );
						}, ObjectCodec.DEFAULT));
					ch.pipeline().addLast(new NettyIMessageEncoder(ObjectCodec.DEFAULT));
				}

				@Override
				public void channelRead(ChannelHandlerContext ctx, Object msg) {
					System.out.println( "Received: " + msg );
				}

				@Override
				public void channelInactive(ChannelHandlerContext ctx) {
					System.out.println( "Channel closed" );
					session_close_latch.countDown();
				}
			});
		session_close_latch = new CountDownLatch( 1 );


		ChannelFuture connect_future = bootstrap.connect( new InetSocketAddress(
			InetAddress.getLoopbackAddress(), server.getServerPort() ) );
		assertTrue( connect_future.await( 30, TimeUnit.SECONDS ) );

		SessionInfo mock_info = Mockito.mock( SessionInfo.class );
		Mockito.when( mock_info.getProtocolVersion() )
			.thenReturn( ProtocolVersions.PROTOCOL_VERSION );


		Channel channel = connect_future.channel();

		// Need to manually set the SessionInfo, otherwise we can't send this message
		// due to version checks.
		channel.attr( NettyIntrepidDriver.SESSION_INFO_KEY ).set( mock_info );

		assertNotNull( channel );
		assertTrue( channel.isActive() );

		ChannelFuture write_future = channel.writeAndFlush( message );
		assertTrue( write_future.await( 30, TimeUnit.SECONDS ) );

		return channel;
	}
}
