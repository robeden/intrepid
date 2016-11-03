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

import com.logicartisan.intrepid.exception.IntrepidRuntimeException;
import com.logicartisan.intrepid.exception.NotConnectedException;
import com.logicartisan.intrepid.exception.UnknownMethodException;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Handles reflective method invocations and dispatches the calls as appropriate.
 */
class ProxyInvocationHandler implements InvocationHandler, Externalizable {
	private static final long serialVersionUID = 0L;

	private static final Logger LOG =
		LoggerFactory.getLogger( ProxyInvocationHandler.class );

	private static final ThreadLocal<MethodIDTemplate> method_template_local =
		new ThreadLocal<>();

	private static final Method EQUALS_METHOD;
	private static final Method HASHCODE_METHOD;
	private static final Method GET_LOCAL_DELEGATE_METHOD;
	private static final Method GET_HOST_VMID_METHOD;
	private static final Method GET_OBJECT_ID_METHOD;
	private static final Method SET_PERSISTENT_NAME_METHOD;
	private static final Method GET_PERSISTENT_NAME_METHOD;
	static {
		try {
			EQUALS_METHOD = Object.class.getMethod( "equals", Object.class );
			HASHCODE_METHOD = Object.class.getMethod( "hashCode" );

			GET_LOCAL_DELEGATE_METHOD =
				Proxy.class.getMethod( "__intrepid__getLocalDelegate" );
			GET_HOST_VMID_METHOD = Proxy.class.getMethod( "__intrepid__getHostVMID" );
			GET_OBJECT_ID_METHOD = Proxy.class.getMethod( "__intrepid__getObjectID" );
			SET_PERSISTENT_NAME_METHOD =
				Proxy.class.getMethod( "__intrepid__setPersistentName", String.class );
			GET_PERSISTENT_NAME_METHOD =
				Proxy.class.getMethod( "__intrepid__getPersistentName" );
		}
		catch( Exception ex ) {
			throw new IllegalArgumentException(
				"Method not found in static initializer", ex );
		}
	}

	// Set by the SPI when we're deserializing packets so we can tell which instance the
	// proxy is being deserialized into. This helps with finding the appropriate instance
	// to make a call from when there are multiple active instances in a single VM.
	static final ThreadLocal<VMID> DESERIALIZING_VMID = new ThreadLocal<>();


	private VMID vmid;
	private int object_id;

	private TObjectIntMap<MethodIDTemplate> method_to_id_map;
	private final Lock method_map_lock = new ReentrantLock();

	private String persistent_name;

	private volatile Object delegate;

	// VMID of the instance into which this was deserialized
	private transient VMID associated_vmid = null;


	/** FOR EXTERNALIZATION ONLY!!! */
	public ProxyInvocationHandler() {}

	ProxyInvocationHandler( VMID vmid, int object_id,
		TObjectIntMap<MethodIDTemplate> method_to_id_map, String persistent_name,
		Object delegate ) {

		this( vmid, object_id, method_to_id_map, persistent_name, delegate, null );
	}

	ProxyInvocationHandler( VMID vmid, int object_id,
		TObjectIntMap<MethodIDTemplate> method_to_id_map, String persistent_name,
		Object delegate, VMID associated_vmid ) {

		if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Local PIH created for {}\n" +
				"\tVMID / OID: {} / {}\n" +
				"\tPersistent Name: {}\n" +
				"\tMethod map: {}", delegate, vmid, Integer.valueOf( object_id ),
				persistent_name, method_to_id_map );
		}

		this.vmid = vmid;
		this.object_id = object_id;
		this.method_to_id_map = method_to_id_map;
		this.persistent_name = persistent_name;
		this.delegate = delegate;
		this.associated_vmid = associated_vmid;
	}


	@Override
	public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable {
		// Methods from Object are not passed on to the source object.
		if ( method.getDeclaringClass().equals( Object.class ) ) {
			if ( method.equals( HASHCODE_METHOD ) ) {
				return Integer.valueOf( hashCode() );
			}
			else if ( method.equals( EQUALS_METHOD ) ) {
				return Boolean.valueOf( proxyEquals( args[ 0 ] ) );
			}

			return method.invoke( this, args );
		}
		// Handle Proxy methods
		else if ( method.getDeclaringClass().equals( Proxy.class ) ) {
			if ( method.equals( GET_OBJECT_ID_METHOD ) ) {
				return Integer.valueOf( object_id );
			}
			else if ( method.equals( GET_HOST_VMID_METHOD ) ) {
				return vmid;
			}
			else if ( method.equals( GET_LOCAL_DELEGATE_METHOD ) ) {
				if ( delegate != null ) return delegate;

				Intrepid local_instance = Intrepid.findLocalInstance( vmid, true );
				if ( local_instance == null ) return null;

				return local_instance.getLocalCallHandler().getLocalDelegate( object_id );
			}
			else if ( method.equals( SET_PERSISTENT_NAME_METHOD ) ) {
				persistent_name = ( String ) args[ 0 ];
				return null;
			}
			else if ( method.equals( GET_PERSISTENT_NAME_METHOD ) ) {
				return persistent_name;
			}
		}

		// If this is a proxy pointing to a local object and the hasn't been serialized
		// yet (so we still have a pointer to the delegate), short-circuit.
		if ( delegate != null ) {
			try {
				return method.invoke( delegate, args );
			}
			catch( InvocationTargetException ex ) {
				throw ex.getCause();
			}
		}

		MethodIDTemplate template = method_template_local.get();
		if ( template == null ) {
			template = new MethodIDTemplate();
			method_template_local.set( template );
		}
		template.set( method );

		final int method_id;

		method_map_lock.lock();
		try {
			method_id = method_to_id_map.get( template );
		}
		finally {
			method_map_lock.unlock();
		}

		if ( method_id == 0 ) {
			throw new UnknownMethodException( method_id, method );
		}

		try {
			Intrepid local_instance = Intrepid.findLocalInstance( vmid, true );
			if ( local_instance != null ) {
				return local_instance.getLocalCallHandler().invoke( object_id,
					method_id, args, true, persistent_name );
			}
			else {
                Intrepid instance = null;
				// See if we have an instance associated from deserialization
				if ( associated_vmid != null ) {
					instance = Intrepid.findLocalInstance( associated_vmid, false );
				}
				if ( instance == null ) {
	                instance = Intrepid.findInstanceWithRemoteSession( vmid );
				}
				if ( instance == null ) {
					// Try local instances again, this time not trying to shortcut
	                instance = Intrepid.findLocalInstance( vmid, false );
					if ( instance != null ) {
						return instance.getLocalCallHandler().invoke( object_id,
							method_id, args, true, persistent_name );
					}
				}
                if ( instance == null ) {
                    throw new NotConnectedException( vmid );
                }

				try {
					return instance.getRemoteCallHandler().invoke( vmid, object_id,
						method_id, persistent_name, args, method );
				}
				catch( NewIDIndicator id ) {
					if ( LOG.isDebugEnabled() ) {
						LOG.debug( "New IDs received: {} -> {}  and  {} -> {}", vmid,
							id.getNewVMID(), Integer.valueOf( object_id ),
							Integer.valueOf( id.getNewObjectID() ) );
					}

					if ( id.getNewVMID() != null ) vmid = id.getNewVMID();
					object_id = id.getNewObjectID();

					if ( id.isThrown() ) throw ( Throwable ) id.getResult();
					else return id.getResult();
				}
			}
		}
		catch( Throwable t ) {
			if ( !canThrow( method, t.getClass() ) ) {
				throw new IntrepidRuntimeException( t );
			}
			else throw t;
		}
	}


	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		ProxyInvocationHandler that = ( ProxyInvocationHandler ) o;

		if ( object_id != that.object_id ) return false;
		if ( vmid != null ? !vmid.equals( that.vmid ) : that.vmid != null ) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = vmid != null ? vmid.hashCode() : 0;
		result = 31 * result + object_id;
		return result;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder( "Proxy(" );
		if ( persistent_name != null ) {
			builder.append( "\"" ).append( persistent_name ).append( "\"-" );
		}

		builder.append( object_id ).append( "@" ).append( vmid ).append( "):" );


		return builder.toString();
	}


	VMID getVMID() {
		return vmid;
	}

	int getObjectID() {
		return object_id;
	}


	private boolean proxyEquals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || !Intrepid.isProxy( o ) ) return false;

		Proxy that = ( Proxy ) o;

		if ( object_id != that.__intrepid__getObjectID() ) return false;
		if ( vmid != null ? !vmid.equals( that.__intrepid__getHostVMID() ) :
			that.__intrepid__getHostVMID()!= null ) return false;

		return true;
	}


	///////////////////////////////////////////////
	// Externalizable

	@Override
	public void writeExternal( ObjectOutput out ) throws IOException {
		// VERSION
		out.writeByte( 0 );

		// VMID
		out.writeObject( vmid );

		// OBJECT ID
		out.writeInt( object_id );

		// METHOD MAP
		out.writeShort( method_to_id_map.size() );
		TObjectIntIterator<MethodIDTemplate> iterator = method_to_id_map.iterator();
		while ( iterator.hasNext() ) {
			iterator.advance();
			
			out.writeObject( iterator.key() );
			out.writeInt( iterator.value() );
		}

		// PERSISTENT NAME
		if ( persistent_name != null ) {
			out.writeBoolean( true );
			out.writeUTF( persistent_name );
		}
		else out.writeBoolean( false );
	}

	@Override
	public void readExternal( ObjectInput in )
		throws IOException, ClassNotFoundException {

		// VERSION
		in.readByte();

		// VMID
		vmid = ( VMID ) in.readObject();

		// OBJECT ID
		object_id = in.readInt();

		// METHOD MAP
		int size = in.readShort();
		method_to_id_map = new TObjectIntHashMap<>( size );
		for( int i = 0; i < size; i++ ) {
			MethodIDTemplate method = ( MethodIDTemplate ) in.readObject();
			int id = in.readInt();
			method_to_id_map.put( method, id );
		}

		// PERSISTENT NAME
		if ( in.readBoolean() ) persistent_name = in.readUTF();

//		System.out.println( "Remote PIH (" + persistent_name + ") created: " + vmid +
//			" - " + object_id );

		// See if we can track the associated Intrepid instance
		associated_vmid = DESERIALIZING_VMID.get();

		// Register with the lease manager
		LeaseManager.registerProxy( this, vmid, object_id );
	}


	/**
	 * Returns true if the given method can throw the given Throwable without wrapping it
	 * in an UndeclaredThowableException.
	 */
	static boolean canThrow( Method method, Class<? extends Throwable> t ) {
		if ( RuntimeException.class.isAssignableFrom( t ) ) return true;
		if ( Error.class.isAssignableFrom( t ) ) return true;

		Class[] exception_types = method.getExceptionTypes();
		for( Class exception : exception_types ) {
			if ( exception.isAssignableFrom( t ) ) return true;
		}

		return false;
	}
}