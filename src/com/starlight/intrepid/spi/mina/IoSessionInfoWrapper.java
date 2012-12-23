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

import com.starlight.MiscKit;
import com.starlight.ValidationKit;
import com.starlight.intrepid.ConnectionListener;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.spi.SessionInfo;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.nio.NioSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;


/**
 * Wraps an {@link IoSession} to implement the {@link SessionInfo} interface.
 */
class IoSessionInfoWrapper implements SessionInfo {
	private static final Logger LOG =
		LoggerFactory.getLogger( IoSessionInfoWrapper.class );

	private static final Method nioSessionGetChannelMethod;

	static {
		Method m = null;
		try {
			// WARNING!!!
			// Use magical reflection powers to get at the underlying SocketChannel. This
			// can fail if a SecurityManager is installed.
			// This is dependent on MINA internals and could break with newer versions!
			m = NioSession.class.getDeclaredMethod( "getChannel" );
			m.setAccessible( true );
		}
		catch( Exception ex ) {
			// ignore
		}
		nioSessionGetChannelMethod = m;
	}


	private final IoSession session;
	private final Map<VMID,SessionContainer> session_map;
	private final Map<HostAndPort,SessionContainer> outbound_session_map;
	private final Map<VMID,VMID> vmid_remap;
	private final Lock map_lock;
	private final ConnectionListener connection_listener;
	private final String connection_type_description;
	private final VMID local_vmid;

	IoSessionInfoWrapper( IoSession session, Map<VMID,SessionContainer> session_map,
		Map<HostAndPort,SessionContainer> outbound_session_map,
		Map<VMID,VMID> vmid_remap, Lock map_lock,
		ConnectionListener connection_listener, String connection_type_description,
		VMID local_vmid ) {

		this.session = session;
		this.session_map = session_map;
		this.outbound_session_map = outbound_session_map;
		this.vmid_remap = vmid_remap;
		this.map_lock = map_lock;
		this.connection_listener = connection_listener;
		this.connection_type_description = connection_type_description;
		this.local_vmid = local_vmid;
	}


	@Override
	public Object getAttribute( Object key ) {
		assert !( key instanceof String ) || !( ( String ) key ).startsWith( "." ) :
			"String keys may not start with '.'";
		return session.getAttribute( key );
	}

	@Override
	public Object setAttribute( Object key, Object value ) {
		LOG.debug( "MINA.SessionInfo setAttribute: {} = {}", key, value );
		assert !( key instanceof String ) || !( ( String ) key ).startsWith( "." ) :
			"String keys may not start with '.'";
		return session.setAttribute( key, value );
	}

	@Override
	public VMID getVMID() {
		return ( VMID ) session.getAttribute( MINAIntrepidSPI.VMID_KEY );
	}

	@Override
	public void setVMID( VMID vmid ) {
		// Setting a null VMID is invalid. It will be null by default, but this is okay.
		// Allowing null to be set would allow sessions to disappear from the session_map.
		ValidationKit.checkNonnull( vmid, "VMID" );

		VMID old_vmid = ( VMID ) session.setAttribute( MINAIntrepidSPI.VMID_KEY, vmid );

		if ( LOG.isDebugEnabled() ) {
			StringBuilder buf = new StringBuilder();
			boolean first = true;
			for( Object key : session.getAttributeKeys() ) {
				if ( first ) first = false;
				else buf.append( ", " );
				buf.append( key );
				buf.append( "=" );
				buf.append( session.getAttribute( key ) );
			}
			LOG.debug( "MINA.SessionInfo setVMID: {} old_vmid: {} attributes: {}",
				new Object[] { vmid, old_vmid, buf.toString() } );
		}

		// If the VMID is unchanged, exit
		if ( MiscKit.equal( vmid, old_vmid ) ) {
			// Make sure the VMIDFuture is set
			VMIDFuture future =
				( VMIDFuture ) session.getAttribute( MINAIntrepidSPI.VMID_FUTURE_KEY );
			assert future != null;
			if ( future != null ) future.setVMID( vmid );

			// Make sure the session is set in the container
			SessionContainer container =
				( SessionContainer ) session.getAttribute( MINAIntrepidSPI.CONTAINER_KEY );
			if ( container != null ) {
				IoSession old_session = container.setSession( session );
				MINAIntrepidSPI.closeSessionIfDifferent( session, old_session, 2000 );
			}

			return;
		}

		// If the VMID is different from its original value, update the session map.
		map_lock.lock();
		try {
			SessionContainer container = ( SessionContainer )
				session.getAttribute( MINAIntrepidSPI.CONTAINER_KEY );

			if ( old_vmid != null ) {
				SessionContainer old_container = session_map.remove( old_vmid );
				if ( container != null ) {
					outbound_session_map.remove( container.getHostAndPort() );
				}
				vmid_remap.put( old_vmid, vmid );

				// Make sure nothing is pointing to the old key
				if ( vmid_remap.containsValue( old_vmid ) ) {
					Set<VMID> to_remap = new HashSet<VMID>();
					for( Map.Entry<VMID,VMID> entry : vmid_remap.entrySet() ) {
						if ( entry.getValue().equals( old_vmid ) ) {
							to_remap.add( entry.getKey() );
						}
					}
					for( VMID old_old_vmid : to_remap ) {
						vmid_remap.put( old_old_vmid, vmid );
					}
				}

				// Make sure any old connections are cleaned up
				if ( old_container != null && old_container.getSession() != null ) {
					IoSession old_session = old_container.getSession();
					MINAIntrepidSPI.closeSessionIfDifferent( session, old_session, 2000 );
				}

//				System.out.println( "Remap is now: " + vmid_remap );
			}

			if ( container != null ) {
				container.setSession( session );
				session_map.put( vmid, container );

				InetSocketAddress address =
					( InetSocketAddress ) session.getRemoteAddress();
				connection_listener.connectionOpened( address.getAddress(),
					address.getPort(),
					session.getAttribute( MINAIntrepidSPI.ATTACHMENT_KEY ), local_vmid, vmid,
					getUserContext(), old_vmid,
					connection_type_description );
			}
			else assert false : "Null SessionContainer: " + session;

			// If the connection wasn't initiated by us and the peer has a server port,
			// see if we already have an established connection to it. If so, blow that
			// connection away and use this one.
			Boolean locally_initiated =
				( Boolean ) session.getAttribute( MINAIntrepidSPI.LOCAL_INITIATE_KEY );
			if ( ( locally_initiated == null || !locally_initiated.booleanValue() ) &&
				getPeerServerPort() != null ) {

				HostAndPort search_template = new HostAndPort(
					( ( InetSocketAddress ) session.getRemoteAddress() ).getAddress(),
					getPeerServerPort().intValue() );
				SessionContainer current_ob_container =
					outbound_session_map.get( search_template );
				if ( current_ob_container != container ) {
					outbound_session_map.put( search_template, container );

					if ( current_ob_container != null ) {
						IoSession current_session = current_ob_container.getSession();

						// In case there are already listeners on this container, point
						// them to the new session.
						current_ob_container.setSession( session );

						MINAIntrepidSPI.closeSessionIfDifferent( session,
							current_session, 2000 );
					}
				}
			}

		}
		finally {
			map_lock.unlock();
		}

		// NOTE: must come after putting the session in the map to avoid a race condition
		//       where the VMID is returned from the connect method before the connection
		//       is available in the map.
		VMIDFuture future =
			( VMIDFuture ) session.getAttribute( MINAIntrepidSPI.VMID_FUTURE_KEY );
		assert future != null;
		if ( future != null ) future.setVMID( vmid );
	}

	@Override
	public Byte getProtocolVersion() {
		return ( Byte ) session.getAttribute( MINAIntrepidSPI.PROTOCOL_VERSION_KEY );
	}

	@Override
	public void setProtocolVersion( Byte version ) {
		session.setAttribute( MINAIntrepidSPI.PROTOCOL_VERSION_KEY, version );
		LOG.debug( "MINA.SessionInfo setProtocolVersion: {}", version );
	}


	@Override
	public UserContextInfo getUserContext() {
		return ( UserContextInfo ) session.getAttribute( MINAIntrepidSPI.USER_CONTEXT_KEY );
	}

	@Override
	public void setUserContext( UserContextInfo user_context ) {
		session.setAttribute( MINAIntrepidSPI.USER_CONTEXT_KEY, user_context );
		LOG.debug( "MINA.SessionInfo setUserContext: {}", user_context );
	}


	@Override
	public SocketAddress getRemoteAddress() {
		return session.getRemoteAddress();
	}


	@Override
	public Object getSessionSource() {
		try {
			NioSession nio_session = ( NioSession ) session;

			if ( nioSessionGetChannelMethod != null ) {
				return nioSessionGetChannelMethod.invoke( nio_session );
			}
			else return null;
		}
		catch( Exception ex ) {
			return null;
		}
	}

	@Override
	public Integer getPeerServerPort() {
		return ( Integer ) session.getAttribute( MINAIntrepidSPI.SERVER_PORT_KEY );
	}

	@Override
	public void setPeerServerPort( Integer port ) {
		session.setAttribute( MINAIntrepidSPI.SERVER_PORT_KEY, port );
	}


	@Override
	public Serializable getReconnectToken() {
		return ( Serializable ) session.getAttribute(
			MINAIntrepidSPI.RECONNECT_TOKEN_KEY );
	}

	@Override
	public void setReconnectToken( Serializable reconnect_token ) {
		session.setAttribute( MINAIntrepidSPI.RECONNECT_TOKEN_KEY, reconnect_token );
	}


	@Override
	public ScheduledFuture<?> getReconnectTokenRegenerationTimer() {
		return ( ScheduledFuture<?> ) session.getAttribute(
			MINAIntrepidSPI.RECONNECT_TOKEN_REGENERATION_TIMER );
	}

	@Override
	public void setReconnectTokenRegenerationTimer( ScheduledFuture<?> timer ) {
		session.setAttribute( MINAIntrepidSPI.RECONNECT_TOKEN_REGENERATION_TIMER, timer );
	}
}
