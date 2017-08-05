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

import com.starlight.ArrayKit;
import com.starlight.NotNull;
import com.starlight.Nullable;
import com.starlight.intrepid.auth.PreInvocationValidator;
import com.starlight.intrepid.exception.IllegalProxyDelegateException;
import com.starlight.intrepid.exception.UnknownMethodException;
import com.starlight.intrepid.exception.UnknownObjectException;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.TObjectLongMap;
import gnu.trove.map.custom_hash.TObjectIntCustomHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import gnu.trove.procedure.TObjectLongProcedure;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.strategy.IdentityHashingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Externalizable;
import java.io.Serializable;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;


/**
 *
 */
class LocalCallHandler {
	private static final Logger LOG = LoggerFactory.getLogger( LocalCallHandler.class );

	// NOTE: ID zero is reserved
	private static final boolean OBJECT_ID_STARTS_AT_ONE =
		System.getProperty( "intrepid.oid_start_at_one" ) != null;

	private static final long LOCAL_VM_INITIAL_RESERVATION_DURATION_NS =
		TimeUnit.MILLISECONDS.toNanos(
		Long.getLong( "intrepid.local_call_handler.initial_reservation",
		TimeUnit.MINUTES.toMillis( 5 ) ).longValue() );   // 5 min

	private static final Class[] PROXY_CLASS_ARRAY = new Class[] { Proxy.class };
	private static final Class[] REGISTRY_CLASSES =
		new Class[] { Registry.class, Proxy.class };

	private final VMID vmid;
	private final LocalRegistry local_registry;
	private Intrepid instance;					// for call context info

	private final PerformanceListener performance_listeners;
	private final PreInvocationValidator pre_call_validator;

	private final Lock map_lock = new ReentrantLock();
	private final TObjectIntMap<Object> object_to_id_map =
		new TObjectIntCustomHashMap<>( new IdentityHashingStrategy<>() );
	private final TIntObjectMap<ProxyInfo> id_to_object_map =
		new TIntObjectHashMap<>();

	// NOTE: ID zero is reserved
	private final AtomicInteger object_id_counter =
		new AtomicInteger( OBJECT_ID_STARTS_AT_ONE ? 1 : new Random().nextInt() );

	private final LeasePruner lease_pruner = new LeasePruner();

	private final ReferenceQueue<Proxy> ref_queue = new ReferenceQueue<>();


	LocalCallHandler( @NotNull VMID vmid,
		@NotNull PerformanceListener performance_listeners,
		@Nullable PreInvocationValidator pre_call_validator ) {

		this.vmid = Objects.requireNonNull( vmid );
		this.performance_listeners = Objects.requireNonNull( performance_listeners );
		this.pre_call_validator = pre_call_validator;

		LeaseManager.registerLocalHandler( vmid, this );

		local_registry = new LocalRegistry( vmid );
		bindRegistry( local_registry, vmid );
	}

	void initInstance( Intrepid instance ) {
		this.instance = instance;
	}


	VMID getVMID() {
		return vmid;
	}


	void shutdown() {
		LeaseManager.deregisterLocalHandler( vmid );
	}


	Proxy createProxy( Object delegate, String persistent_name,
		@NotNull Predicate<Class> proxy_class_filter ) {

		// Make sure it's not already a proxy
		if ( delegate instanceof Proxy ) return ( Proxy ) delegate;

		// Assume we're actually going to create the proxy so we minimize the time with
		// the map locked.
		
		// Do this before hitting the map so we know it's a proxy-able object
		Class[] interfaces =
			findProxyInterfaces( delegate.getClass(), proxy_class_filter );

		TIntObjectMap<Method> method_map = new TIntObjectHashMap<>();
		for( Class ifc : interfaces ) {
			TIntObjectMap<Method> map = MethodMap.generateMethodMap( ifc );
			method_map.putAll( map );
		}

		TObjectIntMap<MethodIDTemplate> reverse_method_map =
			MethodMap.generateReverseMethodMap( method_map );

		Proxy proxy = null;
		map_lock.lock();
		try {
			if ( object_to_id_map.containsKey( delegate ) ) {
				int object_id = object_to_id_map.get( delegate );
				ProxyInfo proxy_info = id_to_object_map.get( object_id );
				proxy = proxy_info.promoteReference();
				assert proxy != null :
					"Unable to promote reference for delegate: " + delegate;
			}

			// If we were unable to get a proxy from the maps, create one now.
			if ( proxy == null ) {
				int object_id = object_id_counter.incrementAndGet();
				if ( object_id == 0 ) {		// NOTE: ID zero is reserved
					object_id = object_id_counter.incrementAndGet();
				}

				object_to_id_map.put( delegate, object_id );
				LOG.debug( "Adding proxy {} for: {}", Integer.valueOf( object_id ),
					delegate );

				ClassLoader class_loader = delegate.getClass().getClassLoader();
				if ( class_loader == ClassLoader.getSystemClassLoader() ||
					class_loader == null ) {

					class_loader = Intrepid.class.getClassLoader();
				}


				proxy = ( Proxy ) java.lang.reflect.Proxy.newProxyInstance(
					class_loader,
					ArrayKit.combine( Class.class, interfaces, PROXY_CLASS_ARRAY ),
					new ProxyInvocationHandler( vmid, object_id, reverse_method_map,
					persistent_name, delegate ) );

				ProxyInfo proxy_info = new ProxyInfo( proxy, ref_queue, object_id,
					delegate, method_map, vmid );
				id_to_object_map.put( object_id, proxy_info );
			}
		}
		catch( IllegalArgumentException ex ) {
			StringBuilder buf = new StringBuilder();
			for( Class clazz : interfaces ) {
				if ( buf.length() != 0 ) buf.append( ", " );
				buf.append( Modifier.toString( clazz.getModifiers() ) );
				buf.append( ' ' );
				buf.append( clazz.getName() );
			}
			throw new IllegalArgumentException( ex.getMessage() + " (Interfaces: " +
				buf.toString() + ")", ex );
		}
		finally {
			map_lock.unlock();
		}

		return proxy;
	}

	public Object invoke( int object_id, int method_id, Object[] args,
		boolean local_origination, String persistent_name ) throws Throwable {

		ProxyInfo proxy_info;

		map_lock.lock();
		try {
			proxy_info = id_to_object_map.get( object_id );
		}
		finally {
			map_lock.unlock();
		}

		if ( proxy_info == null ) {
			throw new UnknownObjectException( object_id, persistent_name, vmid );
		}

		Method method = proxy_info.method_map.get( method_id );
		if ( method == null ) {
			throw new UnknownMethodException( method_id );
		}

		Thread my_thread = Thread.currentThread();
		final String original_thread_name = my_thread.getName();

		try {
			if ( !local_origination ) {
				my_thread.setName( "Intrepid invoke: " + methodToString( method ) +
					" (source: " + IntrepidContext.getCallingVMID() + ")" );
			}
			method.setAccessible( true );

			if ( local_origination ) {
				IntrepidContext.setCallInfo( instance, vmid, null, null );
			}

			if ( pre_call_validator != null ) {
				pre_call_validator.preCall(
					IntrepidContext.getActiveInstance(),
					IntrepidContext.getCallingVMID(),
					IntrepidContext.getCallingHost(),
					IntrepidContext.getUserInfo(),
					method, proxy_info.delegate, args );
			}

			return method.invoke( proxy_info.delegate, args );
		}
		catch( InvocationTargetException ex ) {
			throw ex.getCause();
		}
		finally {
			if ( local_origination ) IntrepidContext.clearCallInfo();
			else my_thread.setName( original_thread_name );
		}
	}


	/**
	 * Find the method associated with a given object and method ID.
	 *
	 * @return  The method ID or null if not found.
	 */
	Method lookupMethodForID( int object_id, int method_id ) {
		ProxyInfo proxy_info;

		map_lock.lock();
		try {
			proxy_info = id_to_object_map.get( object_id );
		}
		finally {
			map_lock.unlock();
		}

		if ( proxy_info == null ) return null;

		return proxy_info.method_map.get( method_id );
	}


	LocalRegistry getLocalRegistry() {
		return local_registry;
	}


	Object getLocalDelegate( int object_id ) {
		ProxyInfo proxy_info;

		map_lock.lock();
		try {
			proxy_info = id_to_object_map.get( object_id );
		}
		finally {
			map_lock.unlock();
		}

		if ( proxy_info == null ) return null;
		return proxy_info.delegate;
	}


	void renewLease( int object_id, VMID originating_vm ) {
		ProxyInfo info;

		map_lock.lock();
		try {
			info = id_to_object_map.get( object_id );
		}
		finally {
			map_lock.unlock();
		}

		if ( info == null ) return;

		info.renewLease( originating_vm );
	}

	void giveUpLease( int object_id, VMID originating_vm ) {
		ProxyInfo info;

		map_lock.lock();
		try {
			info = id_to_object_map.get( object_id );
		}
		finally {
			map_lock.unlock();
		}

		if ( info == null ) return;

		info.giveUpLease( originating_vm );
	}


	// This should only be called from the Lease Manager
	void pruneLeases() {
		if ( LeaseManager.LEASE_DEBUGGING ) System.out.println( "---pruneLeases---" );
		LOG.trace( "---pruneLeases---" );
		lease_pruner.reset();

		map_lock.lock();
		try {
			ProxyWeakReference ref;
			while( ( ref = ( ProxyWeakReference ) ref_queue.poll() ) != null ) {
				object_to_id_map.remove( ref.delegate );
				id_to_object_map.remove( ref.object_id );
				if ( LeaseManager.LEASE_DEBUGGING ) {
					System.out.println( "Removing object " + ref.object_id + ": " +
						ref.delegate_to_string );
				}
				if ( LOG.isTraceEnabled() ) {
					LOG.trace( "Removing object {}: {}", Integer.valueOf( ref.object_id ),
						ref.delegate_to_string );
				}

				performance_listeners.leasedObjectRemoved( vmid, ref.object_id );
			}

			id_to_object_map.forEachValue( lease_pruner );
		}
		finally {
			map_lock.unlock();
		}
	}


	void markBound( int object_id, boolean bound ) {
		map_lock.lock();
		try {
			ProxyInfo info = id_to_object_map.get( object_id );
			if ( info == null ) return;

			info.bound = bound;
			if ( bound ) info.promoteReference();

			performance_listeners.leaseInfoUpdated( vmid, info.object_id,
				info.delegate_to_string, info.strong_ref != null,
				info.vmid_lease_map.size(), false, false );
		}
		finally {
			map_lock.unlock();
		}
	}


	// Package-private for testability
	static Class[] findProxyInterfaces( Class object_class,
		@NotNull Predicate<Class> class_filter ) {

		Set<Class> class_set = new HashSet<>();
		findProxyInterfaces( object_class, class_set, class_filter );

		if ( class_set.isEmpty() ) {
			throw new IllegalProxyDelegateException( "No valid interfaces found." );
		}

		// Make sure it's Serializable
		class_set.add( Serializable.class );

		return class_set.toArray( new Class[ class_set.size() ] );
	}


	private void bindRegistry( LocalRegistry registry, VMID vmid ) {
		// Registry is always object ID "0

		TIntObjectMap<Method> method_map = MethodMap.generateMethodMap( Registry.class );
		TObjectIntMap<MethodIDTemplate> reverse_method_map =
			MethodMap.generateReverseMethodMap( method_map );

		ProxyInfo proxy_info;
		map_lock.lock();
		try {
			object_to_id_map.put( registry, 0 );

			ClassLoader class_loader = registry.getClass().getClassLoader();

			Proxy proxy = ( Proxy ) java.lang.reflect.Proxy.newProxyInstance(
				class_loader, REGISTRY_CLASSES,
				new ProxyInvocationHandler( vmid, 0, reverse_method_map, null, registry ) );

			// NOTE: force us to always hold a strong reference
			proxy_info = new ProxyInfo( proxy, ref_queue, 0, registry, method_map,
				vmid, true );
			id_to_object_map.put( 0, proxy_info );
		}
		finally {
			map_lock.unlock();
		}
	}


	private static void findProxyInterfaces( Class clazz, Set<Class> interface_list,
		@NotNull Predicate<Class> class_filter ) {

		Class[] implemented_classes = clazz.getInterfaces();

		for( Class implemented_class : implemented_classes ) {
			if ( !class_filter.test( implemented_class ) ) continue;

			// Add all interfaces, except for Externalizable
			if ( Externalizable.class.isAssignableFrom( implemented_class ) ) continue;

			// Skip non-public interfaces
			if ( !Modifier.isPublic( implemented_class.getModifiers() ) ) continue;

			interface_list.add( implemented_class );
		}

		Class super_class = clazz.getSuperclass();
		if ( super_class == null ) return;

		findProxyInterfaces( super_class, interface_list, class_filter );
	}


	private static String methodToString( Method method ) {
		StringBuilder buf = new StringBuilder();
		buf.append( method.getDeclaringClass().getSimpleName() );
		buf.append( '.' );
		buf.append( method.getName() );
		buf.append( '(' );
		boolean first = true;
		for( Class<?> type : method.getParameterTypes() ) {
			if ( first ) first = false;
			else {
				buf.append( ',' );
			}
			buf.append( type.getSimpleName() );
		}
		buf.append( ')' );
		return buf.toString();
	}


	private class ProxyInfo {
		private final int object_id;

		private final boolean force_strong_ref;
		private volatile boolean bound = false;

		private volatile Proxy strong_ref;
		private final ProxyWeakReference weak_ref;

		private final Object delegate;
		private final TIntObjectMap<Method> method_map;

		private final String delegate_to_string;
		private final StackTraceElement[] allocation_trace;

		// Map containing leases and the time at which they are next expected to renew
		private final TObjectLongMap<VMID> vmid_lease_map = new TObjectLongHashMap<>();
		private final Lock vmid_lease_map_lock = new ReentrantLock();

		ProxyInfo( Proxy proxy, ReferenceQueue<Proxy> ref_queue, int object_id,
			Object delegate, TIntObjectMap<Method> method_map, VMID local_vmid ) {

			this( proxy, ref_queue, object_id, delegate, method_map, local_vmid,
				LeaseManager.DGC_DISABLED );
		}

		ProxyInfo( Proxy proxy, ReferenceQueue<Proxy> ref_queue, int object_id,
			Object delegate, TIntObjectMap<Method> method_map, VMID local_vmid,
			boolean force_strong_ref ) {

			this.object_id = proxy.__intrepid__getObjectID();
			this.force_strong_ref = force_strong_ref;

			// NOTE: automatically grab strong reference
			strong_ref = proxy;
			// Automatically register a reservation for this VMID for 30 sec. That will
			// prevent the reservation from going away too quickly if it is purely a
			// remotely referenced proxy.
			vmid_lease_map.put( local_vmid,
				System.nanoTime() + LOCAL_VM_INITIAL_RESERVATION_DURATION_NS );

			this.delegate = delegate;
			this.delegate_to_string = delegate.toString();
			this.method_map = method_map;

			weak_ref = new ProxyWeakReference( proxy, object_id, ref_queue, delegate,
				delegate_to_string );

			if ( LeaseManager.LEASE_DEBUGGING ) {
				allocation_trace = new Throwable().getStackTrace();
			}
			else allocation_trace = null;

			notifyPerformanceListeners( false, false );
		}


		void renewLease( VMID originating_vmid ) {
			promoteReference();

			vmid_lease_map_lock.lock();
			try {
				vmid_lease_map.put( originating_vmid,
					System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
					LeaseManager.LEASE_DURATION_MS ) );
			}
			finally {
				vmid_lease_map_lock.unlock();
			}

			notifyPerformanceListeners( true, false );
		}

		void giveUpLease( VMID originating_vmid ) {
			vmid_lease_map_lock.lock();
			try {
				vmid_lease_map.remove( originating_vmid );
				if ( vmid_lease_map.isEmpty() ) {
					if ( LeaseManager.LEASE_DEBUGGING ) {
						System.out.println( "demoting reference for " + object_id +
							" (" + delegate_to_string +
							") after giveUpLease since vmid_lease_map is empty." );
					}
					demoteReference();
				}
			}
			finally {
				vmid_lease_map_lock.unlock();
			}

			notifyPerformanceListeners( false, true );
		}


		/**
		 * Attempt to promote the reference to a strong reference and return whether or
		 * that action was successful. This can be unsuccessful if the weak reference was
		 * already GC'ed. If successful the Proxy will be returned. Otherwise null will
		 * be returned.
		 */
		private Proxy promoteReference() {
			if ( bound || force_strong_ref || strong_ref != null ) return strong_ref;

			strong_ref = weak_ref.get();
			if ( LeaseManager.LEASE_DEBUGGING && strong_ref == null ) {
				System.out.println( ">>> Referent for weak reference was null " +
					"for object " + object_id + " (" + delegate_to_string + ")" );
			}

			notifyPerformanceListeners( false, false );

			return strong_ref;
		}

		/**
		 * Demote the reference to a weak reference.
		 *
		 * @return true if the reference was actually demoted.
		 */
		private boolean demoteReference() {
			if ( bound || force_strong_ref ) return false;

			Proxy old_ref = strong_ref;

			if ( LeaseManager.LEASE_DEBUGGING && old_ref != null ) {
				synchronized ( System.out ) {
					vmid_lease_map_lock.lock();
					try {
						System.out.println( ">>> Demoting reference for " + object_id +
							"(" + delegate_to_string + "). Lease map: " + vmid_lease_map );
					}
					finally {
						vmid_lease_map_lock.unlock();
					}

					if ( allocation_trace != null ) {
						for( StackTraceElement element : allocation_trace ) {
							System.out.println( ">>>    at " + element );
						}
					}
				}
			}

			strong_ref = null;

			notifyPerformanceListeners( false, false );

			return old_ref != null;
		}


		private void notifyPerformanceListeners( boolean renew, boolean release ) {
			performance_listeners.leaseInfoUpdated( vmid, object_id, delegate_to_string,
				strong_ref != null, vmid_lease_map.size(), renew, release );
		}


		@Override
		public String toString() {
			return "{ProxyInfo " + delegate_to_string + "}";
		}
	}


	private static class ProxyWeakReference extends WeakReference<Proxy> {
		private final int object_id;
		private final Object delegate;
		private final String delegate_to_string;

		ProxyWeakReference( Proxy proxy, int object_id,
			ReferenceQueue<Proxy> ref_queue, Object delegate, String delegate_to_string ) {

			super( proxy, ref_queue );

			this.object_id = object_id;
			this.delegate = delegate;
			this.delegate_to_string = delegate_to_string;
		}
	}


	private static class LeasePruner implements TObjectProcedure<ProxyInfo>,
		TObjectLongProcedure<VMID> {

		private final VMID[] vms_to_remove = new VMID[ 250 ];
		private int next_vm_index = 0;

		private long current_time;


		void reset() {
			next_vm_index = 0;
			current_time = System.nanoTime();
		}


		@Override
		public boolean execute( ProxyInfo info ) {
			// See if this proxy has lease management disabled.
			if ( info.force_strong_ref ) return true;

			info.vmid_lease_map_lock.lock();
			try {
				// If there are no entries, exit quickly
				if ( info.vmid_lease_map.isEmpty() ) {
					if ( LeaseManager.LEASE_DEBUGGING ) {
						System.out.println( "demoting reference for " + info.object_id +
							" (" + info.delegate_to_string +
							") vmid_lease_map is empty at start of LeasePruner." );
					}
					boolean demoted = info.demoteReference();
					if ( demoted ) {
						LOG.debug( "Reference demoted for {}",
							Integer.valueOf( info.object_id ) );
					}
					return true;
				}

				info.vmid_lease_map.forEachEntry( this );

				// If any VMID's were marked for removal, remove them now.
				if ( next_vm_index > 0 ) {
					for( int i = 0; i < next_vm_index; i++ ) {
						info.vmid_lease_map.remove( vms_to_remove[ i ] );
					}
					next_vm_index = 0;
				}

				if ( info.vmid_lease_map.isEmpty() ) {
					if ( LeaseManager.LEASE_DEBUGGING ) {
						System.out.println( "demoting reference for " + info.object_id +
							" (" + info.delegate_to_string +
							") vmid_lease_map is empty after pruning entries." );
					}
					boolean demoted = info.demoteReference();
					if ( demoted ) {
						LOG.debug( "Reference demoted for {}",
							Integer.valueOf( info.object_id ) );
					}
				}
			}
			finally {
				info.vmid_lease_map_lock.unlock();
			}
			return true;
		}

		@Override
		public boolean execute( VMID vmid, long expiration_time ) {
			if ( expiration_time < current_time ) {
				vms_to_remove[ next_vm_index ] = vmid;
				next_vm_index++;
			}
			// Stop if we're about to overrun the array
			return next_vm_index < vms_to_remove.length;
		}
	}
}
