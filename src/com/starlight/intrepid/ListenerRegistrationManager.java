package com.starlight.intrepid;

import com.starlight.NotNull;
import com.starlight.Nullable;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.exception.IntrepidRuntimeException;
import com.starlight.thread.SharedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;


/**
 *
 */
class ListenerRegistrationManager implements ConnectionListener {
	private static final Logger LOG =
		LoggerFactory.getLogger( ListenerRegistrationManager.class );



	private final Intrepid intrepid_instance;

	private final Lock listeners_map_lock = new ReentrantLock();

	private final Map<VMID,Set<ListenerInfo>> listeners_map = new HashMap<>();



	ListenerRegistrationManager( @NotNull Intrepid intrepid_instance ) {
		this.intrepid_instance = Objects.requireNonNull( intrepid_instance );
	}



	@Override
	public void connectionOpened( InetAddress host, int port, Object attachment,
		VMID source_vmid, VMID vmid, UserContextInfo user_context, VMID previous_vmid,
		Object connection_type_description, byte ack_rate_sec ) {

		listeners_map_lock.lock();
		try {
			// Deal with remapped VMID
			Set<ListenerInfo> old_listeners = null;
			if ( previous_vmid != null ) {
				old_listeners = listeners_map.remove( previous_vmid );
			}

			Set<ListenerInfo> listeners = listeners_map.get( vmid );

			if ( listeners == null ) {
				if ( old_listeners == null ) return;

				listeners_map.put( vmid, old_listeners );
				listeners = old_listeners;
			}
			else if ( old_listeners != null ) {
				listeners.addAll( old_listeners );
			}

			for( ListenerInfo info : listeners ) {
				SharedThreadPool.INSTANCE.execute( () -> {
					info.addListener( true );
				} );
			}
		}
		finally {
			listeners_map_lock.unlock();
		}
	}



	@Override
	public void connectionClosed( InetAddress host, int port, VMID source_vmid, VMID vmid,
		Object attachment, boolean will_attempt_reconnect ) {

		listeners_map_lock.lock();
		try {
			Set<ListenerInfo> listeners = listeners_map.get( vmid );
			if ( listeners == null ) return;

			listeners.forEach( ListenerInfo::markOffline );
		}
		finally {
			listeners_map_lock.unlock();
		}
	}



	@Override
	public void connectionOpening( InetAddress host, int port, Object attachment,
		ConnectionArgs args, Object connection_type_description ) {}

	@Override
	public void connectionOpenFailed( InetAddress host, int port, Object attachment,
		Exception error, boolean will_retry ) {}



	<P,L> ListenerRegistration keepListenerRegistered(
		final @NotNull L listener, final @NotNull VMID vmid, final @NotNull P proxy,
		final @NotNull BiConsumer<P,L> add_method,
		final @Nullable BiConsumer<P,L> remove_method )
		throws IllegalArgumentException {

		final ListenerInfo<L,P> info =
			new ListenerInfo<>( proxy, listener, add_method, remove_method );

		info.addListener( false );

		listeners_map_lock.lock();
		try {
			boolean first_listener = listeners_map.isEmpty();

			Set<ListenerInfo> listeners = listeners_map.get( vmid );
			if ( listeners == null ) {
				listeners = new HashSet<>();
				listeners_map.put( vmid, listeners );
			}

			listeners.add( info );

			if ( first_listener ) {
				intrepid_instance.addConnectionListener( this );
			}
		}
		finally {
			listeners_map_lock.unlock();
		}

		return new ListenerRegistration() {
			@Override
			public void remove() {
				info.removeListener();

				listeners_map_lock.lock();
				try {
					Set<ListenerInfo> listeners = listeners_map.get( vmid );
					if ( listeners == null ) return;

					listeners.remove( info );
					if ( listeners.isEmpty() ) {
						listeners_map.remove( vmid );
					}

					if ( listeners_map.isEmpty() ) {
						intrepid_instance.removeConnectionListener(
							ListenerRegistrationManager.this );
					}
				}
				finally {
					listeners_map_lock.unlock();
				}
			}

			@Override
			public boolean isCurrentlyConnected() {
				return info.isConnected();
			}
		};
	}


	private static class ListenerInfo<L,P> {
		private final P proxy;
		private final L listener;

		private final BiConsumer<P,L> add_method;
		private final BiConsumer<P,L> remove_method;

		private final AtomicBoolean connected = new AtomicBoolean( false );
		private final AtomicReference<ScheduledFuture<?>> deferred_add_listener_slot =
			new AtomicReference<>();


		public ListenerInfo( P proxy, L listener, BiConsumer<P,L> add_method,
			BiConsumer<P,L> remove_method ) {

			this.proxy = proxy;
			this.listener = listener;
			this.add_method = add_method;
			this.remove_method = remove_method;
		}


		void addListener( boolean schedule_on_fail ) {
			removeScheduledAddTask();

			try {
				long start = System.currentTimeMillis();
				add_method.accept( proxy, listener );
				System.out.println( "Add time: " + ( System.currentTimeMillis() - start ));
			}
			catch( Exception ex ) {
				connected.set( false );
				if ( schedule_on_fail ) {
					deferred_add_listener_slot.set( SharedThreadPool.INSTANCE.schedule(
						() -> addListener( true ), 5, TimeUnit.SECONDS ) );
				}
				else throw ex;
			}
			connected.set( true );
		}

		void removeListener() {
			removeScheduledAddTask();

			try {
				remove_method.accept( proxy, listener );
			}
			catch( IntrepidRuntimeException ex ) {
				// ignore this
			}

			connected.set( false );
		}

		void markOffline() {
			removeScheduledAddTask();

			connected.set( false );
		}

		boolean isConnected() {
			return connected.get();
		}


		private void removeScheduledAddTask() {
			ScheduledFuture<?> future = deferred_add_listener_slot.getAndSet( null );
			if ( future != null ) {
				future.cancel( false );
			}
		}
	}
}
