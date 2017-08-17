package com.starlight.intrepid;

import com.logicartisan.common.core.Pair;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.exception.ChannelRejectedException;
import com.starlight.intrepid.message.*;
import junit.framework.TestCase;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class PerformanceListenerTest extends TestCase {
	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	@Override
	protected void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}
	
	public void testCalls() throws Exception {
		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		TestPerformanceListener server_listener = new TestPerformanceListener( "server" );
		TestPerformanceListener client_listener = new TestPerformanceListener( "client" );

		ChannelAcceptor server_channel_acceptor = new ChannelAcceptor() {
			@Override
			public void newChannel( final ByteChannel channel, VMID source_vmid,
				Serializable attachment ) throws ChannelRejectedException {

				new Thread() {
					@Override
					public void run() {
						ByteBuffer buffer = ByteBuffer.allocate( 1 << 18 ); // 256KB

						try {
							int read;
							//noinspection UnusedAssignment
							while( ( read = channel.read( buffer ) ) >= 0 ) {
								buffer.clear();
							}
						}
						catch( IOException ex ) {
							// ignore
						}
					}
				}.start();
			}
		};

		server_instance = Intrepid.create(
			new IntrepidSetup().vmidHint( "server" ).serverPort(
			11751 ).openServer().performanceListener( server_listener ).channelAcceptor(
			server_channel_acceptor ) );
		CommTest.ServerImpl original_instance =
			new CommTest.ServerImpl( true, server_instance.getLocalVMID() );
		server_instance.getLocalRegistry().bind( "server", original_instance );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint(
			"client" ).performanceListener( client_listener ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			11751, null, null );

		// Make sure queue is empty
		assertTrue( server_listener.queue.toString(), server_listener.queue.isEmpty() );
		assertTrue( client_listener.queue.toString(), client_listener.queue.isEmpty() );

		// Lookup the server object
		Registry server_registry = client_instance.getRemoteRegistry( server_vmid );

		//////////////////////////////////////////////////////////////////////////////////
		// Registry lookup

		long before_call_time = System.currentTimeMillis();
		CommTest.Server server = ( CommTest.Server ) server_registry.lookup( "server" );
		long after_call_time = System.currentTimeMillis();


		// SERVER - started message
		Pair<CallType,List<Object>> pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.INBOUND_REMOTE_CALL_STARTED, pair.getOne() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		long time  = ( ( Long ) pair.getTwo().get( 1 ) ).longValue();
		assertTrue( "Time: " + time + " Before: " + before_call_time + " After: " +
			after_call_time, time >= before_call_time && time <= after_call_time );
		int server_call_id = ( ( Integer ) pair.getTwo().get( 2 ) ).intValue();
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 3 ) );
		assertEquals( Integer.valueOf( 0 ), pair.getTwo().get( 4 ) );   // object 0
		// 5 = method id
		assertEquals( "lookup", ( ( Method ) pair.getTwo().get( 6 ) ).getName() );
		assertEquals( 1, ( ( Object[] ) pair.getTwo().get( 7 ) ).length );   // args
		assertEquals( "server", ( ( Object[] ) pair.getTwo().get( 7 ) )[ 0 ] );   // args
		assertNull( pair.getTwo().get( 8 ) );   // user context
		assertNull( pair.getTwo().get( 9 ) );   // persistent name

		// SERVER - completed message
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.INBOUND_REMOTE_CALL_COMPLETED, pair.getOne() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertEquals( Integer.valueOf( server_call_id ), pair.getTwo().get( 2 ) );
		assertEquals( server, pair.getTwo().get( 3 ) );
		assertEquals( Boolean.FALSE, pair.getTwo().get( 4 ) );
		
		// CLIENT - started message
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.REMOTE_CALL_STARTED, pair.getOne() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		time  = ( ( Long ) pair.getTwo().get( 1 ) ).longValue();
		assertTrue( "Time: " + time + " Before: " + before_call_time + " After: " +
			after_call_time, time >= before_call_time && time <= after_call_time );
		int client_call_id = ( ( Integer ) pair.getTwo().get( 2 ) ).intValue();
		assertEquals( server_call_id, client_call_id );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 3 ) );
		assertEquals( Integer.valueOf( 0 ), pair.getTwo().get( 4 ) );   // object 0
		// 5 = method id
		assertEquals( "lookup", ( ( Method ) pair.getTwo().get( 6 ) ).getName() );
		assertEquals( 1, ( ( Object[] ) pair.getTwo().get( 7 ) ).length );   // args
		assertEquals( "server", ( ( Object[] ) pair.getTwo().get( 7 ) )[ 0 ] );   // args
		assertNull( pair.getTwo().get( 8 ) );   // user context
		assertNull( pair.getTwo().get( 9 ) );   // persistent name

		// CLIENT - completed message
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.REMOTE_CALL_COMPLETED, pair.getOne() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		time  = ( ( Long ) pair.getTwo().get( 1 ) ).longValue();
		assertTrue( "Time: " + time + " Before: " + before_call_time + " After: " +
			after_call_time, time >= before_call_time && time <= after_call_time );
		assertEquals( Integer.valueOf( client_call_id ), pair.getTwo().get( 2 ) );
		assertEquals( server, pair.getTwo().get( 3 ) );
		assertEquals( Boolean.FALSE, pair.getTwo().get( 4 ) );
		Long server_time = ( Long ) pair.getTwo().get( 5 );
		assertNotNull( server_time );
		assertTrue( server_time.toString(),
			TimeUnit.NANOSECONDS.toMillis( server_time.longValue() ) <=
			( after_call_time - before_call_time ) );


		final int server_oid = ( ( Proxy ) server ).__intrepid__getObjectID();
		

		//////////////////////////////////////////////////////////////////////////////////
		// Normal method call
		before_call_time = System.currentTimeMillis();
		server.getMessage();
		after_call_time = System.currentTimeMillis();

		// SERVER - started message
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.INBOUND_REMOTE_CALL_STARTED, pair.getOne() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		time  = ( ( Long ) pair.getTwo().get( 1 ) ).longValue();
		assertTrue( "Time: " + time + " Before: " + before_call_time + " After: " +
			after_call_time, time >= before_call_time && time <= after_call_time );
		server_call_id = ( ( Integer ) pair.getTwo().get( 2 ) ).intValue();
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 3 ) );
		assertEquals( Integer.valueOf( server_oid ), pair.getTwo().get( 4 ) );
		// 5 = method id
		assertEquals( "getMessage", ( ( Method ) pair.getTwo().get( 6 ) ).getName() );
		assertNull( pair.getTwo().get( 7 ) );   // args
		assertNull( pair.getTwo().get( 8 ) );   // user context
		assertNull( pair.getTwo().get( 9 ) );   // persistent name

		// SERVER - completed message
		pair = getNextMessage( server_listener, 2000 );
		assertEquals( CallType.INBOUND_REMOTE_CALL_COMPLETED, pair.getOne() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertEquals( Integer.valueOf( server_call_id ), pair.getTwo().get( 2 ) );
		assertEquals( "Message from server", pair.getTwo().get( 3 ) );
		assertEquals( Boolean.FALSE, pair.getTwo().get( 4 ) );

		// CLIENT - started message
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.REMOTE_CALL_STARTED, pair.getOne() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		time  = ( ( Long ) pair.getTwo().get( 1 ) ).longValue();
		assertTrue( "Time: " + time + " Before: " + before_call_time + " After: " +
			after_call_time, time >= before_call_time && time <= after_call_time );
		client_call_id = ( ( Integer ) pair.getTwo().get( 2 ) ).intValue();
		assertEquals( server_call_id, client_call_id );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 3 ) );
		assertEquals( Integer.valueOf( server_oid ), pair.getTwo().get( 4 ) );
		// 5 = method id
		assertEquals( "getMessage", ( ( Method ) pair.getTwo().get( 6 ) ).getName() );
		assertNull( pair.getTwo().get( 7 ) );   // args
		assertNull( pair.getTwo().get( 8 ) );   // user context
		assertNull( pair.getTwo().get( 9 ) );   // persistent name

		// CLIENT - completed message
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.REMOTE_CALL_COMPLETED, pair.getOne() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		time  = ( ( Long ) pair.getTwo().get( 1 ) ).longValue();
		assertTrue( "Time: " + time + " Before: " + before_call_time + " After: " +
			after_call_time, time >= before_call_time && time <= after_call_time );
		assertEquals( Integer.valueOf( client_call_id ), pair.getTwo().get( 2 ) );
		assertEquals( "Message from server", pair.getTwo().get( 3 ) );
		assertEquals( Boolean.FALSE, pair.getTwo().get( 4 ) );
		server_time = ( Long ) pair.getTwo().get( 5 );
		assertNotNull( server_time );
		assertTrue( server_time.toString(),
			TimeUnit.NANOSECONDS.toMillis( server_time.longValue() ) <=
			( after_call_time - before_call_time ) );


		//////////////////////////////////////////////////////////////////////////////////
		// Data channel

		ByteChannel channel = client_instance.createChannel( server_vmid, null );

		// CLIENT - channel opened
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.VIRTUAL_CHANNEL_OPENED, pair.getOne() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 1 ) );
		short client_channel_id = ( ( Short ) pair.getTwo().get( 2 ) ).shortValue();

		// SERVER - channel opened
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.VIRTUAL_CHANNEL_OPENED, pair.getOne() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 1 ) );
		assertEquals( Short.valueOf( client_channel_id ), pair.getTwo().get( 2 ) );

		ByteBuffer buffer = ByteBuffer.allocate( 1024 );
		byte[] to_write = new byte[ buffer.remaining() ];
		new Random().nextBytes( to_write );
		buffer.put( to_write );
		buffer.flip();
		channel.write( buffer );

		// CLIENT - channel data sent
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.VIRTUAL_CHANNEL_DATA_SENT, pair.getOne() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 1 ) );
		assertEquals( Short.valueOf( client_channel_id ), pair.getTwo().get( 2 ) );
		assertEquals( Integer.valueOf( to_write.length ), pair.getTwo().get( 3 ) );

		// SERVER - channel data received
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.VIRTUAL_CHANNEL_DATA_RECEIVED, pair.getOne() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 1 ) );
		assertEquals( Short.valueOf( client_channel_id ), pair.getTwo().get( 2 ) );
		assertEquals( Integer.valueOf( to_write.length ), pair.getTwo().get( 3 ) );

		channel.close();

		// CLIENT - channel close
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.VIRTUAL_CHANNEL_CLOSED, pair.getOne() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 1 ) );
		assertEquals( Short.valueOf( client_channel_id ), pair.getTwo().get( 2 ) );

		// SERVER - channel close
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.VIRTUAL_CHANNEL_CLOSED, pair.getOne() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 1 ) );
		assertEquals( Short.valueOf( client_channel_id ), pair.getTwo().get( 2 ) );
	}


	public void testMessages() throws Exception {
		// Disable inter-instance bridge so we see all messages
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		
		TestPerformanceListener server_listener =
			new TestPerformanceListener( "server", true );
		TestPerformanceListener client_listener =
			new TestPerformanceListener( "client", true );

		// Listener added during setup
		server_instance = Intrepid.create(
			new IntrepidSetup().openServer().performanceListener( server_listener ) );
		// Listener added after setup
		client_instance = Intrepid.create( null );
		client_instance.addPerformanceListener( client_listener );


		// Establish connection
		VMID vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_instance.getServerPort().intValue(), null, null );
		assertNotNull( vmid );
		assertEquals( server_instance.getLocalVMID(), vmid );


		// Check for connection messages

		// Client -> Server (SessionInit sent)
		Pair<CallType,List<Object>> pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_SENT, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
//		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof SessionInitIMessage );

		// Client -> Server (SessionInit received)
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_RECEIVED, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
//		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof SessionInitIMessage );

		// Server -> Client (SessionInitResponse sent)
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_SENT, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof SessionInitResponseIMessage );

		// Server -> Client (SessionInitResponse received)
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_RECEIVED, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
//		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof SessionInitResponseIMessage );

		// Make sure there are no more messages
		pair = getNextMessage( client_listener, 500 );
		assertNull( pair );
		pair = getNextMessage( server_listener, 500 );
		assertNull( pair );


		// Make a method call

		server_instance.getLocalRegistry().bind( "lib/test",
			new Runnable() {
				@Override
				public void run() {
					// nothing to see here
				}
			} );

		// NOTE: this doesn't generate 
		Registry registry =
			client_instance.getRemoteRegistry( server_instance.getLocalVMID() );

		Runnable proxy = ( Runnable ) registry.lookup( "lib/test" );


		// Check for connection messages

		// Client -> Server (SessionInit sent)
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_SENT, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof InvokeIMessage );

		// Client -> Server (SessionInit received)
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_RECEIVED, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof InvokeIMessage );

		// Server -> Client (SessionInitResponse sent)
		pair = getNextMessage( server_listener, 2 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_SENT, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof InvokeReturnIMessage );

		// Server -> Client (SessionInitResponse received)
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_RECEIVED, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof InvokeReturnIMessage );

		// Make sure there are no more messages
		pair = getNextMessage( client_listener, 500 );
		assertNull( pair );
		pair = getNextMessage( server_listener, 500 );
		assertNull( pair );


		// Method call
		proxy.run();


		// Check for connection messages

		// Client -> Server (SessionInit sent)
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_SENT, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof InvokeIMessage );

		// Client -> Server (SessionInit received)
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_RECEIVED, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof InvokeIMessage );

		// Server -> Client (SessionInitResponse sent)
		pair = getNextMessage( server_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_SENT, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
		assertEquals( client_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof InvokeReturnIMessage );

		// Server -> Client (SessionInitResponse received)
		pair = getNextMessage( client_listener, 2000 );
		assertNotNull( pair );
		assertEquals( CallType.MESSAGE_RECEIVED, pair.getOne() );
		assertEquals( 2, pair.getTwo().size() );
		assertEquals( server_instance.getLocalVMID(), pair.getTwo().get( 0 ) );
		assertTrue( pair.getTwo().get( 1 ) instanceof InvokeReturnIMessage );

		// Make sure there are no more messages
		pair = getNextMessage( client_listener, 500 );
		assertNull( pair );
		pair = getNextMessage( server_listener, 500 );
		assertNull( pair );
	}


	public void testPerformanceControlArtificialDelay() throws Exception {
		
	}


	private Pair<CallType,List<Object>> getNextMessage(
		TestPerformanceListener listener, long milliseconds )
		throws InterruptedException {

		long start = System.currentTimeMillis();
		Pair<CallType,List<Object>> pair =
			listener.queue.poll( milliseconds, TimeUnit.MILLISECONDS );
		if ( pair == null ) return null;
		
		if ( pair.getOne() == CallType.MESSAGE_SENT ||
			pair.getOne() == CallType.MESSAGE_RECEIVED ) {
			IMessage message = ( IMessage ) pair.getTwo().get( 1 );

			// Ignore lease messages
			if ( message instanceof LeaseIMessage ||
				message instanceof LeaseReleaseIMessage ) {

				long duration = System.currentTimeMillis() - start;
				return getNextMessage( listener, milliseconds - duration );
			}
		}

		return pair;
	}


	private enum CallType {
		REMOTE_CALL_STARTED,
		REMOTE_CALL_COMPLETED,
		INBOUND_REMOTE_CALL_STARTED,
		INBOUND_REMOTE_CALL_COMPLETED,
		VIRTUAL_CHANNEL_OPENED,
		VIRTUAL_CHANNEL_CLOSED,
		VIRTUAL_CHANNEL_DATA_SENT,
		VIRTUAL_CHANNEL_DATA_RECEIVED,
		MESSAGE_RECEIVED,
		MESSAGE_SENT
	}


	private class TestPerformanceListener implements PerformanceListener {
		public final BlockingQueue<Pair<CallType,List<Object>>> queue =
			new LinkedBlockingQueue<Pair<CallType,List<Object>>>();

		private final String id;
		private final boolean want_messages;

		TestPerformanceListener( String id ) {
			this( id, false );
		}

		TestPerformanceListener( String id, boolean want_messages ) {
			this.id = id;
			this.want_messages = want_messages;
		}


		@Override
		public void remoteCallStarted( VMID instance_vmid, long time, int call_id,
			VMID destination_vmid, int object_id, int method_id, Method method,
			Object[] args, UserContextInfo user_context, String persistent_name ) {

			if ( want_messages ) return;

			queue.add( Pair.create( CallType.REMOTE_CALL_STARTED, Arrays.asList(
				instance_vmid, Long.valueOf( time ), Integer.valueOf( call_id ),
				destination_vmid, Integer.valueOf( object_id ),
				Integer.valueOf( method_id ), method, args, user_context,
				persistent_name ) ) );
		}

		@Override
		public void remoteCallCompleted( VMID instance_vmid, long time, int call_id,
			Object result, boolean result_was_thrown, Long server_time ) {

			if ( want_messages ) return;

			queue.add( Pair.create( CallType.REMOTE_CALL_COMPLETED, Arrays.asList(
				instance_vmid, Long.valueOf( time ), Integer.valueOf( call_id ), result,
				Boolean.valueOf( result_was_thrown ), server_time ) ) );
		}

		@Override
		public void inboundRemoteCallStarted( VMID instance_vmid, long time, int call_id,
			VMID source_vmid, int object_id, int method_id, Method method, Object[] args,
			UserContextInfo user_context, String persistent_name ) {

			if ( want_messages ) return;

			queue.add( Pair.create( CallType.INBOUND_REMOTE_CALL_STARTED, Arrays.asList(
				instance_vmid, Long.valueOf( time ), Integer.valueOf( call_id ),
				source_vmid, Integer.valueOf( object_id ), Integer.valueOf( method_id ),
				method, args, user_context, persistent_name ) ) );
		}

		@Override
		public void inboundRemoteCallCompleted( VMID instance_vmid, long time,
			int call_id, Object result, boolean result_was_thrown ) {

			if ( want_messages ) return;

			queue.add( Pair.create( CallType.INBOUND_REMOTE_CALL_COMPLETED, Arrays.asList(
				instance_vmid, Long.valueOf( time ),
				Integer.valueOf( call_id ), result,
				Boolean.valueOf( result_was_thrown ) ) ) );
		}

		@Override
		public void virtualChannelOpened( VMID instance_vmid, VMID peer_vmid,
			short channel_id ) {

			if ( want_messages ) return;

			queue.add( Pair.create( CallType.VIRTUAL_CHANNEL_OPENED,
				Arrays.asList(
				( Object ) instance_vmid, peer_vmid, Short.valueOf( channel_id ) ) ) );
		}

		@Override
		public void virtualChannelClosed( VMID instance_vmid, VMID peer_vmid,
			short channel_id ) {

			if ( want_messages ) return;

			queue.add( Pair.create( CallType.VIRTUAL_CHANNEL_CLOSED,
				Arrays.asList(
				( Object ) instance_vmid, peer_vmid, Short.valueOf( channel_id ) ) ) );
		}

		@Override
		public void virtualChannelDataReceived( VMID instance_vmid, VMID peer_vmid,
			short channel_id, int bytes ) {

			if ( want_messages ) return;

			queue.add( Pair.create( CallType.VIRTUAL_CHANNEL_DATA_RECEIVED,
				Arrays.asList( ( Object ) instance_vmid, peer_vmid,
				Short.valueOf( channel_id ), Integer.valueOf( bytes ) ) ) );
		}



		@Override
		public void virtualChannelDataSent( VMID instance_vmid, VMID peer_vmid,
			short channel_id, short message_id, int bytes ) {

			if ( want_messages ) return;

			queue.add( Pair.create( CallType.VIRTUAL_CHANNEL_DATA_SENT,
				Arrays.asList( instance_vmid, peer_vmid,
				Short.valueOf( channel_id ), Integer.valueOf( bytes ) ) ) );
		}

		@Override
		public void messageReceived( VMID source_vmid, IMessage message ) {
			if ( !want_messages ) return;
			queue.add( Pair.create( CallType.MESSAGE_RECEIVED,
				Arrays.asList( source_vmid, message ) ) );
		}

		@Override
		public void messageSent( VMID destination_vmid, IMessage message ) {
			if ( !want_messages ) return;
			queue.add( Pair.create( CallType.MESSAGE_SENT,
				Arrays.asList( destination_vmid, message ) ) );
		}

		@Override
		public void leasedObjectRemoved( VMID vmid, int object_id ) {}

		@Override
		public void leaseInfoUpdated( VMID vmid, int object_id, String delegate_tostring,
			boolean holding_strong_reference, int leasing_vm_count, boolean renew,
			boolean release ) {}
	}
}
