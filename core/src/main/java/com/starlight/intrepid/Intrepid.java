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

package com.starlight.intrepid;

import com.logicartisan.common.core.listeners.ListenerSupport;
import com.logicartisan.common.core.thread.ScheduledExecutor;
import com.logicartisan.common.core.thread.SharedThreadPool;
import com.starlight.intrepid.auth.AuthenticationHandler;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.PreInvocationValidator;
import com.starlight.intrepid.driver.IntrepidDriver;
import com.starlight.intrepid.driver.NoAuthenticationHandler;
import com.starlight.intrepid.driver.UnitTestHook;
import com.starlight.intrepid.exception.ChannelRejectedException;
import com.starlight.intrepid.exception.ConnectionFailureException;
import com.starlight.intrepid.exception.IntrepidRuntimeException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import static java.util.Objects.requireNonNull;


/**
 * This class provides static functions for accessing Intrepid's main functionality.
 */
@SuppressWarnings( { "WeakerAccess", "unused" } )
public class Intrepid {
	private static final long CONNECT_TIMEOUT = Long.getLong("intrepid.connect.timeout", 10000);

	private static final Lock LOCAL_INSTANCE_MAP_LOCK = new ReentrantLock();
	private static final Map<VMID,Intrepid> LOCAL_INSTANCE_MAP = new HashMap<>();

	private static final ThreadLocal<Intrepid> THREAD_INSTANCE = new InheritableThreadLocal<>();

	private static final ListenerSupport<IntrepidInstanceListener,?> INSTANCE_LISTENERS =
		ListenerSupport.forType( IntrepidInstanceListener.class ).asynchronous().build();

	/** This is a hook for testing while allows the "inter-instance bridging" provided
	 *  by {@link #findLocalInstance(VMID,boolean)} to be disabled when trying to shortcut.
	 *  When disabled, calls to local instance will user the full stack. */
	static volatile boolean disable_inter_instance_bridge =
		System.getProperty( "intrepid.disable_inter_instance_bridge" ) != null;


	private final IntrepidDriver spi;

	private final VMID vmid;

	private final LocalCallHandler local_handler;
	private final RemoteCallHandler remote_handler;

	private final ListenerSupport<ConnectionListener,?> connection_listeners;
	private final ListenerSupport<PerformanceListener,?> performance_listeners;

	private final PerformanceControl performance_control;

	private final ListenerRegistrationManager listener_registration_manager =
		new ListenerRegistrationManager( this );


	private volatile boolean closed = false;



	public static Builder newBuilder() {
		return new Builder();
	}


	/**
	 * Indicates whether or not the given object is a proxy.
	 */
	public static boolean isProxy( Object object ) {
		return ProxyKit.isProxy( object );
	}


	/**
	 * Returns the VMID the given proxy object points to. If the object is not a proxy,
	 * null will be returned.
	 */
	public static VMID getProxyVMID( Object object ) {
		return ProxyKit.getProxyVMID( object );
	}


	/**
	 * Similar to {@link #createProxy(Object)}, but this can be done in a static context
	 * and so has some additional requirements. The method needs to be able to determine
	 * the applicable <tt>Intrepid</tt> instance. The will be done either by:
	 * <ol>
	 * <li>A thread-local instance has been set via {@link #setThreadInstance(Intrepid)}.</li>
	 * <li>This is called from within an Intrepid call, in which case that instance
	 *     will be used.</li>
	 * <li>There is one and only one active instance in the VM.</li>
	 * </ol>
	 * The second option will likely be the more typical scenario. For example, if a proxy
	 * should be contained inside a serialized object being returned from a method call,
	 * this would be suitable for use.
	 *
	 * @param delegate		The object to which the proxy will delegate.
	 *
	 * @return		A proxy object.
	 *
	 * @throws IllegalStateException	If more than one Intrepid instance is active in
	 * 									the local VM.
	 */
	@SuppressWarnings( "UnusedDeclaration" )
	public static Object staticCreateProxy( Object delegate ) {
		if ( isProxy( delegate ) ) return delegate;

		// See if there's a thread instance set
		Intrepid instance = THREAD_INSTANCE.get();

		// See if there is an active call context from which we can determine the instance
		if ( instance == null ) {
			instance = IntrepidContext.getActiveInstance();
		}

		// If not, see if there is only one active instance
		if ( instance == null ) {
			LOCAL_INSTANCE_MAP_LOCK.lock();
			try {
				if ( LOCAL_INSTANCE_MAP.size() != 1 ) {
					throw new IllegalStateException( "This method can only be used if " +
						" is one and only one instance active but there are " +
						LOCAL_INSTANCE_MAP.size() + " instances active." );
				}

				instance = LOCAL_INSTANCE_MAP.values().iterator().next();
			}
			finally {
				LOCAL_INSTANCE_MAP_LOCK.unlock();
			}
		}

		return instance.createProxy( delegate );
	}


	/**
	 * Sets the instance to be used by {@link #staticCreateProxy(Object)}. This value is
	 * inherited to child threads, so it only need be set on the parent. See the
	 * documentation for {@link #staticCreateProxy(Object)} for the algorithm used to
	 * determine the instance use if this is not set.
	 *
	 * @param instance		The instance to be used or null to clear the current value.
	 */
	public static void setThreadInstance( Intrepid instance ) {
		if ( instance == null ) THREAD_INSTANCE.remove();
		else THREAD_INSTANCE.set( instance );
	}


	/**
	 * Add a listener that will be notified when a new Intrepid instance is created.
	 */
	public static void addInstanceListener( IntrepidInstanceListener listener ) {
		INSTANCE_LISTENERS.add( listener );
	}

	/**
	 * @see #addInstanceListener(IntrepidInstanceListener)
	 */
	@SuppressWarnings( "UnusedDeclaration" )
	public static void removeInstanceListener( IntrepidInstanceListener listener ) {
		INSTANCE_LISTENERS.remove( listener );
	}


	/**
	 * Find the Intrepid instance with the VMID in the local VM.
	 *
	 * @see #disable_inter_instance_bridge
	 */
	static Intrepid findLocalInstance( VMID vmid, boolean trying_to_shortcut ) {
		if ( trying_to_shortcut && disable_inter_instance_bridge ) return null;

		LOCAL_INSTANCE_MAP_LOCK.lock();
		try {
			return LOCAL_INSTANCE_MAP.get( vmid );
		}
		finally {
			LOCAL_INSTANCE_MAP_LOCK.unlock();
		}
	}


    /**
     * Returns the instance that has a connection to the given VMID or null if none.
     */
    static Intrepid findInstanceWithRemoteSession( VMID remote_vmid ) {
	    // Check the thread-specified instance first
	    Intrepid t_instance = THREAD_INSTANCE.get();
	    if ( t_instance != null && t_instance.spi.hasConnection( remote_vmid ) ) {
		    return t_instance;
	    }

		LOCAL_INSTANCE_MAP_LOCK.lock();
		try {
            for( Intrepid instance : LOCAL_INSTANCE_MAP.values() ) {
                if ( instance.spi.hasConnection( remote_vmid ) ) return instance;
            }

            return null;
		}
		finally {
			LOCAL_INSTANCE_MAP_LOCK.unlock();
		}
    }


	private Intrepid( IntrepidDriver spi, VMID vmid, LocalCallHandler local_handler,
		RemoteCallHandler remote_handler,
		ListenerSupport<ConnectionListener,?> connection_listeners,
		ListenerSupport<PerformanceListener,?> performance_listeners ) {

		this.spi = spi;
		this.vmid = vmid;
		this.local_handler = local_handler;
		this.remote_handler = remote_handler;
		this.connection_listeners = connection_listeners;
		this.performance_listeners = performance_listeners;

		this.performance_control = new PerformanceControlWrapper();

		LOCAL_INSTANCE_MAP_LOCK.lock();
		try {
			LOCAL_INSTANCE_MAP.put( vmid, this );
		}
		finally {
			LOCAL_INSTANCE_MAP_LOCK.unlock();
		}
	}


	public void close() {
		closed = true;

		LOCAL_INSTANCE_MAP_LOCK.lock();
		try {
			LOCAL_INSTANCE_MAP.remove( vmid );
		}
		finally {
			LOCAL_INSTANCE_MAP_LOCK.unlock();
		}

		local_handler.shutdown();
		spi.shutdown();

		INSTANCE_LISTENERS.dispatch().instanceClosed( getLocalVMID() );
	}


	/**
	 * Wrap the delegate object in a proxy, if it isn't already.
	 *
	 * @param delegate		The object to which the proxy will delegate.
	 *
	 * @return		A proxy object.
	 */
	public Object createProxy( Object delegate ) {
		if ( closed ) throw new IllegalStateException( "Closed" );

		if ( isProxy( delegate ) ) return delegate;

		return local_handler.createProxy( delegate, null );
	}


	/**
	 * Indicates whether or not the given object is a local proxy.
	 *
	 * @see #isProxy(Object)
	 */
	public boolean isProxyLocal( Object object ) {
		return ProxyKit.isProxyLocal( object, vmid );
	}


	/**
	 * If the given object is a local proxy, this returns the object it delegates calls
	 * to, if available. If it is not a proxy or the proxy is not local, it returns null.
	 * <p>
	 * The delegate may not be available under certain circumstances, generally
	 * encountered during test (if the proxy has been serialized and the
	 * {@link Intrepid#disable_inter_instance_bridge inter-instance bridge} is disabled).
	 *
	 * @see #isProxy(Object)
	 * @see #isProxyLocal(Object)
	 */
	public Object getLocalProxyDelegate( Object object ) {
		return ProxyKit.getLocalProxyDelegate( object, vmid );
	}


	/**
	 * Returns the VMID the given proxy object points to. If the object is not a proxy or
	 * is local, it returns null.
	 *
	 * @see #isProxy(Object)
	 * @see #isProxyLocal(Object)
	 */
	public VMID getRemoteProxyVMID( Object object ) {
		return ProxyKit.getRemoteProxyVMID( object, vmid );
	}


	/**
	 * Return the VMID of the local VM.
	 */
	public VMID getLocalVMID() {
		return vmid;
	}


	/**
	 * Return a pointer to a remote registry.
	 */
	public Registry getRemoteRegistry( VMID vmid ) {
		if ( closed ) throw new IllegalStateException( "Closed" );

		return remote_handler.getRemoteRegistry( vmid );
	}


	/**
	 * Get the local registry.
	 */
	public LocalRegistry getLocalRegistry() {
		LocalRegistry registry = local_handler.getLocalRegistry();
		registry.setInstance( this );
		return registry;
	}


	/**
	 * Connect to remote host, throwing an exception immediately if the host is not
	 * reachable.
	 * reachable using a default timeout.
	 *
	 * @see #connect(InetAddress, int, ConnectionArgs, Object, long, TimeUnit)
	 */
	public VMID connect( InetAddress host, int port, ConnectionArgs args,
						 Object attachment ) throws IOException {

		return connect( new InetSocketAddress(host, port), args, attachment, CONNECT_TIMEOUT, TimeUnit.MILLISECONDS );
	}

	/**
	 * Connect to remote host, throwing an exception immediately if the host is not
	 * reachable.
	 * reachable using a default timeout.
	 *
	 * @see #connect(SocketAddress, ConnectionArgs, Object, long, TimeUnit)
	 */
	public VMID connect( SocketAddress address, ConnectionArgs args,
						 Object attachment ) throws IOException {

		return connect( address, args, attachment, CONNECT_TIMEOUT, TimeUnit.MILLISECONDS );
	}

	/**
	 * Connect to remote host, throwing an exception immediately if the host is not
	 * reachable. But allowing a caller provider connection timeout.
	 *
	 * @param host      	Host to connect to.
	 * @param args      	(Optional) SPI-dependant connection args.
	 * @param attachment	An object the caller can associate with the connection
	 * @param timeout		Time to wait for the connection. Note that this is a soft
	 * 						timeout, so it's guaranteed to try for at least the time given
	 * 						and not start any long operations after the time has expired.
	 * @param timeout_units Time unit for <tt>timeout</tt> argument.
	 *
	 * @return              The VMID of the remote host.
	 *
	 * @throws IOException                  Thrown if an error occurs while trying to
	 *                                      connect.
	 * @throws ConnectionFailureException   If the connection failed due to an
	 *                                      authentication/authorization failure.
	 *
	 * @see #tryConnect
	 */
	public VMID connect( InetAddress host, int port, ConnectionArgs args,
		Object attachment, long timeout, TimeUnit timeout_units ) throws IOException {

		return connect( new InetSocketAddress(host, port), args, attachment, timeout, timeout_units );
	}

	/**
	 * Connect to remote host, throwing an exception immediately if the host is not
	 * reachable. But allowing a caller provider connection timeout.
	 *
	 * @param destination   Destination for the connection.
	 * @param args      	(Optional) SPI-dependant connection args.
	 * @param attachment	An object the caller can associate with the connection
	 * @param timeout		Time to wait for the connection. Note that this is a soft
	 * 						timeout, so it's guaranteed to try for at least the time given
	 * 						and not start any long operations after the time has expired.
	 * @param timeout_units Time unit for <tt>timeout</tt> argument.
	 *
	 * @return              The VMID of the remote host.
	 *
	 * @throws IOException                  Thrown if an error occurs while trying to
	 *                                      connect.
	 * @throws ConnectionFailureException   If the connection failed due to an
	 *                                      authentication/authorization failure.
	 *
	 * @see #tryConnect
	 */
	public VMID connect( SocketAddress destination, ConnectionArgs args,
		Object attachment, long timeout, TimeUnit timeout_units ) throws IOException {

		return spi.connect( destination, args, attachment, timeout, timeout_units, false );
	}


	/**
	 * Wait for a connection to the remote host.
	 *
	 * @param host      	Host to connect to.
	 * @param args      	(Optional) SPI-dependant connection args.
	 * @param timeout		Time to wait for the connection. Note that this is a soft
	 * 						timeout, so it's guaranteed to try for at least the time given
	 * 						and not start any long operations after the time has expired.
	 * @param timeout_units Time unit for <tt>timeout</tt> argument.
	 *
	 * @return          	The VMID of the remote host. This will always be non-null.
	 * 						If the timeout is reached, an exception (indicating the most
	 * 						recent failure cause) will be thrown. 
	 *
	 * @throws IOException  Thrown if an error occurs while trying to connect.
	 */
	public VMID tryConnect( InetAddress host, int port, ConnectionArgs args,
		Object attachment, long timeout, TimeUnit timeout_units )
		throws IOException, InterruptedException {

		return tryConnect( new InetSocketAddress(host, port), args, attachment, timeout, timeout_units );
	}

	/**
	 * Wait for a connection to the remote host.
	 *
	 * @param destination   Destination to connect to.
	 * @param args      	(Optional) SPI-dependant connection args.
	 * @param timeout		Time to wait for the connection. Note that this is a soft
	 * 						timeout, so it's guaranteed to try for at least the time given
	 * 						and not start any long operations after the time has expired.
	 * @param timeout_units Time unit for <tt>timeout</tt> argument.
	 *
	 * @return          	The VMID of the remote host. This will always be non-null.
	 * 						If the timeout is reached, an exception (indicating the most
	 * 						recent failure cause) will be thrown.
	 *
	 * @throws IOException  Thrown if an error occurs while trying to connect.
	 */
	public VMID tryConnect( SocketAddress destination, ConnectionArgs args,
		Object attachment, long timeout, TimeUnit timeout_units )
		throws IOException, InterruptedException {

		requireNonNull( destination );

		if ( closed ) throw new IllegalStateException( "Closed" );

		return spi.connect( destination, args, attachment, timeout, timeout_units, true );
	}



	/**
	 * Disconnect from a remote host.
	 */
	public void disconnect( VMID host_vmid ) {
		if ( closed ) throw new IllegalStateException( "Closed" );

		requireNonNull( host_vmid );

		spi.disconnect( host_vmid );
	}


	/**
	 * Creates a virtual channel to the given destination operating over the Intrepid
	 * connection. Channels allow for higher performance data streaming than is possible
	 * with individual method calls.
	 *
	 * @param destination	The destination VM. This cannot specify the local instance.
	 * @param attachment	An optional attachment for identifying the channel to the
	 * 						server.
	 *
	 * @return				The channel, if successful. The returned channel will not
	 * 						support non-blocking mode.
	 *
	 * @throws IOException	Indicates a communication-related failure.
	 * @throws ChannelRejectedException    Indicates the channel was rejected by the server.
	 */
	public ByteChannel createChannel( VMID destination, Serializable attachment )
		throws IOException, ChannelRejectedException {

		requireNonNull( destination );

		if ( destination.equals( vmid ) ) {
			throw new IllegalArgumentException( "Destination cannot be local instance" );
		}

		return remote_handler.channelCreate( destination, attachment );
	}


	/**
	 * Returns the server port in use, if applicable.
	 */
	public Integer getServerPort() {
		return spi.getServerPort();
	}

	/**
	 * Returns the server address in use, if applicable.
	 */
	public SocketAddress getServerAddress() {
		return spi.getServerAddress();
	}


	/**
	 * Ping a remote connection to see if it's responding.
	 *
	 * @param vmid          VMID of the instance to ping.
	 * @param timeout       Time allowed for the response.
	 * @param timeout_unit  Unit for <tt>timeout</tt>.
	 *
	 * @return              The time in which the response was received.
	 *
	 * @throws TimeoutException             Thrown if the timeout expires.
	 * @throws IntrepidRuntimeException     Thrown if a communication error occurs.
	 */
	public long ping( VMID vmid, long timeout, TimeUnit timeout_unit ) throws
		TimeoutException, IntrepidRuntimeException, InterruptedException {

		return remote_handler.ping( vmid, timeout, timeout_unit );
	}


	/**
	 * Add a listener that is notified when connections are opened or closed.
	 */
	public void addConnectionListener( ConnectionListener listener ) {
		connection_listeners.add( listener );
	}

	/**
	 * Remove a {@link ConnectionListener}.
	 *
	 * @see #addConnectionListener(ConnectionListener)
	 */
	public void removeConnectionListener( ConnectionListener listener ) {
		connection_listeners.remove( listener );
	}


	/**
	 * Add a listener that is notified regarding method calls and related performance
	 * statistics.
	 *
	 * @return  A {@link PerformanceControl} object for tuning performance parameters,
	 *          generally for debugging/performance testing.
	 */
	public PerformanceControl addPerformanceListener( PerformanceListener listener ) {
		performance_listeners.add( listener );
		return performance_control;
	}

	/**
	 * Remove a {@link PerformanceListener}.
	 *
	 * @see #addPerformanceListener(PerformanceListener)
	 */
	public void removePerformanceListener( PerformanceListener listener ) {
		performance_listeners.remove( listener );
	}



	/**
	 * This method facilitates persistent connections to remote proxies by automatically
	 * re-registering a listener (or other class using a similar model) when a connection
	 * is lost and re-opened to a peer.
	 * <p>
	 * This is an example of maintaining a "FooListener" on a "Server" proxy:
	 * <pre>
	 *     Intrepid intrepid =        // Intrepid client instance
	 *     Server server_proxy =     // proxy to server
	 *
	 *     FooListener my_listener = // listener instance
	 *
	 *     intrepid.keepListenerRegistered( my_listener, server_proxy,
	 *         Server::addFooListener, Server::removeFooListener );
	 * </pre>
	 * If your listener has a defined lifetime and should be removed at some point, simply
	 * hang on to the <tt>ListenerRegistration</tt> instance returned by the
	 * <tt>keepListenerRegistered</tt> method and call the
	 * {@link ListenerRegistration#remove()} method.
	 *
	 * @param listener          The listener to be registered.
	 * @param proxy             The proxy object on which to register the listener.
	 * @param add_method        The add*Listener method.
	 * @param remove_method     Optional remove*Listener method. If null, calling
	 *                          {@link ListenerRegistration#remove()}
	 *                          will simply stop listening for connection events.
	 *
	 * @param <L>               Listener class.
	 * @param <P>               Proxy class.
	 *
	 * @return                  A registration object that allows canceling the
	 *                          registration and checking the current connection state.
	 *
	 * @throws java.lang.IllegalArgumentException       If the provided "proxy" object
	 *                          is not {@link #isProxy(Object) actually a proxy}.
	 */
	public <L,P> ListenerRegistration keepListenerRegistered( @Nonnull L listener,
		@Nonnull P proxy, @Nonnull BiConsumer<P,L> add_method,
		@Nullable BiConsumer<P,L> remove_method ) throws IllegalArgumentException {

		VMID vmid = getProxyVMID( proxy );
		if ( vmid == null ) {
			throw new IllegalArgumentException( "The \"proxy\" argument must be a proxy" );
		}

		return listener_registration_manager.keepListenerRegistered( listener, vmid,
			proxy, add_method, remove_method );
	}

	/**
	 * Identical to {@link #keepListenerRegistered(Object, Object, java.util.function.BiConsumer, java.util.function.BiConsumer)}
	 * except that it provides for handling a return value from the <tt>add_method</tt>.
	 *
	 * @param listener          The listener to be registered.
	 * @param proxy             The proxy object on which to register the listener.
	 * @param add_method        The add*Listener method which returns a value.
	 * @param remove_method     Optional remove*Listener method. If null, calling
	 *                          {@link ListenerRegistration#remove()}
	 *                          will simply stop listening for connection events.
	 * @param return_value_handler  Consumer that will be called to handle the return
	 *                          value whenever the listener is re-registered.
	 *
	 * @param <L>               Listener class.
	 * @param <P>               Proxy class.
	 * @param <R>               Return value class.
	 *
	 * @return                  A registration object that allows canceling the
	 *                          registration and checking the current connection state.
	 *
	 * @throws java.lang.IllegalArgumentException       If the provided "proxy" object
	 *                          is not {@link #isProxy(Object) actually a proxy}.
	 */
	public <L,P,R> ListenerRegistration keepListenerRegistered( @Nonnull L listener,
		@Nonnull P proxy, @Nonnull BiFunction<P,L,R> add_method,
		@Nullable BiConsumer<P,L> remove_method,
		@Nonnull Consumer<R> return_value_handler ) throws IllegalArgumentException {

		VMID vmid = getProxyVMID( proxy );
		if ( vmid == null ) {
			throw new IllegalArgumentException( "The \"proxy\" argument must be a proxy" );
		}

		return listener_registration_manager.keepListenerRegistered( listener, vmid,
			proxy, add_method, remove_method, return_value_handler );
	}


	/**
	 * Return a pointer to a local handler.
	 */
	LocalCallHandler getLocalCallHandler() {
		if ( closed ) throw new IllegalStateException( "Closed" );
		return local_handler;
	}


	/**
	 * Return a pointer to a remote handler.
	 */
	RemoteCallHandler getRemoteCallHandler() {
		if ( closed ) throw new IllegalStateException( "Closed" );
		return remote_handler;
	}


	/**
	 * Return a pointer to the SPI.
	 */
	IntrepidDriver getSPI() {
		if ( closed ) throw new IllegalStateException( "Closed" );
		return spi;
	}


	@Override
	public String toString() {
		return "Intrepid{vmid=" + vmid + '}';
	}


	private class PerformanceControlWrapper implements PerformanceControl {
		@Override
		public void setMessageSendDelay( Long delay_ms ) {
			spi.setMessageSendDelay( delay_ms );
		}
	}



	public static class Builder {
		private IntrepidDriver driver;
		private SocketAddress server_address;
		private ScheduledExecutor thread_pool = SharedThreadPool.INSTANCE;
		private AuthenticationHandler auth_handler;
		private String vmid_hint;
		private ConnectionListener connection_listener;
		private PerformanceListener performance_listener;
		private ChannelAcceptor channel_acceptor;
		private PreInvocationValidator validator;
		private ToIntFunction<Optional<Object>>
			channel_rx_window_size_function = attachment ->
			Integer.getInteger( "intrepid.channel.default_rx_window", 10_000_000 );
		private ProxyClassFilter proxy_class_filter = ( o, i ) -> true;
		private boolean force_proto_2 = false;

		private UnitTestHook unit_test_hook;


		public Builder authHandler( AuthenticationHandler auth_handler ) {
			if ( this.auth_handler != null ) {
				throw new IllegalStateException(
					"An AuthenticationHandler is already installed." );
			}
			this.auth_handler = auth_handler;
			return this;
		}

		public Builder serverAddress( SocketAddress server_address ) {
			this.server_address = server_address;
			return this;
		}

		public Builder driver(IntrepidDriver driver ) {
			this.driver = driver;
			return this;
		}

		public Builder threadPool( ScheduledExecutor thread_pool ) {
			this.thread_pool = thread_pool;
			return this;
		}

		public Builder vmidHint( String vmid_hint ) {
			this.vmid_hint = vmid_hint;
			return this;
		}

		public Builder openServer() {
			if ( this.auth_handler != null ) {
				throw new IllegalStateException(
					"An AuthenticationHandler is already installed." );
			}
			this.auth_handler = new NoAuthenticationHandler();
			if (this.server_address == null) this.server_address = new InetSocketAddress(0);

			return this;
		}

		public Builder connectionListener( ConnectionListener listener ) {
			this.connection_listener = listener;
			return this;
		}

		public Builder performanceListener( PerformanceListener listener ) {
			this.performance_listener = listener;
			return this;
		}

		public Builder channelAcceptor( ChannelAcceptor acceptor ) {
			this.channel_acceptor = acceptor;
			return this;
		}

		public Builder preInvocationValidator(
			@Nonnull PreInvocationValidator validator ) {

			this.validator = requireNonNull( validator );
			return this;
		}

		/**
		 * Provide a function for specifying the "receive window" for data received in
		 * virtual byte channels. This essentially specifies the amount of data that can sit
		 * in a queue waiting to be processed. A smaller window size will sometimes result
		 * is slower performance, depending on the usage pattern of the application.
		 * This can make a noticeable difference when the sender is very "bursty" and data
		 * processing is relatively expensive. However, testing in your application is
		 * recommended because send rate/frequency, receive processing rate and network
		 * performance can all contribute.
		 * <p>
		 * The default implementation is a fixed size controlled by the system property
		 * {@code intrepid.channel.default_rx_window}.
		 *
		 * @param size_function         A function that returns the window size for a channel
		 *                              given the attachment (from
		 *                              {@link Intrepid#createChannel(VMID, Serializable)} or
		 *                              {@link ChannelAcceptor#newChannel(ByteChannel, VMID, Serializable)}).
		 *                              Note that an Rx Window will be required for both peers
		 *                              involved in the channel, so this will be called on
		 *                              both the "client" and "server" side. The windows do
		 *                              are only used for receiving data, so the returned
		 *                              values do not need to match between peers.
		 *                              The size must be greater than zero.
		 */
		public Builder channelRxWindowSize(
			@Nonnull ToIntFunction<Optional<Object>> size_function ) {

			channel_rx_window_size_function = requireNonNull( size_function );
			return this;
		}


		/**
		 * Provide a filter that will verify the interfaces implemented by proxies. The
		 * default implementation approves all interfaces.
		 *
		 * @see ProxyClassFilter
		 */
		public Builder proxyClassFilter( @Nonnull ProxyClassFilter filter ) {
			proxy_class_filter = requireNonNull( filter );
			return this;
		}


		@Deprecated
		public Builder forceProtocolVersion2() {
			force_proto_2 = true;
			return this;
		}


		Builder unitTestHook( @Nonnull UnitTestHook hook ) {
			this.unit_test_hook = hook;
			return this;
		}


		public Intrepid build() throws IOException {
			if ( driver == null ) {
				try {
					driver = ( IntrepidDriver ) Class.forName(
						"com.starlight.intrepid.driver.mina.MINAIntrepidDriver" )
						.getConstructor()
						.newInstance();
				}
				catch( Exception ex ) {
					throw new UnsupportedOperationException( "Unable to find a default " +
						"driver. Either a known driver is needed in the classpath or " +
						"a driver will need to be manually specified." );
				}
			}

			if ( vmid_hint == null ) {
				if ( server_address != null ) vmid_hint = server_address.toString();
				else {
					try {
						InetAddress local_host = InetAddress.getLocalHost();
						if ( !local_host.isLoopbackAddress() ) {
							vmid_hint = local_host.getHostAddress();
						}
					}
					catch ( UnknownHostException ex ) {
						// ignore
					}
				}
			}
	
			ListenerSupport<ConnectionListener,?> connection_listeners =
				ListenerSupport.forType( ConnectionListener.class ).build();
			if ( this.connection_listener != null ) {
				connection_listeners.add( this.connection_listener );
			}
	
			ListenerSupport<PerformanceListener,?> performance_listeners =
				ListenerSupport.forType( PerformanceListener.class ).build();
			final PerformanceListener perf_listener = this.performance_listener;
			if ( perf_listener != null ) {
				performance_listeners.add( perf_listener );
			}
	
			VMID vmid = new VMID( UUID.randomUUID(), vmid_hint );
	
			// Create handlers
			LocalCallHandler local_handler = new LocalCallHandler( vmid,
				performance_listeners.dispatch(), this.validator,
				this.proxy_class_filter );
			RemoteCallHandler remote_handler = new RemoteCallHandler( driver, auth_handler,
				local_handler, vmid, thread_pool, performance_listeners,
				this.channel_acceptor, this.channel_rx_window_size_function,
				this.force_proto_2 );
	
			// Init SPI
			driver.init( server_address, vmid_hint, remote_handler,
				connection_listeners.dispatch(), thread_pool, vmid,
				ProxyInvocationHandler.DESERIALIZING_VMID, performance_listeners.dispatch(),
				this.unit_test_hook, VMID::new );
	
			Intrepid instance = new Intrepid( driver, vmid, local_handler, remote_handler,
				connection_listeners, performance_listeners );
	
			local_handler.initInstance( instance );
			remote_handler.initInstance( instance );
	
			INSTANCE_LISTENERS.dispatch().instanceOpened( vmid, instance );

			return instance;
		}
	}
}
