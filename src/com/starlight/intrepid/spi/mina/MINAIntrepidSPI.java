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

package com.starlight.intrepid.spi.mina;

import com.starlight.IOKit;
import com.starlight.ValidationKit;
import com.starlight.intrepid.ConnectionListener;
import com.starlight.intrepid.PerformanceListener;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.exception.ConnectionFailureException;
import com.starlight.intrepid.exception.NotConnectedException;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.SessionCloseIMessage;
import com.starlight.intrepid.spi.*;
import com.starlight.locale.FormattedTextResourceKey;
import com.starlight.thread.ScheduledExecutor;
import com.starlight.thread.ThreadKit;
import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.polling.AbstractPollingIoConnector;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionInitializer;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolEncoderException;
import org.apache.mina.filter.compression.CompressionFilter;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.SocketAcceptor;
import org.apache.mina.transport.socket.SocketConnector;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 *
 */
public class MINAIntrepidSPI implements IntrepidSPI, IoHandler {
	private static final Logger LOG = LoggerFactory.getLogger( MINAIntrepidSPI.class );

	private static final long SEND_MESSAGE_SESSION_CONNECT_TIMEOUT =
		Long.getLong( "intrepid.spi.mina.send_message_connect_timeout", 11000 ).longValue();

	private static final long RECONNECT_RETRY_INTERVAL =
		Long.getLong( "intrepid.spi.mina.reconnect_retry", 5000 ).longValue();


	////////////////////////////////
	// Attributes

	// User attachment from connect()
	static final String ATTACHMENT_KEY = ".attachment";
	// ConnectionArgs from connect()
	static final String CONNECTION_ARGS_KEY = ".connection_args";
	// If the session was locally initiated, this will contain the SessionContainer object
	// for the session.
	static final String CONTAINER_KEY = ".container";
	// Time (System.nanoTime()) at which the session was created
	static final String CREATED_TIME_KEY = ".created_time";
	// Byte value for the invoke ack rate (in seconds)
	static final String INVOKE_ACK_RATE = ".invoke_ack_rate";
	// Boolean indicating whether or not we initiated the connection. Null indicates false.
	static final String LOCAL_INITIATE_KEY = ".local_initiate";
	// Boolean indicating that the session has been closed locally. Null indicates false.
	static final String LOCAL_TERMINATE_KEY = ".local_terminate";
	// Protocol version of the session (negotiated in the initial session handshake).
	static final String PROTOCOL_VERSION_KEY = ".protocol_version";
	// Reconnect token provided from SessionInitResponseIMessage
	static final String RECONNECT_TOKEN_KEY = ".reconnect_token";
	// Timer that will regenerate reconnect tokens.
	static final String RECONNECT_TOKEN_REGENERATION_TIMER = ".reconnect_token_regen_timer";
	// Server port for the remote peer
	static final String SERVER_PORT_KEY = ".server_port";
	// SessionInfo for the session.
	static final String SESSION_INFO_KEY = ".session_info";
	// UserContextInfo from AuthenticationHandler for the session.
	static final String USER_CONTEXT_KEY = ".user_context";
	// VMID object for the session.
	static final String VMID_KEY = ".vmid";
	// Slot for VMID to be set into when opening a connection as a client.
	static final String VMID_FUTURE_KEY = ".vmid_future";

	private static final IOException RECONNECT_TIMEOUT_EXCEPTION =
		new IOException( "Timeout during reconnect" );

	private final boolean enable_compression;
	private final SSLConfig ssl_config;

	private String connection_type_description;

	private InboundMessageHandler message_handler;
	private ConnectionListener connection_listener;
	private PerformanceListener performance_listener;
	private UnitTestHook unit_test_hook;
	private ScheduledExecutor thread_pool;
	private VMID local_vmid;

	private SocketAcceptor acceptor;
	private SocketConnector connector;

	// Lock for session_map, outbound_session_map and vmid_remap
	private final Lock map_lock = new ReentrantLock();

	private final Map<VMID,SessionContainer> session_map =
		new HashMap<VMID,SessionContainer>();

	// Map containing information about outbound_session_map sessions (sessions opened
	// locally). There session are managed for automatic reconnection.
	private final Map<HostAndPort,SessionContainer> outbound_session_map =
		new HashMap<HostAndPort,SessionContainer>();

	// When a connection changes VMID's (due to reconnection), the old and new ID's are
	// put here.
	private final Map<VMID,VMID> vmid_remap = new HashMap<VMID,VMID>();

	private final DelayQueue<ReconnectRunnable> reconnect_delay_queue =
		new DelayQueue<ReconnectRunnable>();
	private final ConcurrentHashMap<HostAndPort,HostAndPort> active_reconnections =
		new ConcurrentHashMap<HostAndPort,HostAndPort>();
	private final ReconnectManager reconnect_manager;

	private long reconnect_retry_interval = RECONNECT_RETRY_INTERVAL;

	private volatile Long message_send_delay =
		Long.getLong( "intrepid.spi.mina.message_send_delay" );


	/**
	 * Create an instance with compression and SSL disabled.
	 */
	public MINAIntrepidSPI() {
		this( false, null );
	}

	/**
	 * Create an instance with the given parameters.
	 *
	 * @param enable_compression		If true, compression will be enabled.
	 * @param ssl_config				If non-null, SSL will be enabled with the given
	 * 									parameters.
	 */
	public MINAIntrepidSPI( boolean enable_compression, SSLConfig ssl_config ) {
		this.enable_compression = enable_compression;
		this.ssl_config = ssl_config;

		reconnect_manager = new ReconnectManager();

		if ( message_send_delay != null ) {
			LOG.warn( "Message send delay is active: " + message_send_delay + " ms" );
		}
	}

	@Override
	public void init( InetAddress server_address, Integer server_port, String vmid_hint,
		InboundMessageHandler message_handler, ConnectionListener connection_listener,
		ScheduledExecutor thread_pool, VMID vmid,
		ThreadLocal<VMID> deserialization_context_vmid,
		PerformanceListener performance_listener, UnitTestHook unit_test_hook )
		throws IOException {

		ValidationKit.checkNonnull( message_handler, "message_handler" );
		ValidationKit.checkNonnull( connection_listener, "connection_listener" );

		this.message_handler = message_handler;
		this.connection_listener = connection_listener;
		this.performance_listener = performance_listener;
		this.unit_test_hook = unit_test_hook;
		this.thread_pool = thread_pool;
		this.local_vmid = vmid;
		if ( ssl_config != null ) {
			if ( enable_compression ) connection_type_description = "SSL/Compress";
			else connection_type_description = "SSL";
		}
		else {
			if ( enable_compression ) connection_type_description = "Compress";
			else connection_type_description = "Plain";
		}

		IntrepidCodecFactory codec =
			new IntrepidCodecFactory( vmid, deserialization_context_vmid );

		SSLContext context;
		try {
			context = SSLContext.getInstance( "TLS" );
			context.init( null, null, null );
		}
		catch( Exception ex ) {
			LOG.error( "Unable to enable SSL", ex );
		}

		connector = new NioSocketConnector();

		if ( ssl_config != null ) {
			SslFilter ssl_filter = new SslFilter( ssl_config.getSSLContext() );
			ssl_filter.setUseClientMode( ssl_config.isUseClientMode() );
			ssl_filter.setWantClientAuth( ssl_config.isWantClientAuth() );
			ssl_filter.setNeedClientAuth( ssl_config.isNeedClientAuth() );
			ssl_filter.setEnabledCipherSuites( ssl_config.getEnabledCipherSuites() );
			ssl_filter.setEnabledProtocols( ssl_config.getEnabledProtocols() );
			connector.getFilterChain().addLast( "ssl", ssl_filter );
		}

		if ( enable_compression ) {
			connector.getFilterChain().addLast( "compress", new CompressionFilter() );
		}
		connector.getFilterChain().addLast( "intrepid", new ProtocolCodecFilter( codec ) );
//		connector.getFilterChain().addLast( "logger", new LoggingFilter() );
		connector.setHandler( this );

		connector.getSessionConfig().setThroughputCalculationInterval( 1 );

		// Disable Nagle's algorithm
		connector.getSessionConfig().setTcpNoDelay( true );

		// Enable keep alive
		connector.getSessionConfig().setKeepAlive( true );

		// Make sure sockets don't linger
		connector.getSessionConfig().setSoLinger( 0 );

		if ( server_address != null || server_port != null ) {
			if ( server_port == null ) server_port = Integer.valueOf( 0 );

			acceptor = new NioSocketAcceptor();

			if ( ssl_config != null ) {
				SslFilter ssl_filter = new SslFilter( ssl_config.getSSLContext() );
				ssl_filter.setUseClientMode( ssl_config.isUseClientMode() );
				ssl_filter.setWantClientAuth( ssl_config.isWantClientAuth() );
				ssl_filter.setNeedClientAuth( ssl_config.isNeedClientAuth() );
				ssl_filter.setEnabledCipherSuites( ssl_config.getEnabledCipherSuites() );
				ssl_filter.setEnabledProtocols( ssl_config.getEnabledProtocols() );
				acceptor.getFilterChain().addLast( "ssl", ssl_filter );
			}

			if ( enable_compression ) {
				acceptor.getFilterChain().addLast( "compress", new CompressionFilter() );
			}
			acceptor.getFilterChain().addLast( "intrepid",
				new ProtocolCodecFilter( codec ) );
//			acceptor.getFilterChain().addLast( "logger", new LoggingFilter() );
			acceptor.setHandler( this );

			// Disable Nagle's algorithm
			acceptor.getSessionConfig().setTcpNoDelay( true );

			// Enable keep alive
			acceptor.getSessionConfig().setKeepAlive( true );

			// Make sure sockets don't linger
			acceptor.getSessionConfig().setSoLinger( 0 );

			if ( server_port.intValue() <= 0 ) acceptor.bind();
			else {
				acceptor.bind(
					new InetSocketAddress( server_address, server_port.intValue() ) );
			}
		}

		reconnect_manager.start();
	}


	/**
	 * Local address for the server socket, if applicable.
	 */
	public InetSocketAddress getServerAddress() {
		if ( acceptor == null ) return null;
		return acceptor.getLocalAddress();
	}


	public void setReconnectRetryInterval( long time, TimeUnit time_unit ) {
		reconnect_retry_interval = time_unit.toMillis( time );
	}


	@Override
	public void shutdown() {
		reconnect_manager.halt();

		// Shut down all sessions. Try to do it nicely, but don't wait too long.
		map_lock.lock();
		try {
			List<CloseFuture> futures = new ArrayList<CloseFuture>( session_map.size() );

			for( SessionContainer container : session_map.values() ) {
				container.setCanceled();        // cancel reconnector

				IoSession session = container.getSession();
				if ( session != null ) {
					// Indicate that it was terminated locally.
					session.setAttribute( LOCAL_TERMINATE_KEY, Boolean.TRUE );

					SessionInfo info =
						( SessionInfo ) session.getAttribute( SESSION_INFO_KEY );

					SessionCloseIMessage message = new SessionCloseIMessage();
					session.write( message );
					performance_listener.messageSent(
						info == null ? null : info.getVMID(), message );
					futures.add( session.close( false ) );
				}
			}

			for( CloseFuture future : futures ) {
				future.awaitUninterruptibly( 100 );
				if ( !future.isClosed() ) {
					CloseFuture immediate_future = future.getSession().close( true );
					immediate_future.awaitUninterruptibly( 500 );
				}
			}
		}
		finally {
			map_lock.unlock();
		}


		if ( acceptor != null ) {
			acceptor.dispose( true );
			acceptor = null;
		}
		if ( connector != null ) {
			connector.dispose( true );
			connector = null;
		}
	}


	@Override
	public VMID connect( InetAddress address, int port, ConnectionArgs args,
		Object attachment, long timeout, TimeUnit timeout_unit, boolean keep_trying )
		throws IOException {

		ValidationKit.checkGreaterThan( port, 0, "port" );

		if ( timeout_unit == null ) timeout_unit = TimeUnit.MILLISECONDS;

		final HostAndPort host_and_port = new HostAndPort( address, port );
		SessionContainer container;

		// Make sure we don't already have a connection for that host/port
		final boolean already_had_container;
		map_lock.lock();
		try {
			container = outbound_session_map.get( host_and_port );

			if ( container == null ) {
				// If we didn't find a container, see if there is an existing connection
				// to the given host that resides on the given server port.

				boolean found_container = false;
				for( Map.Entry<VMID,SessionContainer> entry : session_map.entrySet() ) {
					HostAndPort entry_hap = entry.getValue().getHostAndPort();
					if ( entry_hap == null ) continue;

					IoSession session = entry.getValue().getSession();
					if ( session == null ) continue;

					SessionInfo session_info =
						( SessionInfo ) session.getAttribute( SESSION_INFO_KEY );
					if ( session_info == null ) continue;

					Integer server_port = session_info.getPeerServerPort();
					if ( server_port == null ) continue;

					if ( host_and_port.equals( entry_hap.getHost(),
						server_port.intValue() ) ) {

						outbound_session_map.put( host_and_port, entry.getValue() );
						container = entry.getValue();
						found_container = true;
						break;
					}
				}

				if ( !found_container ) {
					container = new SessionContainer( host_and_port, args );
					outbound_session_map.put( host_and_port, container );
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
			IoSession session = container.getSession( timeout_unit.toMillis( timeout ) );
			if ( session == null ) {
				// Mark outstanding connections dead (because we'll clean the map in
				// a moment)
				container.setCanceled();

				// Clean up outbound session map to make sure we do a full connect the
				// next time through.
				map_lock.lock();
				try {
					outbound_session_map.remove( host_and_port );
				}
				finally {
					map_lock.unlock();
				}

				throw new ConnectException( "Connect timed out (waiting for session): " +
					host_and_port +
					"  (timeout was " + timeout_unit.toMillis( timeout ) + ")" );
			}

			// NOTE: session won't be set in the container until after the VMID attribute
			//       is set, so it's safe to use this rather than the VMID future.
			VMID session_vmid = ( VMID ) session.getAttribute( VMID_KEY );
			assert session_vmid != null;
			assert session_map.containsKey( session_vmid );
			return session_vmid;
		}

		boolean abend = true;
		try {
			long remaining = timeout_unit.toNanos( timeout );

			IOException exception = null;

			connection_listener.connectionOpening( address, port, attachment, args,
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

					for( int i = 0; i < 3; i++ ) {
						try {
							VMID vmid = inner_connect( address, port, args, null,
								attachment, remaining, container, null );
							if ( vmid != null ) {
								abend = false;
								return vmid;
							}
							break;
						}
						catch( IOException ex ) {
							exception = ex;

							// If the exception has a cause and it's an instance of
							// MINA's RuntimeIoException, try again. This is trying to
							// work around a problem in the JDK where this exception is
							// received when setting keep alive and nodelay flags:
							//   Caused by: org.apache.mina.core.RuntimeIoException:
							//     java.net.SocketException: Invalid argument:
							//     no further information
							//     at ...NioSocketSession$SessionConfigImpl.setKeepAlive(NioSocketSession.java:133)
							if ( ex.getCause() != null &&
								ex.getCause() instanceof RuntimeIoException ) {

								// continue
							}
							else break;
						}
					}
				}
				finally {
					remaining -= System.nanoTime() - start;

					if ( abend ) {
						connection_listener.connectionOpenFailed( address, port,
							attachment, exception, remaining > 0 );
					}
				}
			}

			if ( exception == null ) {
				exception = new ConnectException( "Connect timed out: " + host_and_port );
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
					SessionContainer pulled_container =
						outbound_session_map.remove( host_and_port );
					if ( pulled_container != null && pulled_container == container ) {
						// Make sure it doesn't have a VMID, and clean up if it does
						IoSession session = pulled_container.getSession();

						if ( session != null ) {
							CloseHandler.close( session );

							VMID vmid = ( VMID ) session.getAttribute( VMID_KEY );
							if ( vmid != null ) {
								SessionContainer session_map_container =
									session_map.remove( vmid );

								if ( session_map_container != pulled_container ) {
									IoSession session_map_session =
										session_map_container.getSession();
									CloseHandler.close( session_map_session );
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


	private VMID inner_connect( InetAddress address, int port, ConnectionArgs args,
		Serializable reconnect_token, Object attachment, long timeout_ns,
		SessionContainer container, VMID original_vmid )
		throws IOException, InterruptedException {

		if ( connector == null ) throw new ClosedChannelException();

		long nano_time = System.nanoTime();
		// NOTE: slot initializer ensures expected attributes are present
        if ( LOG.isTraceEnabled() ) {
            LOG.trace( "MINA.inner_connection: {}:{}", address, Integer.valueOf( port ) );
        }
		ConnectFuture future = connector.connect(
			new InetSocketAddress( address, port ),
			new VMIDSlotInitializer<ConnectFuture>( args, reconnect_token, container,
				attachment, original_vmid ) );
		if ( !future.await( timeout_ns, TimeUnit.NANOSECONDS ) ) {
			future.cancel();

			// TODO: better way to do this??
			// HACK ALERT: In situations where the connection times out, there can be a
			//             file descriptor leak because MINA doesn't seem to close the
			//             handler (SocketChannel) when the ConnectFuture is canceled.
			//             So, we forcibly close it here to make sure it's closed no
			//             matter what MINA decides to do with it.
			if ( future instanceof AbstractPollingIoConnector.ConnectionRequest ) {
				Object handle =
					( ( AbstractPollingIoConnector.ConnectionRequest ) future ).getHandle();
				if ( handle instanceof SocketChannel ) {
					IOKit.close( ( SocketChannel ) handle );
				}
			}
			return null;
		}

		nano_time = System.nanoTime() - nano_time;

		Throwable t = future.getException();
		if ( t != null ) {
			if ( t instanceof IOException ) throw ( IOException ) t;
			else throw new IOException( "Unable to connect due to error", t );
		}

		boolean abend = true;
		IoSession session = future.getSession();
		try {
			// Wait for the VMID to be set
			VMIDFuture vmid_future =
				( VMIDFuture ) session.getAttribute( VMID_FUTURE_KEY );
			if ( !vmid_future.await( Math.max( 0, timeout_ns - nano_time ),
				TimeUnit.NANOSECONDS ) ) {

				// Force the session closed to make sure we don't get stuck
				session.setAttribute( LOCAL_TERMINATE_KEY, Boolean.TRUE );
				CloseHandler.close( session );

				return null;
			}

			t = vmid_future.getException();
			if ( t != null ) {
				if ( t instanceof IOException ) throw ( IOException ) t;
				else throw new IOException( "Unable to connect due to error", t );
			}

			abend = false;
			return vmid_future.getVMID();
		}
		finally {
			// If we abnormally exit and have a session, close it
			if ( abend && session != null ) {
				CloseHandler.close( session );
			}
		}
	}


	@Override
	public void disconnect( VMID vmid ) {
		SessionContainer container;

        LOG.debug( "MINA.disconnect: {}", vmid );

		map_lock.lock();
		try {
			container = session_map.remove( vmid );

			// Find any remaped VMID's that point to this one
			// TODO: possibly keep this around?
			Iterator<VMID> it = vmid_remap.values().iterator();
			while( it.hasNext() ) {
				if ( it.next().equals( vmid ) ) it.remove();
			}

			if ( container != null ) {
				outbound_session_map.remove( container.getHostAndPort() );
			}
		}
		finally {
			map_lock.unlock();
		}

		if ( container == null ) return;

		container.setCanceled();        // cancel reconnector

		IoSession session = container.getSession();
		if ( session == null ) return;

		// Indicate that it was terminated locally.
		session.setAttribute( LOCAL_TERMINATE_KEY, Boolean.TRUE );

		// Notify listeners
		InetSocketAddress address = ( InetSocketAddress ) session.getRemoteAddress();
		connection_listener.connectionClosed( address.getAddress(), address.getPort(),
			local_vmid, vmid, session.getAttribute( ATTACHMENT_KEY ), false );

		IMessage message =
			new SessionCloseIMessage( Resources.USER_INITIATED_DISCONNECT, false );
		session.write( message );
		performance_listener.messageSent( vmid, message );
		CloseHandler.close( session, 2000 );
	}


	@Override
	public boolean hasConnection( VMID vmid ) {
		map_lock.lock();
		try {
			boolean has_connection = session_map.containsKey( vmid );

            LOG.debug( "MINA.hasConnection({}): {}", vmid,
                Boolean.valueOf( has_connection ) );

			return has_connection;
		}
		finally {
			map_lock.unlock();
		}
	}


	@Override
	public SessionInfo sendMessage( VMID destination, IMessage message,
		AtomicInteger protocol_version_slot ) throws IOException {

		Integer message_id = Integer.valueOf( System.identityHashCode( message ) );
		LOG.debug( "Send message (ID:{}): ", message_id, message );

		VMID new_vmid;

		// If there's an artificial delay, sleep now
		if ( message_send_delay != null ) {
			ThreadKit.sleep( message_send_delay.longValue() );
		}

		SessionContainer container;
		map_lock.lock();
		try {
			new_vmid = vmid_remap.get( destination );
			if ( new_vmid != null ) destination = new_vmid;

			container = session_map.get( destination );

			// No container means not connected and not trying to connect
			if ( container == null ) {
				if ( LOG.isDebugEnabled() ) {
					LOG.debug( "Container not found for {} in sendMessage. " +
						"Session map: {}  Outbound session map: {}  VMID remap: {}  " +
						"Reconnect delay queue: {}  Active reconnections: {}",
						destination, session_map, outbound_session_map,
						vmid_remap, reconnect_delay_queue, active_reconnections );
				}
				throw new NotConnectedException( destination );
			}
		}
		finally {
			map_lock.unlock();
		}

		IoSession session = container.getSession( SEND_MESSAGE_SESSION_CONNECT_TIMEOUT );
		if ( session == null ) throw new NotConnectedException( destination );

		SessionInfo session_info = ( SessionInfo ) session.getAttribute( SESSION_INFO_KEY );


		// See if there's a test hook that would like to drop the message
		if ( unit_test_hook != null &&
			unit_test_hook.dropMessageSend( destination, message ) ) {

			LOG.info( "Dropping message send per UnitTestHook instructions: {} to {}",
				message, destination );
			return session_info;
		}


		if ( protocol_version_slot != null ) {
			Byte protocol_version = session_info.getProtocolVersion();
			if ( protocol_version != null ) {
				protocol_version_slot.set( protocol_version.byteValue() & 0xFF );
			}
			else {
				protocol_version_slot.set( -1 );
			}
		}

//		System.err.println( "Sending message to " + destination + ": " + message );

		// Write the message and wait for it to be sent.
		WriteFuture future = session.write( message );
		LOG.debug( ">>>  return from session.write: {}  Waiting...", message_id );
		try {
			future.await();
		}
		catch( InterruptedException ex ) {
			throw new InterruptedIOException(
				"Interrupted while waiting for message write" );
		}
		LOG.debug( ">>> return from future.await: {}", message_id );

		Throwable exception = future.getException();
		if ( exception != null ) {
			LOG.debug( ">>> exception for {}: ", message, exception );
			if ( exception instanceof ProtocolEncoderException ) {
				Throwable cause = exception.getCause();
				if ( cause instanceof IOException ) {
					throw ( IOException ) cause;
				}
				else if ( cause != null ) throw new IOException( cause );
				else throw new IOException( exception );
			}
			if ( exception instanceof IOException ) throw ( IOException ) exception;
			else throw new IOException( exception );
		}
		else performance_listener.messageSent( destination, message );

		return ( SessionInfo ) session.getAttribute( SESSION_INFO_KEY );
	}

	@Override
	public Integer getServerPort() {
		SocketAcceptor acceptor = this.acceptor;
		if ( acceptor == null ) return null;

		InetSocketAddress address = acceptor.getLocalAddress();
		if ( address == null ) return null;

		return Integer.valueOf( address.getPort() );
	}

	@Override
	public void exceptionCaught( IoSession session, Throwable cause ) throws Exception {
        LOG.info( "MINA.exceptionCaught: {}", session, cause );

		// Make sure unexpected errors are printed
		if ( cause instanceof RuntimeException || cause instanceof Error ) {
			LOG.warn( "Unexpected exception caught", cause );
		}
		else LOG.debug( "Exception caught", cause );
	}

	@Override
	public void sessionOpened( IoSession session ) throws Exception {
        LOG.debug( "MINA.sessionOpened: {}", session );

		// Make sure the session has a container attached
		SessionContainer container =
			( SessionContainer ) session.getAttribute( CONTAINER_KEY );
		if ( container == null ) {
			InetSocketAddress address = ( InetSocketAddress ) session.getRemoteAddress();
			container = new SessionContainer(
				new HostAndPort( address.getAddress(), address.getPort() ), null );
			session.setAttribute( CONTAINER_KEY, container );

			// Can't be locally initiated
			session.setAttribute( LOCAL_INITIATE_KEY, Boolean.FALSE );

			// Install a VMID future
			session.setAttribute( VMID_FUTURE_KEY, new DefaultVMIDFuture() );
		}

		// WARNING: Don't set session in container here because the session isn't fully
		//          initialized. It needs to be done when the VMID is set because that
		//          indicates that a full handshake has happened. Previously I did it here
		//          and that cause a race condition on reconnection because the client
		//          could think the channel was ready and send a message before the
		//          server was ready, so it would be unable to send a response.

		// Install the SessionInfo wrapper
		IoSessionInfoWrapper session_info_wrapper = new IoSessionInfoWrapper( session,
			session_map, outbound_session_map, vmid_remap, map_lock, connection_listener,
			connection_type_description, local_vmid );
		session.setAttribute( SESSION_INFO_KEY, session_info_wrapper );

		IMessage message;
		try {
			message = message_handler.sessionOpened( session_info_wrapper,
				session.getService() == connector,
				( ConnectionArgs ) session.getAttribute( CONNECTION_ARGS_KEY ) );
		}
		catch ( CloseSessionIndicator close_indicator ) {
			// If there's a message, write it first
			if ( close_indicator.getReasonMessage() != null ) {
				IMessage close_message = close_indicator.getReasonMessage();
				session.write( close_message );
				performance_listener.messageSent( session_info_wrapper.getVMID(),
					close_message );
			}

			CloseHandler.close( session );
			return;
		}

		if ( message != null ) {
			session.write( message );
			performance_listener.messageSent( session_info_wrapper.getVMID(), message );
		}
	}

	@Override
	public void sessionClosed( IoSession session ) throws Exception {
        LOG.trace( "MINA.sessionClosed: {}", session );
		LOG.debug( "Session closed: {}", session.getAttribute( VMID_KEY ) );

		Boolean locally_terminated =
			( Boolean ) session.getAttribute( LOCAL_TERMINATE_KEY );
		if ( locally_terminated == null ) locally_terminated = Boolean.FALSE;

		Boolean locally_initiated =
			( Boolean ) session.getAttribute( LOCAL_INITIATE_KEY );
		if ( locally_initiated == null ) locally_initiated = Boolean.FALSE;

		final SessionContainer container =
			( SessionContainer ) session.getAttribute( CONTAINER_KEY );
		if ( container != null ) container.setSession( null );

		final VMID vmid = ( VMID ) session.getAttribute( VMID_KEY );
		final Object attachment = session.getAttribute( ATTACHMENT_KEY );

		// Kill the session reconnect token timer, if it exists
		ScheduledFuture<?> regen_timer = ( ScheduledFuture<?> ) session.getAttribute(
			RECONNECT_TOKEN_REGENERATION_TIMER );
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
		if ( container != null && container.getHostAndPort() != null ) {
			map_lock.lock();
			try {
				outbound_session_map.remove( container.getHostAndPort() );
			}
			finally {
				map_lock.unlock();
			}
		}

		// If it's locally initiated, make sure there isn't a caller waiting on it
		if ( locally_initiated.booleanValue() ) {
			VMIDFuture vmid_future =
				( VMIDFuture ) session.getAttribute( VMID_FUTURE_KEY );
			if ( vmid_future != null && !vmid_future.isDone() ) {
				vmid_future.setException(
					new IOException( "Session unexpectedly closed" ) );

				// No need to notify listeners or anything since it was never an
				// established connection.
				return;
			}
		}

		boolean reconnect = message_handler.sessionClosed(
			( SessionInfo ) session.getAttribute( SESSION_INFO_KEY ),
			locally_initiated.booleanValue(), locally_terminated.booleanValue(),
			container != null && vmid != null );
        if ( LOG.isDebugEnabled() ) {
            LOG.debug( "MINA.sessionClosed (stage 2): {} session_info: {} " +
                "locally_initiated: {} locally_terminated: {} vmid: {} attachment: {} " +
                "RECONNECT: {} container: {}", session,
	            session.getAttribute( SESSION_INFO_KEY ), locally_initiated,
	            locally_terminated, vmid, attachment, Boolean.valueOf( reconnect ),
	            container );
        }

		// If it was not locally terminated, notify listeners. Otherwise, this has already
		// been done.
		boolean send_close_updates = false;
		if ( !locally_terminated.booleanValue() && vmid != null ) {
			InetSocketAddress address = ( InetSocketAddress ) session.getRemoteAddress();
			connection_listener.connectionClosed( address.getAddress(), address.getPort(),
				local_vmid, vmid, attachment, reconnect );
			send_close_updates = true;
		}

		if ( reconnect ) {
			// TODO: make sure this is the "current" connection for this host
			map_lock.lock();
			try {
				SessionContainer test_container = session_map.get( vmid );
				boolean should_really_reconnect;
				//noinspection SimplifiableIfStatement
				if ( test_container == null ) should_really_reconnect = true;
				else {
					should_really_reconnect = test_container == container;
				}

				HostAndPort host_and_port =
					container == null ? null : container.getHostAndPort();
				if ( should_really_reconnect && container != null &&
					!container.isCanceled() && host_and_port != null ) {

					// Reset the VMIDFuture since this is used to determine when a new
					// connection is established (and the VMID might have changed).
					session.setAttribute( VMID_FUTURE_KEY, new DefaultVMIDFuture() );

					// Schedule a retry (will pretty much run immediately)
					ReconnectRunnable runnable = new ReconnectRunnable( container, vmid,
						attachment, host_and_port,
						( Serializable ) session.getAttribute( RECONNECT_TOKEN_KEY ) );
					LOG.debug( "ReconnectRunnable added to delay queue: {}", runnable );
					reconnect_delay_queue.add( runnable );
					return;
				}
				else if ( send_close_updates ) {
					InetSocketAddress address =
						( InetSocketAddress ) session.getRemoteAddress();
					connection_listener.connectionClosed( address.getAddress(),
						address.getPort(), local_vmid, vmid, attachment, false );

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
			SessionContainer test_container = session_map.get( vmid );
			if ( test_container != null && test_container == container ) {
				session_map.remove( vmid );

				if ( LOG.isDebugEnabled() ) {
					LOG.debug( "Removed {} from session_map due to close of session " +
						"({}) , container session ({})", vmid, session,
						test_container.getSession() );
				}
			}

			if ( locally_initiated.booleanValue() ) {
				InetSocketAddress peer_address =
					( ( InetSocketAddress ) session.getRemoteAddress() );
				HostAndPort search_template = new HostAndPort(
					peer_address.getAddress(), peer_address.getPort() );
				test_container = outbound_session_map.get( search_template );
				if ( test_container != null && test_container == container ) {
					outbound_session_map.remove( search_template );
				}
			}
		}
		finally {
			map_lock.unlock();
		}
	}

	@Override
	public void messageReceived( final IoSession session, Object message ) throws Exception {
		LOG.debug( "messageReceived - message class: {}", message.getClass().getName() );
		LOG.trace( "messageReceived: {}", message );

		final SessionInfo session_info =
			( SessionInfo ) session.getAttribute( SESSION_INFO_KEY );

		performance_listener.messageReceived( session_info.getVMID(),
			( IMessage ) message );

		if ( message == null ) return;


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
					( IMessage ) message );
			}
			catch( ClassCastException ex ) {
				throw new CloseSessionIndicator( new SessionCloseIMessage(
					new FormattedTextResourceKey( Resources.INVALID_MESSAGE_TYPE,
					message.getClass().getName() ), false ) );
			}
		}
		catch ( final CloseSessionIndicator close_indicator ) {
			thread_pool.execute( new Runnable() {
				@Override
				public void run() {
					// If there's a message, write it first
					try {
						if ( close_indicator.getReasonMessage() != null ) {
							IMessage close_message = close_indicator.getReasonMessage();
							WriteFuture future = session.write( close_message );

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
					VMIDFuture vmid_future =
						( VMIDFuture ) session.getAttribute( VMID_FUTURE_KEY );
					if ( vmid_future != null ) {
						if ( close_indicator.getServerReasonMessage() != null ) {
							IOException exception;
							if ( close_indicator.getReasonMessage() != null &&
								close_indicator.getReasonMessage().isAuthFailure() ) {
								
								exception = new ConnectionFailureException(
									close_indicator.getServerReasonMessage().getValue() );
							}
							else {
								exception = new IOException(
									close_indicator.getServerReasonMessage().getValue() );
							}

							vmid_future.setException( exception );
						}
						else vmid_future.setException( new IOException( "Session closed" ) );
					}

					CloseHandler.close( session );
				}
			});
			return;
		}

		// If there was a response, write it
		if ( response != null ) {
			session.write( response );
			performance_listener.messageSent( session_info.getVMID(), response );
		}
	}


	@Override
	public void sessionCreated( IoSession session ) throws Exception {
        LOG.trace( "MINA.sessionCreated: {}", session );

		session.setAttribute( CREATED_TIME_KEY, Long.valueOf( System.nanoTime() ) );
	}

	@Override
	public void sessionIdle( IoSession session, IdleStatus status ) throws Exception {
        LOG.trace( "MINA.sessionIdle: {} status: ", session, status );
	}

	@Override
	public void messageSent( IoSession session, Object message ) throws Exception {
		LOG.trace( "messageSent: {} message: {}", message );
	}


	@Override
	public void setMessageSendDelay( Long delay_ms ) {
		message_send_delay = delay_ms;
	}


	/**
	 * A session initializer that ensure expected attributes are set on locally initiated
	 * sessions.
	 */
	private static class VMIDSlotInitializer<T extends ConnectFuture>
		implements IoSessionInitializer<T> {

		private final ConnectionArgs connection_args;
		private final Serializable reconnect_token;
		private final SessionContainer container;
		private final Object attachment;
		private final VMID original_vmid;

		VMIDSlotInitializer( ConnectionArgs connection_args, Serializable reconnect_token,
			SessionContainer container, Object attachment, VMID orginal_vmid ) {

			this.connection_args = connection_args;
			this.reconnect_token = reconnect_token;
			this.container = container;
			this.attachment = attachment;
			this.original_vmid = orginal_vmid;
		}


		@Override
		public void initializeSession( IoSession session, T future ) {
			session.setAttribute( LOCAL_INITIATE_KEY, Boolean.TRUE );

			VMIDFuture vmid_future = new DefaultVMIDFuture();
			session.setAttribute( VMID_FUTURE_KEY, vmid_future );
			
			session.setAttribute( CONNECTION_ARGS_KEY, connection_args );
			session.setAttribute( RECONNECT_TOKEN_KEY, reconnect_token );
			session.setAttribute( CONTAINER_KEY, container );
			session.setAttribute( ATTACHMENT_KEY, attachment );

			// WARNING: DO NOT use setVMID() on the container
			if ( original_vmid != null ) {
				session.setAttribute( VMID_KEY, original_vmid );
				vmid_future.setVMID( original_vmid );
			}

			// WARNING: DO NOT set the session in the container here or it will be usable
			//          by callers (i.e., sending messages) before it's fully initialized.
		}
	}


	class ReconnectRunnable implements Runnable, Delayed {
		private final SessionContainer container;
		private final VMID original_vmid;
		private final Object attachment;
		private final Serializable reconnect_token;

		private final HostAndPort host_and_port;

		private volatile long next_run_time;

//		private int attempts = 0;


		ReconnectRunnable( SessionContainer container, VMID original_vmid,
			Object attachment, HostAndPort host_and_port, Serializable reconnect_token ) {

			ValidationKit.checkNonnull( container, "container" );
			ValidationKit.checkNonnull( host_and_port, "host_and_port" );

			this.container = container;
			this.original_vmid = original_vmid;
			this.attachment = attachment;
			this.reconnect_token = reconnect_token;
			this.host_and_port = host_and_port;

			// Random delay between 1 and 10 seconds for initial firing. This works around
			// "oscillation" problems with single connection negotiation where each side
			// closes the other side.
			int reconnect_delay_sec = ThreadLocalRandom.current().nextInt( 1, 10 );
			next_run_time = System.nanoTime() +
				TimeUnit.SECONDS.toNanos( reconnect_delay_sec );
		}

		@Override
		public void run() {
//			attempts++;

			// Check to see if there's already a running ReconnectRunnable for this
			// host and port. If there is, exit.
			if ( active_reconnections.putIfAbsent(
				host_and_port, host_and_port ) != null ) {

				LOG.debug( "ReconnectRunnable ({}) exiting because one is already " +
					"active: " + active_reconnections );
				return;
			}

			boolean abend = true;
			try {
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "MINA.ReconnectRunnable({}) running for {}",
                        Integer.valueOf( System.identityHashCode( this ) ), host_and_port );
                }

				if ( container.isCanceled() ) {
					LOG.debug( "MINA.ReconnectRunnable exiting because container " +
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

				VMID vmid = inner_connect( host_and_port.getHost(),
					host_and_port.getPort(), container.getConnectionArgs(),
					reconnect_token, attachment, TimeUnit.SECONDS.toNanos( 30 ),
					container, original_vmid );

				if ( container.isCanceled() ) {
					LOG.debug( "MINA.ReconnectRunnable exiting because container " +
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
					connection_listener.connectionClosed( host_and_port.getHost(),
						host_and_port.getPort(), local_vmid, null, attachment, false );
					abend = false;
					return;
				}

				if ( vmid == null ) throw RECONNECT_TIMEOUT_EXCEPTION;
			}
			catch( ClosedChannelException ex ) {
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "MINA.ReconnectRunnable({}) - {} - CHANNEL CLOSED",
	                    Integer.valueOf( System.identityHashCode( this ) ),
	                    host_and_port, ex );
                }
				// If it's closed, exit!

				// Notify listeners that we're giving up
				connection_listener.connectionClosed( host_and_port.getHost(),
					host_and_port.getPort(), local_vmid, null, attachment, false );
				abend = false;
			}
			catch( Throwable ex ) {
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "MINA.ReconnectRunnable({}) - {} - exception (will be " +
                        "rescheduled)",
	                    Integer.valueOf( System.identityHashCode( this ) ),
	                    host_and_port, ex );
                }
				if ( ex instanceof RuntimeException || ex instanceof Error ) {
					LOG.warn( "Error while reconnecting to {}",
						container.getHostAndPort(), ex );
				}
				else {
					LOG.debug( "Unable to reconnect to {}",
						container.getHostAndPort(), ex );
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
                    LOG.debug( "MINA.ReconnectRunnable({}) exiting for {} (abend={})",
	                    Integer.valueOf( System.identityHashCode( this ) ),
	                    host_and_port, Boolean.valueOf( abend ) );
                }

				active_reconnections.remove( host_and_port );

				if ( abend ) {
					// Notify listeners that we're giving up
					connection_listener.connectionClosed( host_and_port.getHost(),
						host_and_port.getPort(), local_vmid, null, attachment, false );
				}
			}
		}


		@Override
		public long getDelay( TimeUnit unit ) {
			long nano_duration = next_run_time - System.nanoTime();
			return unit.convert( nano_duration, TimeUnit.NANOSECONDS );
		}

		@Override
		public int compareTo( Delayed o ) {
			ReconnectRunnable other = ( ReconnectRunnable ) o;

			if ( next_run_time < other.next_run_time ) return -1;
			else if ( next_run_time == other.next_run_time ) return 0;
			else return 1;
		}

        @Override
        public String toString() {
            return "ReconnectRunnable to " + host_and_port + " original vmid: " +
	            original_vmid + " container: " + container;
        }
    }


	private class ReconnectManager extends Thread {
		private volatile boolean keep_going = true;

		ReconnectManager() {
			super( "MINA ReconnectManager" );
			setDaemon( true );
		}


		void halt() {
			keep_going = false;
			interrupt();
		}


		@Override
		public void start() {
			setName( "MINA ReconnectManager - " + local_vmid );
			super.start();
		}

		@Override
		public void run() {
			while( keep_going ) {
				try {
					ReconnectRunnable runnable = reconnect_delay_queue.take();
					Thread reconnect_thread =
						new Thread( runnable, "MINA Reconnect Thread: " +
						runnable.host_and_port );
                    LOG.debug( "MINA.ReconnectManager starting reconnect thread: {}",
                        runnable );
					reconnect_thread.start();
				}
				catch( InterruptedException ex ) {
					// ignore
				}
				catch( Throwable t ) {
					LOG.warn( "Error in MINA ReconnectManager", t );
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
	 * @param new_session           The new session to compare against. Can also be null
	 *                              to force <tt>old_session</tt> to be closed if it is
	 *                              non-null.
	 * @param nice_close_time_ms    The amount of time (in milliseconds) to allow the
	 *                              "nice" close to finish before forcefully closing the
	 *                              session, if the nice close hasn't completed.
	 */
	static void closeSessionIfDifferent( IoSession new_session, IoSession old_session,
		long nice_close_time_ms ) {

		if ( old_session == null || old_session == new_session ) return;

		// Indicate that it's locally terminated to prevent confusion about reconnection
		old_session.setAttribute( MINAIntrepidSPI.LOCAL_TERMINATE_KEY, Boolean.TRUE );

		CloseHandler.close( old_session, nice_close_time_ms );
	}
}
