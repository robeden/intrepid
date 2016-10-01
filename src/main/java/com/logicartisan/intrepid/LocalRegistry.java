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

package com.logicartisan.intrepid;

import com.starlight.ValidationKit;
import com.logicartisan.intrepid.exception.ObjectNotBoundException;
import com.starlight.locale.FormattedTextResourceKey;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 *
 */
public class LocalRegistry implements Registry {
	private final VMID vmid;

	private final Lock map_lock = new ReentrantLock();
	private final Map<String,Proxy> proxy_map = new HashMap<String, Proxy>();
	private final TIntObjectMap<String> id_to_name_map = new TIntObjectHashMap<String>();

	private final Condition new_object_condition = map_lock.newCondition();

	private Intrepid instance;

	LocalRegistry( VMID vmid ) {
		this.vmid = vmid;
	}


	@Override
	public Object lookup( String name ) throws ObjectNotBoundException {
		map_lock.lock();
		try {
			Object obj = proxy_map.get( name );
			if ( obj == null ) {
				throw new ObjectNotBoundException( new FormattedTextResourceKey(
					Resources.OBJECT_NOT_BOUND, name ) );
			}
			else return obj;
		}
		finally {
			map_lock.unlock();
		}
	}


	@Override
	public Object tryLookup( String name, long timeout, TimeUnit timeout_unit )
		throws InterruptedException {

		map_lock.lock();
		try {
			Object obj = proxy_map.get( name );
			if ( obj != null ) return obj;

			long remaining = timeout_unit.toNanos( timeout );
			while( remaining > 0 ) {
				long start = System.nanoTime();
				new_object_condition.await( remaining, TimeUnit.NANOSECONDS );

				obj = proxy_map.get( name );
				if ( obj != null ) return obj;

				remaining -= ( System.nanoTime() - start );
			}

			return null;
		}
		finally {
			map_lock.unlock();
		}
	}

	/**
	 * Bind an object in the registry.
	 */
	public void bind( String name, Object object ) {
		ValidationKit.checkNonnull( name, "name" );
		ValidationKit.checkNonnull( object, "object" );

		final Object original_object = object;

		// Make sure it's a proxy
		if ( !ProxyKit.isProxy( object ) ) {
			object = instance.createProxy( object );
			assert object != null :
				"Null object returned from Intrepid.createProxy() for object: " +
				original_object;
		}
		else if ( !ProxyKit.isProxyLocal( object, vmid ) ) {
			throw new IllegalArgumentException( "Proxy must be local" );
		}

		Proxy proxy = ( Proxy ) object;

		map_lock.lock();
		try {
			proxy_map.put( name, proxy );
			id_to_name_map.put( proxy.__intrepid__getObjectID(), name );

			proxy.__intrepid__setPersistentName( name );

			new_object_condition.signalAll();
		}
		finally {
			map_lock.unlock();
		}

		instance.getLocalCallHandler().markBound( proxy.__intrepid__getObjectID(), true );
	}


	/**
	 * Remove an object from the registry.
	 */
	public void unbind( String name ) {
		Proxy proxy;

		map_lock.lock();
		try {
			proxy = proxy_map.remove( name );
			if ( proxy != null ) {
				id_to_name_map.remove( proxy.__intrepid__getObjectID() );
			}
		}
		finally {
			map_lock.unlock();
		}

		if ( proxy != null ) {
			proxy.__intrepid__setPersistentName( null );

			instance.getLocalCallHandler().markBound( proxy.__intrepid__getObjectID(),
				false );
		}
	}


	void setInstance( Intrepid instance ) {
		assert vmid.equals( instance.getLocalVMID() ) :
			vmid + " != " + instance.getLocalVMID();
		this.instance = instance;
	}
}
