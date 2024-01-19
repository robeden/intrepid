// Copyright (c) 2010 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Intrepid nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.starlight.intrepid.driver.netty;

import com.logicartisan.common.core.thread.ObjectSlot;
import com.logicartisan.common.core.thread.ScheduledExecutor;
import com.logicartisan.common.core.thread.ThreadKit;
import com.starlight.intrepid.ConnectionListener;
import com.starlight.intrepid.PerformanceListener;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.driver.InboundMessageHandler;
import com.starlight.intrepid.driver.IntrepidDriver;
import com.starlight.intrepid.driver.SessionInfo;
import com.starlight.intrepid.driver.UnitTestHook;
import com.starlight.intrepid.exception.NotConnectedException;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.SessionCloseIMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.compression.JZlibDecoder;
import io.netty.handler.codec.compression.JZlibEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;

import static java.util.Objects.requireNonNull;


/**
 *
 */
public class NettyIntrepidDriver<C extends Channel, S extends ServerChannel> implements IntrepidDriver {

	private static final Logger LOG = LoggerFactory.getLogger( NettyIntrepidDriver.class );

	private static final long SEND_MESSAGE_SESSION_CONNECT_TIMEOUT =
        Long.getLong("intrepid.driver.netty.send_message_connect_timeout", 11000);

	private static final long RECONNECT_RETRY_INTERVAL =
        Long.getLong("intrepid.driver.netty.reconnect_retry", 5000);


	////////////////////////////////
	// Attributes

	// User attachment from connect()
	static final AttributeKey<Object> ATTACHMENT_KEY = AttributeKey.newInstance(".attachment");
	// ConnectionArgs from connect()
	static final AttributeKey<ConnectionArgs> CONNECTION_ARGS_KEY = AttributeKey.newInstance(".connection_args");
	// If the session was locally initiated, this will contain the SessionContainer object
	// for the session.
	static final AttributeKey<ChannelContainer> CONTAINER_KEY = AttributeKey.newInstance(".container");
	// Time (System.nanoTime()) at which the session was created
	static final AttributeKey<Long> CREATED_TIME_KEY = AttributeKey.newInstance(".created_time");
	// Byte value for the invoke ack rate (in seconds)
	static final AttributeKey<Byte> INVOKE_ACK_RATE = AttributeKey.newInstance(".invoke_ack_rate");
	// Boolean indicating whether or not we initiated the connection. Null indicates false.
	static final AttributeKey<Boolean> LOCAL_INITIATE_KEY = AttributeKey.newInstance(".local_initiate");
	// Boolean indicating that the session has been closed locally. Null indicates false.
	static final AttributeKey<Boolean> LOCAL_TERMINATE_KEY = AttributeKey.newInstance(".local_terminate");
	// Protocol version of the session (negotiated in the initial session handshake).
	static final AttributeKey<Byte> PROTOCOL_VERSION_KEY = AttributeKey.newInstance(".protocol_version");
	// Reconnect token provided from SessionInitResponseIMessage
	static final AttributeKey<Serializable> RECONNECT_TOKEN_KEY = AttributeKey.newInstance(".reconnect_token");
	// Timer that will regenerate reconnect tokens.
	static final AttributeKey<ScheduledFuture<?>> RECONNECT_TOKEN_REGENERATION_TIMER =
		AttributeKey.newInstance( ".reconnect_token_regen_timer");
	// Server port for the remote peer
	static final AttributeKey<Integer> SERVER_PORT_KEY = AttributeKey.newInstance(".server_port");
	// SessionInfo for the session.
	static final AttributeKey<SessionInfo> SESSION_INFO_KEY = AttributeKey.newInstance(".session_info");
	// UserContextInfo from AuthenticationHandler for the session.
	static final AttributeKey<UserContextInfo> USER_CONTEXT_KEY = AttributeKey.newInstance(".user_context");
	// VMID object for the session.
	static final AttributeKey<VMID> VMID_KEY = AttributeKey.newInstance(".vmid");
	// Slot for VMID to be set into when opening a connection as a client.
	static final AttributeKey<ObjectSlot<VmidOrBust>> VMID_SLOT_KEY = AttributeKey.newInstance(".vmid_future");

	private static final IOException RECONNECT_TIMEOUT_EXCEPTION =
		new IOException( "Timeout during reconnect" );

	private final boolean enable_compression;
	private final SslContext client_ssl_context;
	private final SslContext server_ssl_context;
	private final Class<? extends Channel> client_channel_class;
	private final Map<ChannelOption,Object> client_options;
	private final EventLoopGroup client_worker_group;
	private final Class<? extends ServerChannel> server_channel_class;
	private final Map<ChannelOption,Object> server_options;
	private final EventLoopGroup server_boss_group;
	private final EventLoopGroup server_worker_group;

	private String connection_type_description;

	private InboundMessageHandler message_handler;
	private ConnectionListener connection_listener;
	private PerformanceListener performance_listener;
	private UnitTestHook unit_test_hook;
	private ScheduledExecutor thread_pool;
	private VMID local_vmid;

	private ServerBootstrap server_bootstrap;
	private ChannelFuture server_channel_future;
	private Bootstrap client_bootstrap;

	// Lock for session_map, outbound_session_map and vmid_remap
	private final Lock map_lock = new ReentrantLock();

	private final Map<VMID, ChannelContainer> session_map = new HashMap<>();

	// Map containing information about outbound_session_map sessions (sessions opened
	// locally). There session are managed for automatic reconnection.
	private final Map<SocketAddress, ChannelContainer> outbound_session_map =
		new HashMap<>();

	// When a connection changes VMID's (due to reconnection), the old and new ID's are
	// put here.
	private final Map<VMID,VMID> vmid_remap = new HashMap<>();

	private final DelayQueue<ReconnectRunnable> reconnect_delay_queue = new DelayQueue<>();
	private final ConcurrentHashMap<SocketAddress,SocketAddress> active_reconnections =
		new ConcurrentHashMap<>();

	private ReconnectManager reconnect_manager;

	private long reconnect_retry_interval = RECONNECT_RETRY_INTERVAL;

	private volatile Long message_send_delay =
		Long.getLong( "intrepid.driver.mina.message_send_delay" );


	public static <C extends Channel, S extends ServerChannel> Builder<C,S> newBuilder() {
		return new Builder<>();
	}


	public NettyIntrepidDriver() {
		this(false, null, null, null, null, null, null, null, null, null);
	}

	/**
	 * Create an instance with the given parameters.
	 *
	 * @param enable_compression		If true, compression will be enabled.
	 */
	private NettyIntrepidDriver(boolean enable_compression,
								@Nullable SslContext client_ssl_context,
								@Nullable Class<C> client_channel_class,
								@Nullable Map<ChannelOption,Object> client_options,
								@Nullable EventLoopGroup client_worker_group,
								@Nullable SslContext server_ssl_context,
								@Nullable Class<S> server_channel_class,
								@Nullable Map<ChannelOption,Object> server_options,
								@Nullable EventLoopGroup server_boss_group,
								@Nullable EventLoopGroup server_worker_group) {

		this.enable_compression = enable_compression;
		this.client_ssl_context = client_ssl_context;
		this.client_channel_class =
			client_channel_class == null ? NioSocketChannel.class : client_channel_class;
		this.client_options = client_options;
		this.client_worker_group = client_worker_group == null ? new NioEventLoopGroup() : client_worker_group;
		this.server_ssl_context = server_ssl_context;
		this.server_channel_class =
			server_channel_class == null ? NioServerSocketChannel.class : server_channel_class;
		this.server_options = server_options;
		this.server_boss_group =
			server_boss_group == null ? new NioEventLoopGroup(1) : server_boss_group;
		this.server_worker_group =
			server_worker_group == null ? new NioEventLoopGroup() : server_worker_group;

		if ( message_send_delay != null ) {
			LOG.warn( "Message send delay is active: " + message_send_delay + " ms" );
		}

		if (this.client_worker_group.isShuttingDown() || this.client_worker_group.isShutdown() ||
			this.client_worker_group.isTerminated()) {

			throw new IllegalArgumentException("Client worker group is stopped");
		}
		if (this.server_worker_group.isShuttingDown() || this.server_worker_group.isShutdown() ||
			this.server_worker_group.isTerminated()) {

			throw new IllegalArgumentException("Server worker group is stopped");
		}
	}

	@Override
	public void init( SocketAddress server_address, String vmid_hint,
		InboundMessageHandler message_handler, ConnectionListener connection_listener,
		ScheduledExecutor thread_pool, VMID vmid,
		ThreadLocal<VMID> deserialization_context_vmid,
		PerformanceListener performance_listener, UnitTestHook unit_test_hook,
		BiFunction<UUID,String,VMID> vmid_creator )
		throws IOException {

		requireNonNull( message_handler );
		requireNonNull( connection_listener );

		this.reconnect_manager = new ReconnectManager();

		this.message_handler = message_handler;
		this.connection_listener = connection_listener;
		this.performance_listener = performance_listener;
		this.unit_test_hook = unit_test_hook;
		this.thread_pool = thread_pool;
		this.local_vmid = vmid;
		if ( client_ssl_context != null ) {
			if ( enable_compression ) connection_type_description = "SSL/Compress";
			else connection_type_description = "SSL";
		}
		else {
			if ( enable_compression ) connection_type_description = "Compress";
			else connection_type_description = "Plain";
		}

		ChannelHandler handler = new ChannelHandler(deserialization_context_vmid, vmid_creator);

		client_bootstrap = new Bootstrap()
			.group(client_worker_group)
			.channel(client_channel_class)
			.handler(handler);
		if (client_options == null) {
			if (NioSocketChannel.class.equals(client_channel_class)) {
				client_bootstrap = client_bootstrap
					.option(ChannelOption.TCP_NODELAY, true)
					.option(ChannelOption.SO_KEEPALIVE, true)
					.option(ChannelOption.SO_LINGER, 0);
			}
		}
		else {
			client_options.forEach(client_bootstrap::option);
		}


		if ( server_address != null ) {
			server_bootstrap = new ServerBootstrap()
				.group(server_boss_group, server_worker_group)
             	.channel(server_channel_class)
				.childAttr(LOCAL_INITIATE_KEY, false)
				.childHandler(handler);
			if (server_options == null) {
				if (NioServerSocketChannel.class.equals(server_channel_class)) {
					server_bootstrap = server_bootstrap
						.childOption(ChannelOption.TCP_NODELAY, true)
						.childOption(ChannelOption.SO_KEEPALIVE, true)
						.childOption(ChannelOption.SO_LINGER, 0);
				}
			}
			else {
				server_options.forEach(server_bootstrap::option);
			}

			if (server_address instanceof InetSocketAddress && ((InetSocketAddress) server_address).getPort() <= 0) {
				server_channel_future = server_bootstrap.bind(0);
			}
			else {
				server_channel_future = server_bootstrap.bind(server_address);
			}
            try {
                server_channel_future.sync();
            }
			catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }

		reconnect_manager.start();
	}


	/**
	 * Local address for the server socket, if applicable.
	 */
	@Override
	public SocketAddress getServerAddress() {
		if ( server_channel_future == null ) return null;
		return server_channel_future.channel().localAddress();
	}


	public void setReconnectRetryInterval( long time, TimeUnit time_unit ) {
		reconnect_retry_interval = time_unit.toMillis( time );
	}


	@Override
	public void shutdown() {
		reconnect_manager.halt();

		if ( server_bootstrap != null ) {
			server_channel_future.channel().close().syncUninterruptibly();
			server_bootstrap = null;
		}

		// Shut down all sessions. Try to do it nicely, but don't wait too long.
		List<ChannelContainer> containers;
		map_lock.lock();
		try {
			containers = new ArrayList<>(session_map.values());
			session_map.clear();
		}
		finally {
			map_lock.unlock();
		}

		containers.forEach( container -> {
			container.setCanceled();        // cancel reconnector

			Channel channel = container.getChannel();
			if ( channel == null ) return;

			// Indicate that it was terminated locally.
			channel.attr( LOCAL_TERMINATE_KEY ).set( Boolean.TRUE );

			SessionInfo info = channel.attr( SESSION_INFO_KEY ).get();

			SessionCloseIMessage message = new SessionCloseIMessage();
			channel.writeAndFlush( message ).addListener(l ->
				performance_listener.messageSent(
					info == null ? null : info.getVMID(), message ));
			channel.close();
		});


		if (server_boss_group != null) server_boss_group.shutdownGracefully();
		if (server_worker_group != null) server_worker_group.shutdownGracefully();
		if (client_worker_group != null) client_worker_group.shutdownGracefully();

		if ( client_bootstrap != null ) {
			client_bootstrap = null;
		}
	}

	@Override
	public VMID connect( SocketAddress socket_address, ConnectionArgs args,
		Object attachment, long timeout, TimeUnit timeout_unit, boolean keep_trying )
		throws IOException {

		if ( timeout_unit == null ) timeout_unit = TimeUnit.MILLISECONDS;

		ChannelContainer container;

		// Make sure we don't already have a connection for that host/port
		final boolean already_had_container;
		map_lock.lock();
		try {
			container = outbound_session_map.get( socket_address );

			if ( container == null ) {
				// If we didn't find a container, see if there is an existing connection
				// to the given host that resides on the given server port.

				boolean found_container = false;
				for( Map.Entry<VMID, ChannelContainer> entry : session_map.entrySet() ) {
					SocketAddress entry_address = entry.getValue().getSocketAddress();
					if ( entry_address == null ) continue;

					Channel channel = entry.getValue().getChannel();
					if ( channel == null ) continue;

					SessionInfo session_info = channel.attr( SESSION_INFO_KEY ).get();
					if ( session_info == null ) continue;

					Integer server_port = session_info.getPeerServerPort();
					if ( server_port == null ) continue;

					if ( socket_address.equals( entry_address ) ) {
						outbound_session_map.put( socket_address, entry.getValue() );
						container = entry.getValue();
						found_container = true;
						break;
					}
				}

				if ( !found_container ) {
					container = new ChannelContainer( socket_address, args );
					outbound_session_map.put( socket_address, container );
					already_had_container = false;
				}
				else already_had_container = true;
			}
			else already_had_container = true;
		}
		finally {
			map_lock.unlock();
		}

		if ( already_had_container ) {
			Channel channel = container.getChannel( timeout_unit.toMillis( timeout ) );
			if ( channel == null ) {
				// Mark outstanding connections dead (because we'll clean the map in
				// a moment)
				container.setCanceled();

				// Clean up outbound session map to make sure we do a full connect the
				// next time through.
				map_lock.lock();
				try {
					outbound_session_map.remove( socket_address );
				}
				finally {
					map_lock.unlock();
				}

				throw new ConnectException( "Connect timed out (waiting for session): " +
					socket_address +
					"  (timeout was " + timeout_unit.toMillis( timeout ) + ")" );
			}

			// NOTE: session won't be set in the container until after the VMID attribute
			//       is set, so it's safe to use this rather than the VMID future.
			VMID session_vmid = channel.attr( VMID_KEY ).get();
			assert session_vmid != null;
			assert session_map.containsKey( session_vmid );
			return session_vmid;
		}

		boolean abend = true;
		try {
			long remaining = timeout_unit.toNanos( timeout );

			IOException exception = null;

			connection_listener.connectionOpening( socket_address, attachment, args,
				connection_type_description );

			boolean first = true;
			while( first || ( keep_trying && remaining > 0 ) ) {

				long start = System.nanoTime();
				try {
					if ( first ) first = false;
					else {
						// Wait a second.
						if ( !ThreadKit.sleep( 1000 ) ) {
							throw new InterruptedIOException();
						}
					}

					try {
						VMID vmid = inner_connect( socket_address, args, null,
							attachment, remaining, container, null );
						if ( vmid != null ) {
							abend = false;
							return vmid;
						}
					}
					catch( IOException ex ) {
						exception = ex;
					}
				}
				finally {
					remaining -= System.nanoTime() - start;

					if ( abend ) {
						connection_listener.connectionOpenFailed( socket_address,
							attachment, exception, remaining > 0 );
					}
				}
			}

			if ( exception == null ) {
				exception = new ConnectException(
					"Timed out while waiting for connection to " + socket_address );
			}

			throw exception;
		}
		catch ( InterruptedException e ) {
			InterruptedIOException ex = new InterruptedIOException();
			ex.initCause( e );
			throw ex;
		}
		finally {
			// If the connection failed, clean up the outbound_session_map
			if ( abend ) {
				map_lock.lock();
				try {
					ChannelContainer pulled_container =
						outbound_session_map.remove( socket_address );
					if ( pulled_container != null && pulled_container == container ) {
						// Make sure it doesn't have a VMID, and clean up if it does
						Channel channel = pulled_container.getChannel();

						if ( channel != null ) {
							CloseHandler.close( channel );

							VMID vmid = channel.attr( VMID_KEY ).get();
							if ( vmid != null ) {
								ChannelContainer session_map_container = session_map.remove( vmid );

								if ( session_map_container != pulled_container ) {
									Channel session_map_channel =
										session_map_container.getChannel();
									CloseHandler.close( session_map_channel );
								}
							}
						}
					}
				}
				finally {
					map_lock.unlock();
				}
			}
		}
	}


	private VMID inner_connect(SocketAddress socket_address, ConnectionArgs args,
							   Serializable reconnect_token, Object attachment, long timeout_ns,
							   ChannelContainer container, VMID original_vmid )
		throws IOException, InterruptedException {

		if ( client_bootstrap == null ) throw new ClosedChannelException();

		long nano_time = System.nanoTime();
		// NOTE: slot initializer ensures expected attributes are present
        LOG.trace( "inner_connect: {}", socket_address );

		ObjectSlot<VmidOrBust> vmid_slot =
			new ObjectSlot<>(original_vmid == null ? null : new VmidOrBust(original_vmid));
		ChannelFuture future = client_bootstrap.clone()
			.attr(LOCAL_INITIATE_KEY, true)
			.attr(CONNECTION_ARGS_KEY, args)
			.attr(RECONNECT_TOKEN_KEY, reconnect_token)
			.attr(CONTAINER_KEY, container)
			.attr(ATTACHMENT_KEY, attachment)
			.attr(VMID_SLOT_KEY, vmid_slot)
			.attr(VMID_KEY, original_vmid)
			.connect(socket_address);

		if ( !future.await( timeout_ns, TimeUnit.NANOSECONDS ) ) {
			future.cancel(true);
			future.channel().close();
			return null;
		}

		nano_time = System.nanoTime() - nano_time;

		Throwable t = future.cause();
		if ( t != null ) {
			if ( t instanceof IOException ) throw ( IOException ) t;
			else throw new IOException( "Unable to connect due to error", t );
		}

		Channel channel = future.channel();
		LOG.debug("{} channel opened: {}", local_vmid, channel);
		assert channel.attr(LOCAL_INITIATE_KEY).get();

		boolean abend = true;
		try {
			// Wait for the VMID to be set
            try {
                VmidOrBust vob = vmid_slot.waitForValue(
					TimeUnit.NANOSECONDS.toMillis(Math.max( 0, timeout_ns - nano_time )));
				if (vob == null) {	// timeout
					// Force the session closed to make sure we don't get stuck
					channel.attr( LOCAL_TERMINATE_KEY ).set( Boolean.TRUE );
					CloseHandler.close( channel );
					return null;
				}

				VMID vmid = vob.get();

				abend = false;
				return vmid;
            }
			catch (ExecutionException e) {
				t = e.getCause();
				if ( t instanceof IOException ) throw ( IOException ) t;
				else throw new IOException( "Unable to connect due to error", t );
            }
			catch(Throwable th ) {
				throw new IOException("Unable to connect due to error", th);
			}
		}
		finally {
			// If we abnormally exit and have a session, close it
			if ( abend ) {
				CloseHandler.close( channel );
			}
		}
	}


	@Override
	public void disconnect( VMID vmid ) {
		ChannelContainer container;

        LOG.trace( "disconnect: {}", vmid );

		map_lock.lock();
		try {
			container = session_map.remove( vmid );

			// Find any remaped VMID's that point to this one
			// TODO: possibly keep this around?
			vmid_remap.values().removeIf( vmid1 -> vmid1.equals( vmid ) );

			if ( container != null ) {
				outbound_session_map.remove( container.getSocketAddress() );
			}
		}
		finally {
			map_lock.unlock();
		}

		if ( container == null ) return;

		container.setCanceled();        // cancel reconnector

		Channel channel = container.getChannel();
		if ( channel == null ) return;

		// Indicate that it was terminated locally.
		channel.attr( LOCAL_TERMINATE_KEY ).set( Boolean.TRUE );

		// Notify listeners
		SocketAddress address = channel.remoteAddress();
		connection_listener.connectionClosed(
			address, local_vmid, vmid,
			channel.attr( ATTACHMENT_KEY ).get(), false,
            channel.attr( USER_CONTEXT_KEY ).get());

		IMessage message = new SessionCloseIMessage( "User-initiated disconnect", false );
		channel.writeAndFlush( message );
		performance_listener.messageSent( vmid, message );
		CloseHandler.close( channel, 2000 );
	}


	@Override
	public boolean hasConnection( VMID vmid ) {
		map_lock.lock();
		try {
			boolean has_connection = session_map.containsKey( vmid );

            LOG.debug( "hasConnection({}): {}", vmid,
                has_connection);

			return has_connection;
		}
		finally {
			map_lock.unlock();
		}
	}


	@Override
	public SessionInfo sendMessage( VMID destination, IMessage message,
		@Nullable IntConsumer protocol_version_consumer ) throws IOException {

		final Integer message_id;
		if ( LOG.isTraceEnabled() ) {
			message_id = System.identityHashCode(message);
			LOG.trace( "Send message (ID:{}): {}", message_id, message );
		}
		else message_id = null;


		VMID new_vmid;

		// If there's an artificial delay, sleep now
		if ( message_send_delay != null ) {
			LOG.trace("Artificial delay active for message {}: {} ms", message_id, message_send_delay);
			ThreadKit.sleep(message_send_delay);
		}

		ChannelContainer container;
		map_lock.lock();
		try {
			new_vmid = vmid_remap.get( destination );
			if ( new_vmid != null ) destination = new_vmid;

			container = session_map.get( destination );

			// No container means not connected and not trying to connect
			if ( container == null ) {
				LOG.debug( "Container not found for {} in sendMessage. " +
					"Session map: {}  Outbound session map: {}  VMID remap: {}  " +
					"Reconnect delay queue: {}  Active reconnections: {}",
					destination, session_map, outbound_session_map,
					vmid_remap, reconnect_delay_queue, active_reconnections );
				throw new NotConnectedException( destination );
			}
		}
		finally {
			map_lock.unlock();
		}

		Channel channel = container.getChannel( SEND_MESSAGE_SESSION_CONNECT_TIMEOUT );
		if ( channel == null ) throw new NotConnectedException( destination );

		SessionInfo session_info = channel.attr( SESSION_INFO_KEY ).get();


		if ( protocol_version_consumer != null ) {
			final Byte protocol_version = session_info.getProtocolVersion();
			protocol_version_consumer.accept(
				protocol_version == null ? -1 : protocol_version & 0xFF );
		}


		// See if there's a test hook that would like to drop the message
		if ( unit_test_hook != null &&
			unit_test_hook.dropMessageSend( destination, message ) ) {

			LOG.info( "Dropping message send per UnitTestHook instructions: {} to {}",
				message, destination );
			return session_info;
		}

//		System.err.println( "Sending message to " + destination + ": " + message );

		// Write the message and wait for it to be sent.
		LOG.trace(">>> write: {}", message_id);
		ChannelFuture future = channel.writeAndFlush(message);
        LOG.trace( ">>>  return from session.write: {}  Waiting...", message_id );
        try {
            future = future.sync();
        }
		catch (InterruptedException e) {
            throw new InterruptedIOException();
        }
		catch( EncoderException ex ) {
			if ( ex.getCause() instanceof IOException ) throw ( IOException ) ex.getCause();
			else throw new IOException( ex.getCause() );
		}
		LOG.trace( ">>> return from future.sync: {}", message_id );

		Throwable exception = future.cause();
		if ( exception != null ) {
			LOG.debug( ">>> exception for {}: ", message, exception );
			if ( exception instanceof IOException ) throw ( IOException ) exception;
			else throw new IOException( exception );
		}
		else performance_listener.messageSent( destination, message );

		return session_info;
	}

	@Override
	public Integer getServerPort() {
		if (server_channel_future == null) return null;
		Channel channel = server_channel_future.channel();
		if (channel == null) return null;
		SocketAddress address = channel.localAddress();
		if ( address == null ) return null;
		if ( !( address instanceof InetSocketAddress ) ) return null;
		return ( ( InetSocketAddress ) address ).getPort();
	}

	@Override
	public void setMessageSendDelay( Long delay_ms ) {
		message_send_delay = delay_ms;
	}


	class ReconnectRunnable implements DelayedRunnable {
		private final ChannelContainer container;
		private final VMID original_vmid;
		private final Object attachment;
		private final Serializable reconnect_token;

		private final SocketAddress socket_address;

		private volatile long next_run_time;

//		private int attempts = 0;


		ReconnectRunnable(ChannelContainer container, VMID original_vmid,
						  Object attachment, SocketAddress socket_address, Serializable reconnect_token ) {

			requireNonNull( container );
			requireNonNull(socket_address);

			this.container = container;
			this.original_vmid = original_vmid;
			this.attachment = attachment;
			this.reconnect_token = reconnect_token;
			this.socket_address = socket_address;

			// Random delay between 100 ms and 4 seconds for initial firing. This works
			// around "oscillation" problems with single connection negotiation where
			// each side closes the other side.
			int reconnect_delay_sec = ThreadLocalRandom.current().nextInt( 100, 4000 );
			next_run_time = System.nanoTime() +
				TimeUnit.MILLISECONDS.toNanos( reconnect_delay_sec );
		}

		@Override
		public void run() {
//			attempts++;

			// Check to see if there's already a running ReconnectRunnable for this
			// host and port. If there is, exit.
			if ( active_reconnections.putIfAbsent(
				socket_address, socket_address) != null ) {

				LOG.debug( "ReconnectRunnable exiting because one is already active: {}", active_reconnections );
				return;
			}

			final Channel channel = container.getChannel();

			boolean abend = true;
			try {
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "ReconnectRunnable({}) running for {}",
                        System.identityHashCode(this), socket_address);
                }

				if ( container.isCanceled() ) {
					LOG.debug( "ReconnectRunnable exiting because container " +
						"is canceled (before inner_connect): {} container: {}", this,
						container );

					// NOTE: this will clean the vmid_remap, which wouldn't be a good thing
					//       if this were a non-user-initiated disconnect. However, the
					//       only times this is done are: 1) at shutdown 2) at disconnect
					//       and 3) when a connection fails. 1 & 2 are both fine and 3
					//       really means the connection never happened in the first place
					//       so clearing the vmid_remap really isn't an issue.
					if ( original_vmid != null ) disconnect( original_vmid );
					return;
				}

				LOG.debug("Trying reconnect to {} original_vmid={}", socket_address, original_vmid);
				VMID vmid = inner_connect( socket_address, container.getConnectionArgs(),
					reconnect_token, attachment, TimeUnit.SECONDS.toNanos( 30 ),
					container, original_vmid );

				if ( container.isCanceled() ) {
					LOG.debug( "ReconnectRunnable exiting because container " +
						"is canceled (AFTER inner_connect): {} container: {}", this,
						container );

					// NOTE: this will clean the vmid_remap, which wouldn't be a good thing
					//       if this were a non-user-initiated disconnect. However, the
					//       only times this is done are: 1) at shutdown 2) at disconnect
					//       and 3) when a connection fails. 1 & 2 are both fine and 3
					//       really means the connection never happened in the first place
					//       so clearing the vmid_remap really isn't an issue.
					if ( vmid != null ) disconnect( vmid );
					if ( original_vmid != null ) disconnect( original_vmid );

					// Notify listeners that we're giving up
					connection_listener.connectionClosed( socket_address, local_vmid, null, attachment, false,
						channel == null ? null : channel.attr( USER_CONTEXT_KEY ).get());
					abend = false;
					return;
				}

				if ( vmid == null ) throw RECONNECT_TIMEOUT_EXCEPTION;
				abend = false;
			}
			catch( ClosedChannelException ex ) {
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "ReconnectRunnable({}) - {} - CHANNEL CLOSED",
                        System.identityHashCode(this),
						socket_address, ex );
                }
				// If it's closed, exit!

				// Notify listeners that we're giving up
				connection_listener.connectionClosed( socket_address, local_vmid, null, attachment, false,
					channel == null ? null : channel.attr( USER_CONTEXT_KEY ).get() );
				abend = false;
			}
			catch( Throwable ex ) {
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "ReconnectRunnable({}) - {} - exception (will be " +
                        "rescheduled)",
                        System.identityHashCode(this),
						socket_address, ex );
                }
				if ( ex instanceof RuntimeException || ex instanceof Error ) {
					LOG.warn( "Error while reconnecting to {}",
						container.getSocketAddress(), ex );
				}
				else {
					LOG.debug( "Unable to reconnect to {}",
						container.getSocketAddress(), ex );
				}

				// Adjust the delay time
				next_run_time =
					TimeUnit.MILLISECONDS.toNanos( reconnect_retry_interval ) +
					System.nanoTime();
				reconnect_delay_queue.add( this );
				abend = false;
			}
			finally {
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "ReconnectRunnable({}) exiting for {} (abend={})",
                        System.identityHashCode(this),
						socket_address, abend);
                }

				active_reconnections.remove(socket_address);

				if ( abend ) {
					// Notify listeners that we're giving up
					connection_listener.connectionClosed( socket_address, local_vmid, null, attachment, false,
						channel == null ? null : channel.attr( USER_CONTEXT_KEY ).get());
				}
			}
		}


		@Override
		public long getDelay( @Nonnull TimeUnit unit ) {
			long nano_duration = next_run_time - System.nanoTime();
			return unit.convert( nano_duration, TimeUnit.NANOSECONDS );
		}

		@Override
		public int compareTo( @Nonnull Delayed o ) {
			ReconnectRunnable other = ( ReconnectRunnable ) o;
            return Long.compare(next_run_time, other.next_run_time);
		}

        @Override
        public String toString() {
            return "ReconnectRunnable to " + socket_address + " original vmid: " +
	            original_vmid + " container: " + container;
        }
    }


	@FunctionalInterface
	interface ReconnectRunnableCreator<DR extends DelayedRunnable> {
		DR create(ChannelContainer container, VMID original_vmid,
				  Object attachment, SocketAddress socket_address,
				  Serializable reconnect_token);
	}

	interface DelayedRunnable extends Delayed, Runnable {}


	class ReconnectManager extends Thread {
		private volatile boolean keep_going = true;

		ReconnectManager() {
			super( "ReconnectManager" );
			setDaemon( true );
		}


		void halt() {
			keep_going = false;
			interrupt();
		}


		@Override
		public void start() {
			setName( "ReconnectManager - " + local_vmid );
			super.start();
		}

		@Override
		public void run() {
			while( keep_going ) {
				try {
					ReconnectRunnable runnable = reconnect_delay_queue.take();
					Thread reconnect_thread =
						new Thread( runnable, "Reconnect Thread: " +
						runnable.socket_address);
                    LOG.debug( "ReconnectManager starting reconnect thread: {}",
                        runnable );
					reconnect_thread.start();
				}
				catch( InterruptedException ex ) {
					// ignore
				}
				catch( Throwable t ) {
					LOG.warn( "Error in ReconnectManager", t );
				}
			}
		}
	}


	/**
	 * Compare an "old" and "new" session and close the old one if it is a different
	 * session. It will first try to close it "nicely" (allowing messages in the queue
	 * to be sent) and then close it forcefully if a nice close doesn't finish in the
	 * allotted time.
	 *
	 * @param new_channel           The new session to compare against. Can also be null
	 *                              to force <tt>old_session</tt> to be closed if it is
	 *                              non-null.
	 * @param nice_close_time_ms    The amount of time (in milliseconds) to allow the
	 *                              "nice" close to finish before forcefully closing the
	 *                              session, if the nice close hasn't completed.
	 */
	static void closeChannelIfDifferent(Channel new_channel, Channel old_channel,
										long nice_close_time_ms ) {

		if ( old_channel == null ) return;
		if ( Objects.equals( old_channel.id(), new_channel.id() ) ) return;

		LOG.debug( "Closing channel '{}' due to new channel '{}'", old_channel, new_channel );

		// Indicate that it's locally terminated to prevent confusion about reconnection
		old_channel.attr( NettyIntrepidDriver.LOCAL_TERMINATE_KEY ).set( Boolean.TRUE );

		CloseHandler.close( old_channel, nice_close_time_ms );
	}


	class ChannelHandler extends ChannelInitializer<Channel> {
		private final ThreadLocal<VMID> deserialization_context_vmid;
		private final BiFunction<UUID,String,VMID> vmid_creator;

		public ChannelHandler( @Nonnull ThreadLocal<VMID> deserialization_context_vmid,
							   @Nonnull BiFunction<UUID,String,VMID> vmid_creator) {
			this.deserialization_context_vmid = deserialization_context_vmid;
			this.vmid_creator = vmid_creator;
		}

		@Override
		protected void initChannel(Channel c) {
			ChannelPipeline pipe = c.pipeline();

			SslContext ssl_context = null;
			if (c.parent() == null && client_ssl_context != null) ssl_context = client_ssl_context;
			if (c.parent() != null && server_ssl_context != null ) ssl_context = server_ssl_context;
			if (ssl_context != null) {
				pipe.addLast(ssl_context.newHandler(c.alloc()));
			}

			if (enable_compression) {
				pipe.addLast(new JZlibEncoder(), new JZlibDecoder());
			}

//			String header = server_bootstrap == null ? "<<CLIENT " : "<<SERVER ";
//			pipe.addLast(new LoggingHandler(header + "PRE-CODEC", LogLevel.INFO));
			pipe.addLast(new NettyIMessageEncoder());
			pipe.addLast(new NettyIMessageDecoder(local_vmid, deserialization_context_vmid, vmid_creator));

//			pipe.addLast(new LoggingHandler(header + "POST-CODEC", LogLevel.INFO));

			pipe.addLast(new ProcessListener<>(session_map, outbound_session_map, vmid_remap, map_lock,
				connection_listener, connection_type_description, local_vmid, message_handler,
				performance_listener, reconnect_delay_queue, thread_pool, unit_test_hook,
				ReconnectRunnable::new));
//			pipe.addLast(new LoggingHandler(header + "PIPELINE 4>>", LogLevel.INFO));

//			c.pipeline().addLast(new LoggingHandler("<<PIPELINE>>", LogLevel.INFO));

		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			LOG.trace( "NETTY.exceptionCaught: {}", ctx, cause );

			// Make sure unexpected errors are printed
			if ( cause instanceof RuntimeException || cause instanceof Error ) {
				LOG.warn( "Unexpected exception caught", cause );
			}
			else LOG.debug( "Exception caught", cause );

			super.exceptionCaught(ctx, cause);
		}
	}

	public static class Builder<C extends Channel, S extends ServerChannel> {
		private boolean enableCompression = false;
		private SslContext clientSslContext = null;
		private Class<C> clientChannelClass = null;
		private Map<ChannelOption, Object> clientOptions = null;
		private EventLoopGroup clientWorkerGroup = null;
		private SslContext serverSslContext = null;
		private Class<S> serverChannelClass = null;
		private Map<ChannelOption, Object> serverOptions = null;
		private EventLoopGroup serverBossGroup = null;
		private EventLoopGroup serverWorkerGroup = null;

		public Builder<C,S> compression(boolean enable) {
			this.enableCompression = enable;
			return this;
		}

		public Builder<C,S> clientSslContext(SslContext context) {
			this.clientSslContext = requireNonNull(context);
			return this;
		}

		public Builder<C,S> clientChannelClass(Class<C> channel) {
			this.clientChannelClass = requireNonNull(channel);
			return this;
		}

		public Builder<C,S> clientOptions(Map<ChannelOption,Object> options) {
			this.clientOptions = requireNonNull(options);
			return this;
		}

		public Builder<C,S> clientWorkerGroup(EventLoopGroup group) {
			this.clientWorkerGroup = requireNonNull(group);
			return this;
		}

		public Builder<C,S> serverSslContext(SslContext context) {
			this.serverSslContext = requireNonNull(context);
			return this;
		}

		public Builder<C,S> serverChannelClass(Class<S> channel) {
			this.serverChannelClass = requireNonNull(channel);
			return this;
		}

		public Builder<C,S> serverOptions(Map<ChannelOption,Object> options) {
			this.serverOptions = requireNonNull(options);
			return this;
		}

		public Builder<C,S> serverBossGroup(EventLoopGroup group) {
			this.serverBossGroup = requireNonNull(group);
			return this;
		}

		public Builder<C,S> serverWorkerGroup(EventLoopGroup group) {
			this.serverWorkerGroup = requireNonNull(group);
			return this;
		}

		public NettyIntrepidDriver<C,S> build() {
			return new NettyIntrepidDriver<>(enableCompression, clientSslContext,
				clientChannelClass, clientOptions, clientWorkerGroup, serverSslContext,
				serverChannelClass, serverOptions, serverBossGroup, serverWorkerGroup);
		}
	}
}
