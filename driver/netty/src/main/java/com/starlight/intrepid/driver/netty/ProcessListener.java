package com.starlight.intrepid.driver.netty;

import com.logicartisan.common.core.thread.ObjectSlot;
import com.logicartisan.common.core.thread.ScheduledExecutor;
import com.logicartisan.common.core.thread.ThreadKit;
import com.starlight.intrepid.ConnectionListener;
import com.starlight.intrepid.PerformanceListener;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.driver.CloseSessionIndicator;
import com.starlight.intrepid.driver.InboundMessageHandler;
import com.starlight.intrepid.driver.SessionInfo;
import com.starlight.intrepid.driver.UnitTestHook;
import com.starlight.intrepid.exception.ConnectionFailureException;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.SessionCloseIMessage;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

import static com.starlight.intrepid.driver.netty.NettyIntrepidDriver.*;


public class ProcessListener extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessListener.class);

    private final Map<VMID, ChannelContainer> session_map;
    private final Map<SocketAddress, ChannelContainer> outbound_session_map;
    private final Map<VMID, VMID> vmid_remap;
    private final Lock map_lock;
    private final ConnectionListener connection_listener;
    private final String connection_type_description;
    private final VMID local_vmid;
    private final InboundMessageHandler message_handler;
    private final PerformanceListener performance_listener;
    private final DelayQueue<NettyIntrepidDriver.ReconnectRunnable> reconnect_delay_queue;
    private final ScheduledExecutor thread_pool;
    private final UnitTestHook unit_test_hook;
	private final ReconnectRunnableCreator rr_creator;

    public ProcessListener(Map<VMID, ChannelContainer> session_map,
                           Map<SocketAddress, ChannelContainer> outbound_session_map,
                           Map<VMID, VMID> vmid_remap, Lock map_lock,
                           ConnectionListener connection_listener,
                           String connection_type_description, VMID local_vmid,
                           InboundMessageHandler message_handler,
                           PerformanceListener performance_listener,
                           DelayQueue<NettyIntrepidDriver.ReconnectRunnable> reconnect_delay_queue,
                           ScheduledExecutor thread_pool, UnitTestHook unit_test_hook,
						   ReconnectRunnableCreator rr_creator) {
        this.session_map = session_map;
        this.outbound_session_map = outbound_session_map;
        this.vmid_remap = vmid_remap;
        this.map_lock = map_lock;
        this.connection_listener = connection_listener;
        this.connection_type_description = connection_type_description;
        this.local_vmid = local_vmid;
        this.message_handler = message_handler;
        this.performance_listener = performance_listener;
        this.reconnect_delay_queue = reconnect_delay_queue;
        this.thread_pool = thread_pool;
        this.unit_test_hook = unit_test_hook;
		this.rr_creator = rr_creator;
    }


    @Override
	public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
        LOG.trace( "NETTY.exceptionCaught: {}", context, cause );

		// Make sure unexpected errors are printed
		if ( cause instanceof RuntimeException || cause instanceof Error ) {
			LOG.warn( "Unexpected exception caught", cause );
		}
		else LOG.debug( "Exception caught", cause );
	}

	@Override
	public void channelActive(ChannelHandlerContext context) throws Exception {
        LOG.trace( "{} channelActive: {}", local_vmid, context );

		Channel channel = context.channel();

		// Make sure the session has a container attached
		// NOTE: This path is hit for inbound connections to the server
		ChannelContainer container = channel.attr(CONTAINER_KEY).get();
		if ( container == null ) {
			// TODO: This remoteAddress call is returning null (not connected yet?)
			container = new ChannelContainer( channel.remoteAddress(), null );
			channel.attr(CONTAINER_KEY).set( container );

			// Install a VMID future
			channel.attr(VMID_SLOT_KEY).set( new ObjectSlot<>() );
		}

		// WARNING: Don't set session in container here because the session isn't fully
		//          initialized. It needs to be done when the VMID is set because that
		//          indicates that a full handshake has happened. Previously I did it here
		//          and that cause a race condition on reconnection because the client
		//          could think the channel was ready and send a message before the
		//          server was ready, so it would be unable to send a response.

		// Install the SessionInfo wrapper
		ChannelInfoWrapper session_info_wrapper = new ChannelInfoWrapper( channel,
			session_map, outbound_session_map, vmid_remap, map_lock, connection_listener,
			connection_type_description, local_vmid );
		channel.attr(SESSION_INFO_KEY).set(session_info_wrapper);

		IMessage message;
		try {
			message = message_handler.sessionOpened( session_info_wrapper,
				channel.parent() == null,		// locally initiated does not have a parent
				channel.attr(CONNECTION_ARGS_KEY).get() );
		}
		catch ( CloseSessionIndicator close_indicator ) {
			// If there's a message, write it first
			if ( close_indicator.getReasonMessage() != null ) {
				IMessage close_message = close_indicator.getReasonMessage();
				channel.writeAndFlush( close_message );
				performance_listener.messageSent( session_info_wrapper.getVMID(),
					close_message );
			}

			CloseHandler.close( channel );
			return;
		}

		if ( message != null ) {
			context.writeAndFlush( message ).addListener(future -> {
				if (future.isSuccess()) {
					performance_listener.messageSent(session_info_wrapper.getVMID(), message);
				}
			});
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext context) throws Exception {
        LOG.trace( "{} = channelInactive: {}", context );

		Channel channel = context.channel();

		LOG.debug( "{} - session closed: {} (local-init={} local-term={})",
			local_vmid,
			channel.attr( VMID_KEY ).get(),
			channel.attr(LOCAL_INITIATE_KEY).get(),
			channel.attr(LOCAL_TERMINATE_KEY).get());

		Boolean locally_terminated = channel.attr( LOCAL_TERMINATE_KEY ).get();
		if ( locally_terminated == null ) locally_terminated = Boolean.FALSE;

		Boolean locally_initiated = channel.attr( LOCAL_INITIATE_KEY ).get();
		if ( locally_initiated == null ) locally_initiated = Boolean.FALSE;

		final ChannelContainer container = channel.attr( CONTAINER_KEY ).get();
		if ( container != null ) container.setChannel( null );

		final VMID vmid = channel.attr( VMID_KEY ).get();
		final Object attachment = channel.attr( ATTACHMENT_KEY ).get();

		// Kill the session reconnect token timer, if it exists
		ScheduledFuture<?> regen_timer = channel.attr( RECONNECT_TOKEN_REGENERATION_TIMER ).get();
		if ( regen_timer != null ) {
			regen_timer.cancel( true );
		}

//		// If it has a VMID associated, see if it's the main session for the connection.
//		// If it isn't, ignore the close. This is to resolve an issue with (delayed)
//		// session close notifications that blow out the active session. See the
//		// MultiConnectTest.testSimultaneousConnections unit test.
//		if ( vmid != null ) {
//			map_lock.lock();
//			try {
//				SessionContainer active_container = session_map.get( vmid );
//				if ( active_container != null &&
//					!active_container.getSession().equals( session ) ) {
//
//					System.out.println( "Close of session (" + session +
//						") ignored because it isn't the active session (" +
//						active_container.getSession() + ") for " + vmid );
//					if ( LOG.isDebugEnabled() ) {
//						LOG.debug( "Close of session ({}) ignored because it isn't the " +
//							"active session ({}) for {}",
//							new Object[] { session, active_container.getSession(), vmid } );
//					}
//					return;
//				}
//			}
//			finally {
//				map_lock.unlock();
//			}
//		}


		// Clean up the outbound session map
		if ( container != null && container.getSocketAddress() != null ) {
			map_lock.lock();
			try {
				outbound_session_map.remove( container.getSocketAddress() );
			}
			finally {
				map_lock.unlock();
			}
		}

		// If it's locally initiated, make sure there isn't a caller waiting on it
		if (locally_initiated) {
			ObjectSlot<VmidOrBust> vmid_slot = channel.attr(VMID_SLOT_KEY).get();
			if ( vmid_slot != null ) {
				if ( vmid_slot.compareAndSet( (VmidOrBust) null,
					new VmidOrBust( new IOException( "Session unexpectedly closed" ) ) ) ) {

					// No need to notify listeners or anything since it was never an
					// established connection.
//					return;
				}
			}
		}

		boolean reconnect = message_handler.sessionClosed(
			channel.attr( SESSION_INFO_KEY ).get(),
            locally_initiated, locally_terminated,
			container != null && vmid != null );
		LOG.debug( "MINA.sessionClosed (stage 2): {} session_info: {} " +
			"locally_initiated: {} locally_terminated: {} vmid: {} attachment: {} " +
			"RECONNECT: {} container: {}", channel,
			channel.attr( SESSION_INFO_KEY ).get(),
			locally_initiated,
			locally_terminated, vmid, attachment, reconnect,
			container );

		// If it was not locally terminated, notify listeners. Otherwise, this has already
		// been done.
		boolean send_close_updates = false;
		if ( !locally_terminated && vmid != null ) {
			SocketAddress address = channel.remoteAddress();
			if ( address == null && container != null ) {
				address = container.getSocketAddress();
			}

			if ( address != null ) {
				connection_listener.connectionClosed(address, local_vmid, vmid, attachment,
						reconnect, channel.attr(USER_CONTEXT_KEY).get());
				send_close_updates = true;
			}
			else {
				LOG.warn( "Unable to notify listeners that connection closed, remote address unknown: {}",
					channel.attr( VMID_KEY ).get() );
			}
		}

		if ( reconnect ) {
			// TODO: make sure this is the "current" connection for this host
			map_lock.lock();
			try {
				ChannelContainer test_container = session_map.get( vmid );
				boolean should_really_reconnect;
				//noinspection SimplifiableIfStatement
				if ( test_container == null ) should_really_reconnect = true;
				else {
					should_really_reconnect = test_container == container;
				}

				SocketAddress socket_address =
					container == null ? null : container.getSocketAddress();
				if ( should_really_reconnect && container != null &&
					!container.isCanceled() && socket_address != null ) {

					// Reset the VMIDFuture since this is used to determine when a new
					// connection is established (and the VMID might have changed).
					channel.attr(VMID_SLOT_KEY).set( new ObjectSlot<>() );

					// Schedule a retry (will pretty much run immediately)
					NettyIntrepidDriver.ReconnectRunnable runnable = rr_creator.create(container, vmid,
						attachment, socket_address,
						channel.attr(RECONNECT_TOKEN_KEY).get());
					LOG.debug( "ReconnectRunnable added to delay queue: {}", runnable );
					reconnect_delay_queue.add( runnable );
					return;
				}
				else if ( send_close_updates ) {
					SocketAddress address = channel.remoteAddress();
					if ( address == null && container != null ) {
						address = container.getSocketAddress();
					}

					if ( address != null ) {
						connection_listener.connectionClosed(address, local_vmid, vmid,
							attachment, false, channel.attr(USER_CONTEXT_KEY).get());
					}
					// fall through...
				}
			}
			finally {
				map_lock.unlock();
			}
		}

		// Clean up the map, if the session being closed is the one we currently know
		// about for the VMID. If the session for the VMID is different, then don't
		// mess with the map.
		map_lock.lock();
		try {
			ChannelContainer test_container = session_map.get( vmid );
			if ( test_container != null && test_container == container ) {
				session_map.remove( vmid );

				if ( LOG.isDebugEnabled() ) {
					LOG.debug( "Removed {} from session_map due to close of session " +
						"({}) , container session ({})", vmid, channel,
						test_container.getChannel() );
				}
			}

			if (locally_initiated) {
				SocketAddress peer_address = channel.remoteAddress();
				SocketAddress search_template = null;
				if ( peer_address != null ) {
					if ( peer_address instanceof InetSocketAddress ) {
						search_template = new InetSocketAddress(
							((InetSocketAddress) peer_address).getAddress(),
							((InetSocketAddress) peer_address).getPort());
					}
					else {
						search_template = peer_address;
					}
				}
				else if ( container != null ){
					search_template = container.getSocketAddress();
				}

				if ( search_template != null ) {
					test_container = outbound_session_map.get(search_template);
					if (test_container != null && test_container == container) {
						outbound_session_map.remove(search_template);
					}
				}
			}
		}
		finally {
			map_lock.unlock();
		}
	}

	@Override
	public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
		LOG.trace( "channelRead @ {}: {} - {}", local_vmid, message, context );

		if ( message == null ) return;

		Channel channel = context.channel();

		final SessionInfo session_info = channel.attr( SESSION_INFO_KEY ).get();

		final boolean locally_initiated_session = Optional.ofNullable(channel.attr(LOCAL_INITIATE_KEY).get())
            .orElse(Boolean.FALSE);

		final Consumer<CloseSessionIndicator> close_handler = close_indicator -> {
			thread_pool.execute( () -> {
				// If there's a message, write it first
				try {
					if ( close_indicator.getReasonMessage() != null ) {
						IMessage close_message = close_indicator.getReasonMessage();
						ChannelFuture future = context.writeAndFlush(close_message);

						performance_listener.messageSent( session_info.getVMID(),
							close_message );

						// Wait (a bit) for the message to be sent
						future.awaitUninterruptibly( 2000 );

						ThreadKit.sleep( 500 );	// for good measure
					}
				}
				catch( Exception ex ) {
					LOG.info( "Error writing close message to {}",
						session_info.getVMID(), ex );
				}

				// If this is a locally opened connection, make sure we flag the error
				// so the caller isn't left waiting.
				ObjectSlot<VmidOrBust> vmid_future = channel.attr(VMID_SLOT_KEY).get();
				if ( vmid_future != null ) {
					if ( close_indicator.getServerReasonMessage() != null ) {
						IOException exception;
						if ( close_indicator.isAuthFailure() ) {
							exception = new ConnectionFailureException(
								close_indicator.getServerReasonMessage().orElse( null ) );
						}
						else {
							exception = new IOException(
								close_indicator.getServerReasonMessage().orElse( null ) );
						}

						vmid_future.set( new VmidOrBust( exception ) );
					}
					else vmid_future.set( new VmidOrBust( new IOException( "Session closed" ) ) );
				}

				CloseHandler.close( channel );
			} );
		};


		try {
			message_handler.validateReceivedMessage( session_info,
				( IMessage ) message, locally_initiated_session );
		}
		catch( CloseSessionIndicator close_indicator ) {
			performance_listener.invalidMessageReceived( session_info.getRemoteAddress(),
				( IMessage ) message );

			close_handler.accept( close_indicator );
			return;
		}


		performance_listener.messageReceived( session_info.getVMID(),
			( IMessage ) message );


		// See if there's a test hook that would like to drop the message
		if ( unit_test_hook != null && unit_test_hook.dropMessageReceive(
			session_info.getVMID(), ( IMessage ) message ) ) {

			LOG.info( "Dropping message receive per UnitTestHook instructions: {} from {}",
				message, session_info.getVMID() );
			return;
		}

		final IMessage response;
		try {
			try {
				response = message_handler.receivedMessage( session_info,
					( IMessage ) message, locally_initiated_session );
			}
			catch( ClassCastException ex ) {
				throw new CloseSessionIndicator( new SessionCloseIMessage(
					"Invalid message type: " + message.getClass().getName(),
					false ) );
			}
		}
		catch ( final CloseSessionIndicator close_indicator ) {
			close_handler.accept( close_indicator );
			return;
		}

		// If there was a response, write it
		if ( response != null ) {
			context.writeAndFlush( response );
			performance_listener.messageSent( session_info.getVMID(), response );
		}
	}


	@Override
	public void channelRegistered(ChannelHandlerContext ctx) {
        LOG.trace( "channelRegistered: {}", ctx );
		ctx.channel().attr( CREATED_TIME_KEY ).set( System.nanoTime());
	}
}
