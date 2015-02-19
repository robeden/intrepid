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

import com.starlight.NotNull;
import com.starlight.Nullable;
import com.starlight.ValidationKit;
import com.starlight.intrepid.auth.AuthenticationHandler;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.RequestUserCredentialReinit;
import com.starlight.intrepid.exception.ChannelRejectedException;
import com.starlight.intrepid.exception.IntrepidRuntimeException;
import com.starlight.intrepid.spi.IntrepidSPI;
import com.starlight.intrepid.spi.mina.MINAIntrepidSPI;
import com.starlight.listeners.ListenerSupport;
import com.starlight.listeners.ListenerSupportFactory;
import com.starlight.thread.ScheduledExecutor;
import com.starlight.thread.SharedThreadPool;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.ByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;


/**
 * This class provides static functions for accessing Intrepid's main functionality.
 */
public class Intrepid {
	private static final long CONNECT_TIMEOUT =
		Long.getLong( "intrepid.connect.timeout", 10000 ).longValue();

	private static final Lock local_instance_map_lock = new ReentrantLock();
	private static final Map<VMID,Intrepid> local_instance_map =
		new HashMap<VMID,Intrepid>();

	private static final ThreadLocal<Intrepid> thread_instance =
		new InheritableThreadLocal<Intrepid>();

	private static final ListenerSupport<IntrepidInstanceListener,Void> instance_listeners =
		ListenerSupportFactory.create( IntrepidInstanceListener.class, true );

	/** This is a hook for testing while allows the "inter-instance bridging" provided
	 *  by {@link #findLocalInstance(VMID,boolean)} to be disabled when trying to shortcut.
	 *  When disabled, calls to local instance will user the full stack. */
	static volatile boolean disable_inter_instance_bridge =
		System.getProperty( "intrepid.disable_inter_instance_bridge" ) != null;

	private final IntrepidSPI spi;

	private final VMID vmid;

	private final LocalCallHandler local_handler;
	private final RemoteCallHandler remote_handler;

	private final ListenerSupport<ConnectionListener,Void> connection_listeners;
	private final ListenerSupport<PerformanceListener,Void> performance_listeners;

	private final PerformanceControl performance_control;

	private final ListenerRegistrationManager listener_registration_manager =
		new ListenerRegistrationManager( this );


	private volatile boolean closed = false;


	/**
	 * Initialize Intrepid with the SPI to implement the lower-level communication
	 * mechanism.
	 *
	 * @param setup 		Builder object containing the settings for the instance.
	 * 						Null will create an instance with the default settings for
	 * 						client usage.
	 *
	 * @throws IllegalStateException    If already initialized (without being shut down).
	 * @throws IOException				If an error occurs when initializing the SPI
	 *                                  driver. This generally means server socket setup.
	 */
	public static Intrepid create( IntrepidSetup setup )
		throws IOException {

		if ( setup == null ) setup = new IntrepidSetup();

		IntrepidSPI spi = setup.getSPI();
		if ( spi == null ) spi = new MINAIntrepidSPI();

		ScheduledExecutor thread_pool = setup.getThreadPool();
		if ( thread_pool == null ) {
			thread_pool = SharedThreadPool.INSTANCE;
		}

		InetAddress server_address = setup.getServerAddress();
		Integer server_port = setup.getServerPort();
		AuthenticationHandler auth_handler = setup.getAuthHandler();
		String vmid_hint = setup.getVMIDHint();
		if ( vmid_hint == null ) {
			if ( server_address != null ) vmid_hint = server_address.getHostAddress();
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

		ListenerSupport<ConnectionListener,Void> connection_listeners =
			ListenerSupportFactory.create( ConnectionListener.class, false );
		if ( setup.getConnectionListener() != null ) {
			connection_listeners.add( setup.getConnectionListener() );
		}

		ListenerSupport<PerformanceListener,Void> performance_listeners =
			ListenerSupportFactory.create( PerformanceListener.class, false );
		final PerformanceListener perf_listener = setup.getPerformanceListener();
		if ( perf_listener != null ) {
			performance_listeners.add( perf_listener );
		}

		VMID vmid = new VMID( UUID.randomUUID(), vmid_hint );

		// Create handlers
		LocalCallHandler local_handler =
			new LocalCallHandler( vmid, performance_listeners.dispatch() );
		RemoteCallHandler remote_handler = new RemoteCallHandler( spi, auth_handler,
			local_handler, vmid, thread_pool, performance_listeners,
			setup.getChannelAcceptor() );

		// Init SPI
		spi.init( server_address, server_port, vmid_hint, remote_handler,
			connection_listeners.dispatch(), thread_pool, vmid,
			ProxyInvocationHandler.DESERIALIZING_VMID, performance_listeners.dispatch(),
			setup.getUnitTestHook() );

		Intrepid instance = new Intrepid( spi, vmid, local_handler, remote_handler,
			connection_listeners, performance_listeners );

		local_handler.initInstance( instance );
		remote_handler.initInstance( instance );

		instance_listeners.dispatch().instanceOpened( vmid, instance );

		return instance;
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
		Intrepid instance = thread_instance.get();

		// See if there is an active call context from which we can determine the instance
		if ( instance == null ) {
			instance = IntrepidContext.getActiveInstance();
		}

		// If not, see if there is only one active instance
		if ( instance == null ) {
			local_instance_map_lock.lock();
			try {
				if ( local_instance_map.size() != 1 ) {
					throw new IllegalStateException( "This method can only be used if " +
						" is one and only one instance active but there are " +
						local_instance_map.size() + " instances active." );
				}

				instance = local_instance_map.values().iterator().next();
			}
			finally {
				local_instance_map_lock.unlock();
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
		if ( instance == null ) thread_instance.remove();
		else thread_instance.set( instance );
	}


	/**
	 * Add a listener that will be notified when a new Intrepid instance is created.
	 */
	public static void addInstanceListener( IntrepidInstanceListener listener ) {
		instance_listeners.add( listener );
	}

	/**
	 * @see #addInstanceListener(IntrepidInstanceListener)
	 */
	@SuppressWarnings( "UnusedDeclaration" )
	public static void removeInstanceListener( IntrepidInstanceListener listener ) {
		instance_listeners.remove( listener );
	}


	/**
	 * Find the Intrepid instance with the VMID in the local VM.
	 *
	 * @see #disable_inter_instance_bridge
	 */
	static Intrepid findLocalInstance( VMID vmid, boolean trying_to_shortcut ) {
		if ( trying_to_shortcut && disable_inter_instance_bridge ) return null;

		local_instance_map_lock.lock();
		try {
			return local_instance_map.get( vmid );
		}
		finally {
			local_instance_map_lock.unlock();
		}
	}


    /**
     * Returns the instance that has a connection to the given VMID or null if none.
     */
    static Intrepid findInstanceWithRemoteSession( VMID remote_vmid ) {
	    // Check the thread-specified instance first
	    Intrepid t_instance = thread_instance.get();
	    if ( t_instance != null && t_instance.spi.hasConnection( remote_vmid ) ) {
		    return t_instance;
	    }

		local_instance_map_lock.lock();
		try {
            for( Intrepid instance : local_instance_map.values() ) {
                if ( instance.spi.hasConnection( remote_vmid ) ) return instance;
            }

            return null;
		}
		finally {
			local_instance_map_lock.unlock();
		}
    }


	private Intrepid( IntrepidSPI spi, VMID vmid, LocalCallHandler local_handler,
		RemoteCallHandler remote_handler,
		ListenerSupport<ConnectionListener,Void> connection_listeners,
		ListenerSupport<PerformanceListener,Void> performance_listeners ) {

		this.spi = spi;
		this.vmid = vmid;
		this.local_handler = local_handler;
		this.remote_handler = remote_handler;
		this.connection_listeners = connection_listeners;
		this.performance_listeners = performance_listeners;

		this.performance_control = new PerformanceControlWrapper();

		local_instance_map_lock.lock();
		try {
			local_instance_map.put( vmid, this );
		}
		finally {
			local_instance_map_lock.unlock();
		}
	}


	public void close() {
		closed = true;

		local_instance_map_lock.lock();
		try {
			local_instance_map.remove( vmid );
		}
		finally {
			local_instance_map_lock.unlock();
		}

		local_handler.shutdown();
		spi.shutdown();

		instance_listeners.dispatch().instanceClosed( getLocalVMID() );
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
	 *
	 * @param host      	Host to connect to.
	 * @param args      	(Optional) SPI-dependant connection args.
	 * @param attachment	An object the caller can associate with the connection
	 *
	 * @return              The VMID of the remote host.
	 *
	 * @throws IOException                  Thrown if an error occurs while trying to
	 *                                      connect.
	 * @throws com.starlight.intrepid.exception.ConnectionFailureException   If the connection failed due to an
	 *                                      authentication/authorization failure.
	 *
	 * @see #tryConnect
	 */
	public VMID connect( InetAddress host, int port, ConnectionArgs args,
		Object attachment ) throws IOException {

		long timeout = CONNECT_TIMEOUT;
		TimeUnit timeout_unit = TimeUnit.MILLISECONDS;
		if ( args instanceof RequestUserCredentialReinit ) {
			timeout = 5;
			timeout_unit = TimeUnit.MINUTES;
		}

		return spi.connect( host, port, args, attachment, timeout, timeout_unit, false );
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

		ValidationKit.checkNonnull( host, "host" );

		if ( closed ) throw new IllegalStateException( "Closed" );

		return spi.connect( host, port, args, attachment, timeout, timeout_units, true );
	}



	/**
	 * Disconnect from a remote host.
	 */
	public void disconnect( VMID host_vmid ) {
		if ( closed ) throw new IllegalStateException( "Closed" );

		ValidationKit.checkNonnull( host_vmid, "host_vmid" );

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
	 * @throws com.starlight.intrepid.exception.ChannelRejectedException    Indicates the channel was rejected by the server.
	 */
	public ByteChannel createChannel( VMID destination, Serializable attachment )
		throws IOException, ChannelRejectedException {

		ValidationKit.checkNonnull( destination, "destination" );

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
	public <L,P> ListenerRegistration keepListenerRegistered( @NotNull L listener,
		@NotNull P proxy, @NotNull BiConsumer<P,L> add_method,
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
	public <L,P,R> ListenerRegistration keepListenerRegistered( @NotNull L listener,
		@NotNull P proxy, @NotNull BiFunction<P,L,R> add_method,
		@Nullable BiConsumer<P,L> remove_method,
		@NotNull Consumer<R> return_value_handler ) throws IllegalArgumentException {

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
	IntrepidSPI getSPI() {
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
}
