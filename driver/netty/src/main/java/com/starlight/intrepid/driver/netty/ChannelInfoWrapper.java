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
import com.starlight.intrepid.ConnectionListener;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.driver.SessionInfo;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;


/**
 * Wraps a {@link Channel} to implement the {@link SessionInfo} interface.
 */
class ChannelInfoWrapper implements SessionInfo {
	private static final Logger LOG =
		LoggerFactory.getLogger( ChannelInfoWrapper.class );


	private final Channel channel;
	private final Map<VMID, ChannelContainer> session_map;
	private final Map<SocketAddress, ChannelContainer> outbound_session_map;
	private final Map<VMID,VMID> vmid_remap;
	private final Lock map_lock;
	private final ConnectionListener connection_listener;
	private final String connection_type_description;
	private final VMID local_vmid;

	ChannelInfoWrapper(Channel channel, Map<VMID, ChannelContainer> session_map,
					   Map<SocketAddress, ChannelContainer> outbound_session_map,
					   Map<VMID,VMID> vmid_remap, Lock map_lock,
					   ConnectionListener connection_listener, String connection_type_description,
					   VMID local_vmid ) {

		this.channel = channel;
		this.session_map = session_map;
		this.outbound_session_map = outbound_session_map;
		this.vmid_remap = vmid_remap;
		this.map_lock = map_lock;
		this.connection_listener = connection_listener;
		this.connection_type_description = connection_type_description;
		this.local_vmid = local_vmid;
	}


	@Override
	public VMID getVMID() {
		return channel.attr( NettyIntrepidDriver.VMID_KEY ).get();
	}



	@Override
	public void setVMID( VMID vmid, byte ack_rate_sec ) {
		LOG.debug( "setVMID vmid={} ack={} local_vmid={} channel={}",
			vmid, ack_rate_sec, local_vmid, channel );

		// Setting a null VMID is invalid. It will be null by default, but this is okay.
		// Allowing null to be set would allow sessions to disappear from the session_map.
		Objects.requireNonNull( vmid );

		channel.attr( NettyIntrepidDriver.INVOKE_ACK_RATE ).set( ack_rate_sec);

		VMID old_vmid = channel.attr( NettyIntrepidDriver.VMID_KEY ).getAndSet( vmid );

		// If the VMID is unchanged, exit
		if ( Objects.equals( vmid, old_vmid ) ) {
			// Make sure the VMIDFuture is set
			ObjectSlot<VmidOrBust> slot = channel.attr( NettyIntrepidDriver.VMID_SLOT_KEY).get();
			assert slot != null;
			//noinspection ConstantConditions
			if ( slot != null ) slot.set( new VmidOrBust(vmid) );

			// Make sure the session is set in the container
			ChannelContainer container = channel.attr( NettyIntrepidDriver.CONTAINER_KEY ).get();
			if ( container != null ) {
				Channel old_channel = container.setChannel(channel);
				NettyIntrepidDriver.closeChannelIfDifferent(channel, old_channel, 2000 );
			}

			return;
		}

		// If the VMID is different from its original value, update the session map.
		map_lock.lock();
		try {
			ChannelContainer container = channel.attr( NettyIntrepidDriver.CONTAINER_KEY ).get();

			if ( old_vmid != null ) {
				ChannelContainer old_container = session_map.remove( old_vmid );
				if ( container != null ) {
					outbound_session_map.remove( container.getSocketAddress() );
				}
				vmid_remap.put( old_vmid, vmid );

				// Make sure nothing is pointing to the old key
				if ( vmid_remap.containsValue( old_vmid ) ) {
					Set<VMID> to_remap = new HashSet<>();
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
				if ( old_container != null && old_container.getChannel() != null ) {
					Channel old_channel = old_container.getChannel();
					NettyIntrepidDriver.closeChannelIfDifferent(channel, old_channel, 2000 );
				}

//				System.out.println( "Remap is now: " + vmid_remap );
			}

			if ( container != null ) {
				container.setChannel(channel);
				session_map.put( vmid, container );
			}
			else assert false : "Null SessionContainer: " + channel;

			connection_listener.connectionOpened(
				channel.remoteAddress(),
				channel.attr( NettyIntrepidDriver.ATTACHMENT_KEY ).get(), local_vmid,
				vmid, getUserContext(), old_vmid, connection_type_description,
				ack_rate_sec );

			// If the connection wasn't initiated by us and the peer has a server port,
			// see if we already have an established connection to it. If so, blow that
			// connection away and use this one.
			Boolean locally_initiated = channel.attr( NettyIntrepidDriver.LOCAL_INITIATE_KEY ).get();
			Integer peer_server_port = getPeerServerPort();
			SocketAddress channel_remote_address = channel.remoteAddress();
			if ( ( locally_initiated == null || !locally_initiated ) &&
				getPeerServerPort() != null ) {

				SocketAddress search_template;
				if ( channel_remote_address instanceof InetSocketAddress ) {
					search_template = new InetSocketAddress(
						((InetSocketAddress) channel_remote_address).getAddress(),
						peer_server_port);
				}
				else {
					search_template = channel_remote_address;
				}
				ChannelContainer current_ob_container =
					outbound_session_map.get( search_template );
				if ( current_ob_container != container ) {
					outbound_session_map.put( search_template, container );

					if ( current_ob_container != null ) {
						Channel current_channel = current_ob_container.getChannel();

						// In case there are already listeners on this container, point
						// them to the new session.
						current_ob_container.setChannel(channel);

						NettyIntrepidDriver.closeChannelIfDifferent(channel, current_channel, 2000 );
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
		ObjectSlot<VmidOrBust> slot = channel.attr( NettyIntrepidDriver.VMID_SLOT_KEY).get();
		assert slot != null;
		//noinspection ConstantConditions
		if ( slot != null ) slot.set( new VmidOrBust(vmid) );
	}

	@Override
	public Byte getProtocolVersion() {
		return channel.attr( NettyIntrepidDriver.PROTOCOL_VERSION_KEY ).get();
	}

	@Override
	public void setProtocolVersion( Byte version ) {
		channel.attr( NettyIntrepidDriver.PROTOCOL_VERSION_KEY ).set( version );
		LOG.debug( "setProtocolVersion: {}", version );
	}


	@Override
	public UserContextInfo getUserContext() {
		return channel.attr( NettyIntrepidDriver.USER_CONTEXT_KEY ).get();
	}

	@Override
	public void setUserContext( UserContextInfo user_context ) {
		channel.attr( NettyIntrepidDriver.USER_CONTEXT_KEY ).set( user_context );
		LOG.debug( "setUserContext: {}", user_context );
	}


	@Override
	public SocketAddress getRemoteAddress() {
		return channel.remoteAddress();
	}


	@Override
	public Object getSessionSource() {
		return channel;
	}

	@Override
	public Integer getPeerServerPort() {
		return channel.attr( NettyIntrepidDriver.SERVER_PORT_KEY ).get();
	}

	@Override
	public void setPeerServerPort( Integer port ) {
		channel.attr( NettyIntrepidDriver.SERVER_PORT_KEY ).set( port );
	}


	@Override
	public Serializable getReconnectToken() {
		return channel.attr( NettyIntrepidDriver.RECONNECT_TOKEN_KEY ).get();
	}

	@Override
	public void setReconnectToken( Serializable reconnect_token ) {
		channel.attr( NettyIntrepidDriver.RECONNECT_TOKEN_KEY ).set( reconnect_token );
	}


	@Override
	public ScheduledFuture<?> getReconnectTokenRegenerationTimer() {
		return channel.attr( NettyIntrepidDriver.RECONNECT_TOKEN_REGENERATION_TIMER ).get();
	}

	@Override
	public void setReconnectTokenRegenerationTimer( ScheduledFuture<?> timer ) {
		channel.attr( NettyIntrepidDriver.RECONNECT_TOKEN_REGENERATION_TIMER ).set( timer );
	}



	@Override
	public Byte getAckRateSec() {
		return ( Byte ) channel.attr( NettyIntrepidDriver.INVOKE_ACK_RATE ).get();
	}



	@Override
	public String toString() {
		return "IoSessionInfoWrapper{" + ", connection_type_description='" +
			connection_type_description + '\'' + ", local_vmid=" + local_vmid +
			", outbound_session_map=" + outbound_session_map + ", session=" + channel +
			", session_map=" + session_map + ", vmid_remap=" + vmid_remap + '}';
	}
}
