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
import com.logicartisan.common.core.thread.ObjectSlot;
import com.logicartisan.common.core.thread.ScheduledExecutor;
import com.logicartisan.common.core.thread.SharedThreadPool;
import com.starlight.intrepid.auth.*;
import com.starlight.intrepid.driver.*;
import com.starlight.intrepid.exception.*;
import com.starlight.intrepid.message.*;
import com.starlight.locale.FormattedTextResourceKey;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TShortObjectHashMap;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 *
 */
class RemoteCallHandler implements InboundMessageHandler {
	private static final Logger LOG = LoggerFactory.getLogger( RemoteCallHandler.class );


	private static final int MAX_CHANNEL_MESSAGE_DATA_SIZE =
		Integer.getInteger( "intrepid.channel.max_message_data",
		( 1 << 18 ) - 20 ).intValue();  // 256K - 20 bytes

	// Default to 30 seconds, but don't allow anything longer than 2 min
	private static final byte REQUEST_INVOKE_ACK_RATE_SEC = ( byte )
		Math.min( 120,
			Integer.getInteger( "intrepid.req_invoke_ack_rate_sec", 30 ).intValue() );
	static {
		if ( REQUEST_INVOKE_ACK_RATE_SEC < 0 ) {
			throw new IllegalArgumentException(
				"System property \"intrepid.req_invoke_ack_rate_sec\" may not be set " +
				"to a negative number." );
		}
	}

	private static final InvokeCloseFlag SESSION_CLOSED_FLAG = new InvokeCloseFlag();

	private static final InvokeNotAckedFlag NEVER_ACKED_FLAG =
		new InvokeNotAckedFlag( false );
	private static final InvokeNotAckedFlag SUBSEQUENT_NOT_ACKED_FLAG =
		new InvokeNotAckedFlag( true );



	private enum InvokeAttempt { BY_ID, BY_PERSISTENT_NAME }
	private static final InvokeAttempt[] INVOKE_ATTEMPT = InvokeAttempt.values();



	private final AuthenticationHandler auth_handler;
	private final IntrepidDriver spi;
	private final LocalCallHandler local_handler;
	private final VMID local_vmid;
	private final ScheduledExecutor executor;
	private final ChannelAcceptor channel_acceptor;
	private Intrepid instance;		// for call context info

	private final ListenerSupport<PerformanceListener,?> performance_listeners;

	private final AtomicInteger call_id_counter = new AtomicInteger( 0 );

	private final Lock call_wait_map_lock = new ReentrantLock();
	private final TIntObjectMap<CallInfoAndAckControl> call_wait_map =
		new TIntObjectHashMap<>();

	// Map of VMID to active calls
	private final Map<VMID,TIntSet> vmid_call_wait_map = new HashMap<>();


	private final Lock ping_wait_map_lock = new ReentrantLock();
	private final TShortObjectHashMap<ObjectSlot<PingResponseIMessage>> ping_wait_map =
		new TShortObjectHashMap<>();


	private final Lock channel_map_lock = new ReentrantLock();
	private final TIntObjectMap<ObjectSlot<ChannelInitResponseIMessage>> channel_init_wait_map =
		new TIntObjectHashMap<>();

	// Map of active virtual channels
	private final Map<VMID,TShortObjectMap<VirtualByteChannel>> channel_map =
		new HashMap<>();

	private final AtomicInteger channel_id_counter = new AtomicInteger();


	private final TIntObjectMap<InvokeRunner> runner_map =
		new TIntObjectHashMap<>();
	private final Lock runner_map_lock = new ReentrantLock();

	RemoteCallHandler( IntrepidDriver spi, AuthenticationHandler auth_handler,
		LocalCallHandler local_handler, VMID local_vmid, ScheduledExecutor executor,
		ListenerSupport<PerformanceListener, ?> performance_listeners,
		ChannelAcceptor channel_acceptor ) {

		this.auth_handler = auth_handler;
		this.spi = spi;
		this.local_handler = local_handler;
		this.local_vmid = local_vmid;
		this.executor = executor;
		this.channel_acceptor = channel_acceptor;
		this.performance_listeners = performance_listeners;
	}

	void initInstance( Intrepid instance ) {
		this.instance = instance;
	}


	public Object invoke( VMID vmid, int object_id, int method_id, String persistent_name,
		Object[] args, Method method ) throws Throwable {

		if ( vmid.equals( local_vmid ) ) {
			throw new AssertionError(
				"Logic error detected: should not be making call to local instance via " +
				"RemoteCallHandler:" +
				"\n  local_vmid: " + local_vmid +
				"\n  [destination] vmid: " + vmid +
				"\n  object_id: " + object_id +
				"\n  method: " + method +
				"\n  method_id: " + method_id +
				"\n  persistent_name: " + persistent_name +
				"\n  thread: " + Thread.currentThread() );
		}

		int call_id = call_id_counter.getAndIncrement();

		final ObjectSlot<InvokeReturnIMessage> return_slot =
			new ObjectSlot<>();

		// Make sure args are serializable and wrap in proxy if they're not
		if ( args != null && args.length > 0 ) checkArgsForSerialization( args );

		UserContextInfo user_context = null;
		if ( IntrepidContext.isCall() ) {
			user_context = IntrepidContext.getUserInfo();
		}

		final boolean has_perf_listeners = performance_listeners.hasListeners();

		if ( has_perf_listeners ) {
			performance_listeners.dispatch().remoteCallStarted( local_vmid,
				System.currentTimeMillis(), call_id, vmid, object_id, method_id, method,
				args, user_context, null );
		}

		InvokeIMessage message = new InvokeIMessage( call_id, object_id, null, method_id,
			args, user_context, has_perf_listeners );
		AtomicInteger protocol_version_slot = new AtomicInteger();

		CallInfoAndAckControl call_info =
			new CallInfoAndAckControl( executor, return_slot );

		// Put the slot in the call_wait_map
		call_wait_map_lock.lock();
		try {
			call_wait_map.put( call_id, call_info );

			TIntSet call_id_set =
				vmid_call_wait_map.computeIfAbsent( vmid, v -> new TIntHashSet() );
			call_id_set.add( call_id );
		}
		finally {
			call_wait_map_lock.unlock();
		}

		try {
			VMID new_vmid = null;
			Integer new_object_id = null;
			Throwable t = null;
			for( InvokeAttempt attempt : INVOKE_ATTEMPT ) {
				if ( attempt == InvokeAttempt.BY_PERSISTENT_NAME ) {
					if ( persistent_name == null ) break;
					
					if ( has_perf_listeners ) {
						performance_listeners.dispatch().remoteCallStarted( local_vmid,
							System.currentTimeMillis(), call_id, vmid, object_id,
							method_id, method, args, user_context, persistent_name );
					}

					message = new InvokeIMessage( call_id, object_id, persistent_name,
						method_id, args, user_context,
						performance_listeners.hasListeners() );
				}

				Integer call_id_obj = Integer.valueOf( call_id );
				LOG.trace( "Sending message {}", call_id_obj );
				assert message != null;
				assert vmid != null;

				SessionInfo session_info =
					spi.sendMessage( vmid, message, protocol_version_slot );
				LOG.trace( "Message sent: {}", call_id_obj );

				VMID current_vmid = session_info.getVMID();
				if ( current_vmid != null && !current_vmid.equals( vmid ) ) {
					new_vmid = current_vmid;
				}


				// If the connection supports method acks, set up timer to expect it.
				if ( ProtocolVersions.supportsMethodAck(
					protocol_version_slot.byteValue() ) ) {

					Byte session_ack_rate = session_info.getAckRateSec();
					if ( session_ack_rate != null ) {
						call_info.setSessionAckRateSec( session_ack_rate.byteValue() );
					}

					call_info.scheduleAckExpector( true );
				}


				InvokeReturnIMessage return_message;
				try {
					// Wait for the response
					LOG.trace( "Waiting for slot: {}", call_id_obj );
					return_message = return_slot.waitForValue();
				}
				catch( InterruptedException ex ) {
					// send interrupt message
					try {
						spi.sendMessage( vmid,
							new InvokeInterruptIMessage( call_id ), null );
					}
					catch( Exception exc ) {
						// ignore
					}
					throw new InterruptedCallException( ex );
				}

				LOG.trace( "Receive return value for call slot {}: {}",
					call_id_obj, return_message );

				if ( has_perf_listeners ) {
					performance_listeners.dispatch().remoteCallCompleted( local_vmid,
						System.currentTimeMillis(), call_id, return_message.getValue(),
						return_message.isThrown(), return_message.getServerTimeNano() );
				}

				new_object_id = return_message.getNewObjectID();

				if ( return_message.isThrown() ) {
					t = ( Throwable ) return_message.getValue();

					// Append the local call stack
					final StackTraceElement[] local_stack = new Throwable().getStackTrace();
					final StackTraceElement[] remote_stack = t.getStackTrace();
					int new_stack_size = remote_stack.length + local_stack.length;
					// Sanity check to ensure stack doesn't get too huge if things go
					// horribly wrong. Typically this isn't a problem due to stack
					// overflows, but if you end up with something like a loop between
					// VMs, the call can be in different threads which avoids that
					// protection. This doesn't solve that problem, but avoid causing
					// another with a huge stack trace.
					if ( new_stack_size < 2000 ) {
						StackTraceElement[] new_stack =
							new StackTraceElement[ new_stack_size ];
						System.arraycopy(
							remote_stack, 0, new_stack, 0, remote_stack.length );
						new_stack[ remote_stack.length ] = new StackTraceElement(
							" <<< Intrepid", "remote call to " + vmid + " >>>", null, -1 );
						System.arraycopy( local_stack, 1, new_stack,
							remote_stack.length + 1, local_stack.length - 1 );// drop top
						t.setStackTrace( new_stack );
					}

					// Look for an indicator that the object wasn't found. If that's the
					// case and we have a persistent name available (and we haven't tried
					// already), then retry the loop which will retry with the persistent
					// name.
					if ( t instanceof UnknownObjectException &&
						attempt == InvokeAttempt.BY_ID &&
						persistent_name != null ) {

						return_slot.clear();
						//noinspection UnnecessaryContinue
						continue;
					}
					else break;
				}
				else {
					// If there's a new VMID or ObjectID, pass it on
					if ( new_vmid != null || new_object_id != null ) {
						if ( LOG.isDebugEnabled() ) {
							LOG.debug( "Result of call {} indicates new IDs: " +
								"\n\tVMID: {} -> {}" +
								"\n\tOID:  {} -> {}", vmid, new_vmid,
								Integer.valueOf( object_id ), new_object_id );
						}
						throw new NewIDIndicator( new_vmid,
							new_object_id == null ? object_id : new_object_id.intValue(),
							return_message.getValue(), false );
					}
					else return return_message.getValue();
				}
			}

			if ( !( t instanceof InterruptedCallException ) && t instanceof Error ) {
				t = new ServerException( t );
			}

			// If there's a new VMID or ObjectID, pass it on
			if ( new_vmid != null || new_object_id != null ) {
				if ( LOG.isDebugEnabled() ) {
					LOG.debug( "Result of call {} indicates new IDs: " +
						"\n\tVMID: {} -> {}" +
						"\n\tOID:  {} -> {}", vmid, new_vmid,
						Integer.valueOf( object_id ), new_object_id );
				}
				throw new NewIDIndicator( new_vmid,
					new_object_id == null ? object_id : new_object_id.intValue(),
					t, true );
			}
			else throw t;
		}
		finally {
			call_info.dispose();

			// Make sure nothing is left in the call_wait_map
			call_wait_map_lock.lock();
			try {
				call_wait_map.remove( call_id );

				TIntSet call_id_set = vmid_call_wait_map.get( vmid );
				if ( call_id_set != null ) call_id_set.remove( call_id );
			}
			finally {
				call_wait_map_lock.unlock();
			}
		}
	}


	ByteChannel channelCreate( VMID destination, Serializable attachment )
		throws IOException, ChannelRejectedException {

		final int call_id = call_id_counter.getAndIncrement();

		ObjectSlot<ChannelInitResponseIMessage> response_slot =
			new ObjectSlot<>();
		boolean successful = false;
		short channel_id = -1;
		try {
			ChannelInitIMessage channel_init;
			VirtualByteChannel channel;

			channel_map_lock.lock();
			try {
				// Prepare for the response with the call ID
				channel_init_wait_map.put( call_id, response_slot );


				TShortObjectMap<VirtualByteChannel> channel_id_map =
					channel_map.computeIfAbsent( destination,
					k -> new TShortObjectHashMap<>() );

				// Determine a free channel ID	
				while( true ) {
					int channel_id_tmp = channel_id_counter.getAndIncrement();
					if ( channel_id_tmp > Short.MAX_VALUE ) {
						channel_id_counter.set( 0 );
						channel_id_tmp = 0;
					}
	
					if ( channel_id_map.containsKey( ( short ) channel_id_tmp ) ) continue;
	
					channel_id = ( short ) channel_id_tmp;
					break;
				}
				
				channel_init = new ChannelInitIMessage( call_id, attachment, channel_id );
				channel = new VirtualByteChannel( destination, channel_id, this );
				
				// Register the channel so we're ready to receive data immediately
				channel_id_map.put( channel_id, channel );
			}
			finally {
				channel_map_lock.unlock();
			}

			spi.sendMessage( destination, channel_init, null );

			
			ChannelInitResponseIMessage response = response_slot.waitForValue();

			if ( response.isSuccessful() ) {
				if ( performance_listeners.hasListeners() ) {
					performance_listeners.dispatch().virtualChannelOpened(
						local_vmid, destination, channel_id );
				}
				successful = true;

				return channel;
			}
			else throw new ChannelRejectedException( response.getRejectReason() );
		}
		catch( InterruptedException ex ) {
			throw new InterruptedIOException();
		}
		finally {
			if ( !successful ) {
				// Make sure nothing is left in the channel maps
				channel_map_lock.lock();
				try {
					channel_init_wait_map.remove( call_id );
					
					TShortObjectMap<VirtualByteChannel> channel_id_map =
						channel_map.get( destination );
					if ( channel_id_map != null && channel_id != -1 ) {
						channel_id_map.remove( channel_id );
						if ( channel_id_map.isEmpty() ) channel_map.remove( destination );
					}
				}
				finally {
					channel_map_lock.unlock();
				}
			}
		}
	}


	void channelClose( final VMID destination, final short channel_id,
		boolean send_message ) {

		if ( send_message ) {
			try {
				spi.sendMessage( destination,
					new ChannelCloseIMessage( channel_id ), null );
			}
			catch( NotConnectedException ex ) {
				// ignore
			}
			catch ( IOException ex ) {
				LOG.warn( "Unable to send channel close message: {}",
					Short.valueOf( channel_id ), ex );
			}
		}

		channel_map_lock.lock();
		try {
			TShortObjectMap<VirtualByteChannel> channel_id_map =
				channel_map.get( destination );
			if ( channel_id_map != null ) {
				VirtualByteChannel channel = channel_id_map.remove( channel_id );
				if ( channel != null ) channel.closedByPeer( false );
				
				if ( channel_id_map.isEmpty() ) channel_map.remove( destination );
			}
		}
		finally {
			channel_map_lock.unlock();
		}

		if ( performance_listeners.hasListeners() ) {
			performance_listeners.dispatch().virtualChannelClosed( local_vmid,
				destination, channel_id );
		}
	}

	void channelSendData( VMID destination, short channel_id, ByteBuffer data )
		throws IOException {

		if ( !data.hasRemaining() ) return;

		int bytes = data.remaining();

		int original_limit = data.limit();
		while( data.position() < original_limit ) {
			data.limit( Math.min( original_limit,
				data.position() + MAX_CHANNEL_MESSAGE_DATA_SIZE ) );
			if ( LOG.isDebugEnabled() ) {
				LOG.debug( "Sending {} bytes to virtual channel {}",
					Integer.valueOf( data.remaining() ), Short.valueOf( channel_id ) );
			}
			spi.sendMessage( destination,
				new ChannelDataIMessage( channel_id, data ), null );
		}

		if ( performance_listeners.hasListeners() ) {
			performance_listeners.dispatch().virtualChannelDataSent( local_vmid,
				destination, channel_id, bytes );
		}
	}

	long ping( VMID vmid, long timeout, TimeUnit timeout_unit )
		throws TimeoutException, IntrepidRuntimeException, InterruptedException {

		short id = ( short ) call_id_counter.getAndIncrement();

		ObjectSlot<PingResponseIMessage> response_slot =
			new ObjectSlot<>();

		ping_wait_map_lock.lock();
		try {
			ping_wait_map.put( id, response_slot );
		}
		finally {
			ping_wait_map_lock.unlock();
		}

		long start = System.nanoTime();

		try {
			spi.sendMessage( vmid, new PingIMessage( id ), null );

			PingResponseIMessage response =
				response_slot.waitForValue( timeout_unit.toMillis( timeout ) );
			if ( response == null ) throw new TimeoutException();
			else return TimeUnit.NANOSECONDS.toMillis( System.nanoTime() - start );
		}
		catch( IOException ex ) {
			throw new IntrepidRuntimeException( ex );
		}
		finally {
			ping_wait_map_lock.lock();
			try {
				ping_wait_map.remove( id );
			}
			finally {
				ping_wait_map_lock.unlock();
			}
		}
	}



	@Override
	public void validateReceivedMessage( SessionInfo session_info,
		IMessage message, boolean locally_initiated_session )
		throws CloseSessionIndicator {

		// Close messages are always acceptable
		if ( message instanceof SessionCloseIMessage ) return;


		final boolean acceptable_message;

		// If a session IS initialized, anything other than session initialization
		// messages are acceptable.
		if ( session_info.sessionIsInitialized() ) {
			acceptable_message = !
				( ( message instanceof SessionInitIMessage ) ||
				( message instanceof SessionInitResponseIMessage ) );
		}
		// If a session IS NOT initialized, expect a SessionInit/Response message
		else {
			acceptable_message =
				( ( message instanceof SessionInitIMessage ) ||
				( message instanceof SessionInitResponseIMessage ) );

			// TODO: This is the algorithm without session re-init... which I'd like to drop
//			acceptable_message =
//				( locally_initiated_session && message instanceof SessionInitResponseIMessage ) ||
//				( !locally_initiated_session && message instanceof SessionInitIMessage );
		}

		if ( !acceptable_message ) {
			LOG.warn( "Invalid message ({}) received from session " +
				"(locally_initiated_session={}): {}",
				message.getClass().getSimpleName(),
				Boolean.valueOf( locally_initiated_session ),
				session_info );
			throw new CloseSessionIndicator( new SessionCloseIMessage() );
		}
	}



	@Override
	public IMessage receivedMessage( SessionInfo session_info, IMessage message,
		boolean locally_initiated_session ) throws CloseSessionIndicator {

		LOG.trace( "receivedMessage from {}: {}", session_info.getVMID(), message );


		validateReceivedMessage( session_info, message, locally_initiated_session );


		IMessage response = null;
		switch ( message.getType() ) {
			case SESSION_INIT:
				response = handleSessionInit( ( SessionInitIMessage ) message,
					session_info );
				break;

			case SESSION_INIT_RESPONSE:
				handleSessionInitResponse( ( SessionInitResponseIMessage ) message,
					session_info );
				break;

			case SESSION_TOKEN_CHANGE:
				handleSessionTokenChange( ( SessionTokenChangeIMessage ) message,
					session_info );
				break;

			case SESSION_CLOSE:
				handleSessionClose( ( SessionCloseIMessage ) message );
				break;

			case INVOKE:
				handleInvoke( ( InvokeIMessage ) message, session_info );
				break;

			case INVOKE_RETURN:
				handleInvokeReturn( ( InvokeReturnIMessage ) message );
				break;

			case INVOKE_INTERRUPT:
				handleInvokeInterrupt( ( InvokeInterruptIMessage ) message );
				break;

			case INVOKE_ACK:
				handlerInvokeAck( ( InvokeAckIMessage ) message, session_info.getVMID() );
				break;

			case LEASE:
				LeaseManager.handleLease( ( LeaseIMessage ) message, local_handler,
					session_info.getVMID() );
				break;

			case LEASE_RELEASE:
				LeaseManager.handleLeaseRelease( ( LeaseReleaseIMessage ) message,
					local_handler, session_info.getVMID() );
				break;

			case CHANNEL_INIT:
				response = handleChannelInit( ( ChannelInitIMessage ) message,
					session_info.getVMID() );
				break;

			case CHANNEL_INIT_RESPONSE:
				handleChannelInitResponse( ( ChannelInitResponseIMessage ) message );
				break;

			case CHANNEL_DATA:
				handleChannelData( ( ChannelDataIMessage ) message, session_info.getVMID() );
				break;

			case CHANNEL_CLOSE:
				handleChannelClose( ( ChannelCloseIMessage ) message,
					session_info.getVMID() );
				break;

			case PING:
				response = handlePing( ( PingIMessage ) message );
				break;

			case PING_RESPONSE:
				handlePingResponse( ( PingResponseIMessage ) message );
				break;

			default:
				assert false : "Unknown type: " + message.getType();
				throw new CloseSessionIndicator( new SessionCloseIMessage(
					new FormattedTextResourceKey( Resources.UNKNOWN_MESSAGE_TYPE,
					message.getType().name() ), false ) );
		}

		return response;
	}

	@Override
	public boolean sessionClosed( SessionInfo session_info, boolean opened_locally,
		boolean closed_locally, boolean can_reconnect ) {

		VMID vmid = session_info.getVMID();
		if ( vmid != null ) {
			// Close active method calls
			call_wait_map_lock.lock();
			try {
				TIntSet call_id_set = vmid_call_wait_map.get( vmid );
				if ( call_id_set != null && !call_id_set.isEmpty() ) {
					call_id_set.forEach( new CallInterruptProcedure() );
				}
			}
			finally {
				call_wait_map_lock.unlock();
			}

			// Close active virtual channels
			TShortObjectMap<VirtualByteChannel> channel_id_map;
			channel_map_lock.lock();
			try {
				channel_id_map = channel_map.remove( vmid );
			}
			finally {
				channel_map_lock.unlock();
			}

			if ( channel_id_map != null ) {
				channel_id_map.forEachValue( new ChannelCloseProcedure() );
			}
		}

		// Attempt reconnection if:
		//  1) It's possible
		//  2) We initiated the connection originally
		//  3) We didn't close the connection
		return can_reconnect && opened_locally && !closed_locally;
	}


	@Override
	public IMessage sessionOpened( SessionInfo session_info, boolean opened_locally,
		ConnectionArgs connection_args ) throws CloseSessionIndicator {

		// Only care about sessions we opened
		if ( !opened_locally ) return null;

		return new SessionInitIMessage( local_vmid, spi.getServerPort(),
			connection_args, ProtocolVersions.MIN_PROTOCOL_VERSION,
			ProtocolVersions.PROTOCOL_VERSION, session_info.getReconnectToken(),
			REQUEST_INVOKE_ACK_RATE_SEC );
	}


	private IMessage handleSessionInit( SessionInitIMessage message,
		SessionInfo session_info ) throws CloseSessionIndicator {

		if ( auth_handler == null ) {
			throw new CloseSessionIndicator( new SessionCloseIMessage(
				Resources.ERROR_CLIENT_CONNECTIONS_NOT_ALLOWED_NO_AUTH_HANDLER, true ) );
		}

		byte proto_version = ProtocolVersions.negotiateProtocolVersion(
			message.getMinProtocolVersion(), message.getPrefProtocolVersion() );
		if ( proto_version < 0 ) {
			throw new CloseSessionIndicator( new SessionCloseIMessage(
				new FormattedTextResourceKey( Resources.INCOMPATIBLE_PROTOCOL_VERSION,
				Byte.valueOf( ProtocolVersions.MIN_PROTOCOL_VERSION ),
				Byte.valueOf( ProtocolVersions.PROTOCOL_VERSION ),
				Byte.valueOf( message.getMinProtocolVersion() ),
				Byte.valueOf( message.getPrefProtocolVersion() ) ), false ) );
		}

		// "Normal" connection...
		UserContextInfo user_context;
		Serializable reconnect_token = null;
		try {
			// Session token reconnection added in protocol version 1.
			if ( ProtocolVersions.supportsReconnectTokens( proto_version ) &&
				auth_handler instanceof TokenReconnectAuthenticationHandler ) {

				TokenReconnectAuthenticationHandler token_handler =
					( TokenReconnectAuthenticationHandler ) auth_handler;
				user_context = token_handler.checkConnection( message.getConnectionArgs(),
					session_info.getRemoteAddress(), session_info.getSessionSource(),
					message.getReconnectToken() );

				reconnect_token = generateReconnectToken( session_info, token_handler,
					message.getInitiatorVMID(), user_context, message.getConnectionArgs(),
					session_info.getRemoteAddress(), session_info.getSessionSource(),
					message.getReconnectToken(), false );
			}
			else {
				user_context = auth_handler.checkConnection( message.getConnectionArgs(),
					session_info.getRemoteAddress(), session_info.getSessionSource() );
			}
		}
		catch( ConnectionAuthFailureException ex ) {
			throw new CloseSessionIndicator(
				new SessionCloseIMessage( ex.getMessageResourceKey(), true ) );
		}

		session_info.setProtocolVersion( Byte.valueOf( proto_version ) );
		// NOTE: set user context first, so it's available when the connectionOpened
		//       message is fired.
		session_info.setUserContext( user_context );
		// NOTE: MUST come before setVMID
		session_info.setPeerServerPort( message.getInitiatorServerPort() );


		byte ack_rate = REQUEST_INVOKE_ACK_RATE_SEC;
		if ( message.getRequestedAckRateSec() > 0 &&
			message.getRequestedAckRateSec() < ack_rate ) {

			ack_rate = message.getRequestedAckRateSec();
		}

		session_info.setVMID( message.getInitiatorVMID(), ack_rate );

		return new SessionInitResponseIMessage( local_vmid, spi.getServerPort(),
			proto_version, reconnect_token, ack_rate );
	}

	private void handleSessionInitResponse( SessionInitResponseIMessage message,
		SessionInfo session_info ) {

		byte ack_rate = message.getAckRateSec();
		if ( ack_rate <= 0 ) ack_rate = REQUEST_INVOKE_ACK_RATE_SEC;

		session_info.setProtocolVersion( Byte.valueOf( message.getProtocolVersion() ) );
		session_info.setVMID( message.getResponderVMID(), ack_rate );
		session_info.setReconnectToken( message.getReconnectToken() );
	}

	private void handleSessionTokenChange( SessionTokenChangeIMessage message,
		SessionInfo session_info ) {

		session_info.setReconnectToken( message.getNewReconnectToken() );

		if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Session reconnect token changed for connection to {}: {}",
				session_info.getVMID(), message.getNewReconnectToken() );
		}
	}

	private void handleSessionClose( SessionCloseIMessage message )
		throws CloseSessionIndicator {

//		System.out.println( "Notified of close for session with " + info.getVMID() +
//			": " + message.getReason() );
		throw new CloseSessionIndicator( message.getReason() );
	}

	private void handleInvoke( InvokeIMessage message,
		SessionInfo session_info ) {

		LOG.trace( "Invoke: {} (protocol version={})", message,
			session_info.getProtocolVersion() );

		// Use the messages call context, unless a context has already been set for the
		// session. In other words, only allow an overriding context if this is a server
		// connection.
		UserContextInfo context_info = session_info.getUserContext();
		if ( context_info == null ) context_info = message.getUserContext();

		if ( performance_listeners.hasListeners() ) {
			performance_listeners.dispatch().inboundRemoteCallStarted( local_vmid,
				System.currentTimeMillis(), message.getCallID(), session_info.getVMID(),
				message.getObjectID(), message.getMethodID(),
				local_handler.lookupMethodForID( message.getObjectID(),
					message.getMethodID() ),
				message.getArgs(), context_info, message.getPersistentName() );
		}

		InetAddress source_address = null;
		SocketAddress sock_addr = session_info.getRemoteAddress();
		if ( sock_addr != null && sock_addr instanceof InetSocketAddress ) {
			source_address = ( ( InetSocketAddress ) sock_addr ).getAddress();
		}

		Byte protocol_version = session_info.getProtocolVersion();
		assert protocol_version != null :
			"Unknown protocol version for session: " + session_info;
		//noinspection ConstantConditions
		boolean needs_ack = protocol_version == null ||
			ProtocolVersions.supportsMethodAck( protocol_version.byteValue() );

		Byte ack_rate = session_info.getAckRateSec();

		InvokeRunner runner = new InvokeRunner( message, session_info.getVMID(),
			source_address, context_info, spi, local_handler, instance, runner_map,
			runner_map_lock, performance_listeners, needs_ack,
			ack_rate == null ? REQUEST_INVOKE_ACK_RATE_SEC : ack_rate.byteValue(),
			executor );

		runner_map_lock.lock();
		try {
			runner_map.put( message.getCallID(), runner );
		}
		finally {
			runner_map_lock.unlock();
		}

		executor.execute( runner );
	}

	private void handleInvokeReturn( InvokeReturnIMessage message ) {
		CallInfoAndAckControl call_info;

		LOG.trace( "Invoke return: {}", message );

		call_wait_map_lock.lock();
		try {
			call_info = call_wait_map.get( message.getCallID() );
		}
		finally {
			call_wait_map_lock.unlock();
		}

		if ( call_info == null ) {
			if ( LOG.isDebugEnabled() ) {
				LOG.debug( "No info found for call {}, message: {}",
					Integer.valueOf( message.getCallID() ), message );
			}
			return;
		}

		call_info.return_slot.set( message );
	}

	private void handleInvokeInterrupt( InvokeInterruptIMessage message ) {
		LOG.trace( "Invoke interrupt: {}", message );

		InvokeRunner runner;
		runner_map_lock.lock();
		try {
			runner = runner_map.get( message.getCallID() );
		}
		finally {
			runner_map_lock.unlock();
		}

		if ( runner != null ) runner.interrupt();
	}


	private void handlerInvokeAck( InvokeAckIMessage message, VMID caller_vmid ) {
		LOG.trace( "Invoke ack from {}: {}", caller_vmid, message );

		CallInfoAndAckControl call_info;
		call_wait_map_lock.lock();
		try {
			call_info = call_wait_map.get( message.getCallID() );
		}
		finally {
			call_wait_map_lock.unlock();
		}

		if ( call_info == null ) {
			if ( LOG.isDebugEnabled() ) {
				LOG.debug( "No info found for call {}, message: {}",
					Integer.valueOf( message.getCallID() ), message );
			}
			return;
		}

		call_info.scheduleAckExpector( false );
	}


	private ChannelInitResponseIMessage handleChannelInit( ChannelInitIMessage message,
		VMID vmid ) {

		if ( channel_acceptor == null ) {
			return new ChannelInitResponseIMessage( message.getRequestID(), null );
		}

		VirtualByteChannel channel;
		final short channel_id = message.getChannelID();

		channel_map_lock.lock();
		try {
			TShortObjectMap<VirtualByteChannel> channel_id_map =
				channel_map.computeIfAbsent( vmid, k -> new TShortObjectHashMap<>() );

			channel = new VirtualByteChannel( vmid, message.getChannelID(), this );

			VirtualByteChannel prev_channel = channel_id_map.put( channel_id, channel );
			if ( prev_channel != null ) {
				assert false : "Duplicate channel ID " + channel_id;
				LOG.warn( "Duplicate channel ID: {}", Short.valueOf( channel_id ) );
				prev_channel.closedByPeer( true );
			}
		}
		finally {
			channel_map_lock.unlock();
		}

		try {
			// See if the channel is approved
			channel_acceptor.newChannel( channel, vmid, message.getAttachment() );
		}
		catch( ChannelRejectedException ex ) {
			// Don't close the channel directly, but call our close method (without
			// sending a message) to clean up internal maps.
			channelClose( vmid, channel_id, false );

			// NOTE: no need to close VirtualByteChannel
			return new ChannelInitResponseIMessage( message.getRequestID(),
				ex.getMessageResourceKey() );
		}

		if ( performance_listeners.hasListeners() ) {
			performance_listeners.dispatch().virtualChannelOpened( local_vmid,
				vmid, channel_id );
		}

		return new ChannelInitResponseIMessage( message.getRequestID() );
	}

	private void handleChannelInitResponse( ChannelInitResponseIMessage message ) {
		channel_map_lock.lock();
		try {
			ObjectSlot<ChannelInitResponseIMessage> response_slot =
				channel_init_wait_map.get( message.getRequestID() );

			if ( response_slot != null ) response_slot.set( message );
		}
		finally {
			channel_init_wait_map.remove( message.getRequestID() );

			channel_map_lock.unlock();
		}
	}

	private void handleChannelData( ChannelDataIMessage message, VMID vmid ) {
		VirtualByteChannel channel = null;
		channel_map_lock.lock();
		try {
			TShortObjectMap<VirtualByteChannel> channel_id_map = channel_map.get( vmid );
			if ( channel_id_map != null ) {
				channel = channel_id_map.get( message.getChannelID() );
			}
		}
		finally {
			channel_map_lock.unlock();
		}

		if ( channel == null ) return;      // Log? Send error?

		// NOTE: VirtualByteChannel currently hangs on to these buffers
		final int buffers = message.getBufferCount();
		int bytes = 0;
		for( int i = 0; i < buffers; i++ ) {
			ByteBuffer buffer = message.getBuffer( i );
			bytes += buffer.remaining();
			channel.putData( buffer );
		}

		if ( performance_listeners.hasListeners() ) {
			performance_listeners.dispatch().virtualChannelDataReceived( local_vmid,
				vmid, message.getChannelID(), bytes );
		}
	}

	private void handleChannelClose( ChannelCloseIMessage message, VMID vmid ) {
		channelClose( vmid, message.getChannelID(), false );
	}

	private PingResponseIMessage handlePing( PingIMessage message ) {
		return new PingResponseIMessage( message.getSequenceNumber() );
	}

	private void handlePingResponse( PingResponseIMessage message ) {
		ping_wait_map_lock.lock();
		try {
			ObjectSlot<PingResponseIMessage> slot =
				ping_wait_map.get( message.getSequenceNumber() );
			if ( slot != null ) slot.set( message );
		}
		finally {
			ping_wait_map_lock.unlock();
		}
	}


	private Serializable generateReconnectToken( SessionInfo session_info,
		TokenReconnectAuthenticationHandler token_handler, VMID vmid,
		UserContextInfo user_context, ConnectionArgs connection_args,
		SocketAddress remote_address, Object session_source,
		Serializable previous_reconnect_token, boolean send_token_change_message ) {


		Serializable reconnect_token = token_handler.generateReconnectToken( user_context,
			connection_args, remote_address, session_source, previous_reconnect_token );

		int regeneration_interval_sec =
			token_handler.getTokenRegenerationInterval();

		TokenRegenerator regenerator = new TokenRegenerator( session_info, token_handler,
			vmid, user_context, connection_args, remote_address, session_source,
			reconnect_token );

		ScheduledFuture<?> future =
			SharedThreadPool.INSTANCE.schedule( regenerator, regeneration_interval_sec,
			TimeUnit.SECONDS );
		session_info.setReconnectTokenRegenerationTimer( future );

		if ( send_token_change_message ) {
			for( int i = 2; i >= 0; i-- ) {
				try {
					spi.sendMessage( vmid,
						new SessionTokenChangeIMessage( reconnect_token ), null );
					break;
				}
				catch ( IOException e ) {
					String message = "Unable to send SessionTokenChange message";
					if ( i == 0 ) message += " (will NOT retry)";
					else message += " (will retry)";

					LOG.warn( message, e );
				}
			}
		}

		return reconnect_token;
	}


	private void checkArgsForSerialization( Object[] args ) {
		Class array_type = null;

		for( int i = 0; i < args.length; i++ ) {
			Object arg = args[ i ];
			if ( arg == null ) continue;

			if ( !( arg instanceof ForceProxy ) && arg instanceof Serializable ) continue;

			// Figure out the array type if we haven't already
			if ( array_type == null ) {
				array_type = args.getClass().getComponentType();
			}

			// Try to wrap the object in a proxy
			try {
				Proxy proxy = local_handler.createProxy( arg, null );
				if ( array_type != null && proxy != null &&
					array_type.isAssignableFrom( proxy.getClass() ) ) {
					
//					System.out.println( "Replacing argument " + i + " (" + args[ i ] +
//						") with: " + proxy );
					args[ i ] = proxy;
				}
			}
			catch( IllegalProxyDelegateException ex ) {
				// skip the switch-out
			}
		}
	}


	Registry getRemoteRegistry( VMID vmid ) {
		if ( !spi.hasConnection( vmid ) ) {
			throw new NotConnectedException( vmid );
		}

		TObjectIntMap<MethodIDTemplate> method_map = MethodMap.generateReverseMethodMap(
			MethodMap.generateMethodMap( Registry.class ) );

		Object proxy = java.lang.reflect.Proxy.newProxyInstance(
			Registry.class.getClassLoader(), new Class[] { Proxy.class, Registry.class },
			new ProxyInvocationHandler( vmid, 0, method_map, null, null, local_vmid ) );
		return ( Registry ) proxy;
	}


	private class CallInterruptProcedure implements TIntProcedure {
		@Override
		public boolean execute( int call_id ) {
			CallInfoAndAckControl call_info = call_wait_map.get( call_id );
			call_info.return_slot.set( SESSION_CLOSED_FLAG );
			return true;
		}
	}


	private class ChannelCloseProcedure implements TObjectProcedure<VirtualByteChannel> {
		@Override
		public boolean execute( VirtualByteChannel channel ) {
			if ( channel != null ) channel.closedByPeer( true );
			return true;
		}
	}


	private static class InvokeCloseFlag extends InvokeReturnIMessage {
		InvokeCloseFlag() {
			//noinspection ThrowableResultOfMethodCallIgnored
			super( -1, buildException(), true, null, null );
		}


		private static InterruptedCallException buildException() {
			InterruptedCallException ex = new InterruptedCallException(
				"Connection to peer closed during method invocation" );

			// Erase the stack because this exception is build ahead of time and reused.
			// So, the stack is pretty pointless (and confusing) when viewed.
			ex.setStackTrace( new StackTraceElement[ 0 ] );
			return ex;
		}
	}


	/**
	 * Used to signal that an invocation did not received the expected acknowlegement from
	 * the server.
	 */
	private static class InvokeNotAckedFlag extends InvokeReturnIMessage {

		InvokeNotAckedFlag( boolean received_any_acks ) {
			//noinspection ThrowableResultOfMethodCallIgnored
			super( -1, buildException( received_any_acks ), true, null, null );
		}


		private static MethodInvocationFailedException buildException(
			boolean received_any_acks ) {

			String message;
			if ( received_any_acks ) {
				message = "Message acknowledgement timeout exceeded";
			}
			else {
				message = "Initial message acknowledgement timeout exceeded";
			}
			MethodInvocationFailedException ex =
				new MethodInvocationFailedException( message );

			// Erase the stack because this exception is build ahead of time and reused.
			// So, the stack is pretty pointless (and confusing) when viewed.
			ex.setStackTrace( new StackTraceElement[ 0 ] );
			return ex;
		}
	}


	private class TokenRegenerator implements Runnable {
		private final SessionInfo session_info;
		private final TokenReconnectAuthenticationHandler auth_handler;
		private final VMID vmid;
		private final UserContextInfo user_context;
		private final ConnectionArgs connection_args;
		private final SocketAddress remote_address;
		private final Object session_source;
		private final Serializable previous_reconnect_token;

		TokenRegenerator( SessionInfo session_info,
			TokenReconnectAuthenticationHandler auth_handler, VMID vmid,
			UserContextInfo user_context, ConnectionArgs connection_args,
			SocketAddress remote_address, Object session_source,
			Serializable previous_reconnect_token ) {

			this.session_info = session_info;
			this.auth_handler = auth_handler;
			this.vmid = vmid;
			this.user_context = user_context;
			this.connection_args = connection_args;
			this.remote_address = remote_address;
			this.session_source = session_source;
			this.previous_reconnect_token = previous_reconnect_token;
		}


		@Override
		public void run() {
			Thread.currentThread().setName( "Intrepid Session TokenRegenerator: " +
				remote_address );

			try {
				generateReconnectToken( session_info, auth_handler, vmid, user_context,
					connection_args, remote_address, session_source,
					previous_reconnect_token, true );
			}
			catch( Throwable t ) {
				LOG.warn( "Unexpected error regenerating reconnection token", t );
			}
		}
	}


	/**
	 * This object serves two purposes:
	 * <ol>
	 * <li>It is the data container for information relating to a call invocation</li>
	 * <li>It is the Runnable that is executed if the call is not ack'ed by the server
	 *     in a proper amount of time</li>
	 * </ol>
	 */
	private static class CallInfoAndAckControl implements Runnable {
		long ack_deadline_ms;       // WARNING: milliseconds

		final ScheduledExecutor executor;
		final ObjectSlot<InvokeReturnIMessage> return_slot;

		private volatile boolean received_an_ack = false;
		private volatile boolean aborted_by_ack_fail = false;

		ScheduledFuture<?> ack_expect_future;
		final Lock ack_handler_lock = new ReentrantLock();


		private CallInfoAndAckControl( @Nonnull ScheduledExecutor executor,
			@Nonnull ObjectSlot<InvokeReturnIMessage> return_slot ) {

			this.executor = Objects.requireNonNull( executor );
			this.return_slot = Objects.requireNonNull( return_slot );

			// Default, should be overridden
			setSessionAckRateSec( REQUEST_INVOKE_ACK_RATE_SEC );
		}


		void setSessionAckRateSec( byte ack_sec ) {
			// WARNING: Converting to milliseconds
			ack_deadline_ms = Math.round( TimeUnit.SECONDS.toMillis( ack_sec ) * 3.5 );
		}


		void scheduleAckExpector( boolean first ) {
			if ( !first ) received_an_ack = true;

			ack_handler_lock.lock();
			try {
				// Do nothing if we've already killed the call due to missing an ack
				if ( aborted_by_ack_fail ) return;

				if ( ack_expect_future != null ) {
					ack_expect_future.cancel( false );
				}

				// NOTE: time is in milliseconds
				ack_expect_future =
					executor.schedule( this, ack_deadline_ms, TimeUnit.MILLISECONDS );
			}
			finally {
				ack_handler_lock.unlock();
			}
		}


		/**
		 * Called when a call has not been acknowledged within the deadline.
		 */
		@Override
		public void run() {
			InvokeNotAckedFlag flag;
			if ( received_an_ack ) {
				flag = SUBSEQUENT_NOT_ACKED_FLAG;
			}
			else flag = NEVER_ACKED_FLAG;


			ack_handler_lock.lock();
			try {
				return_slot.compareAndSet( ( InvokeReturnIMessage ) null, flag );
				aborted_by_ack_fail = true;
				ack_expect_future = null;
			}
			finally {
				ack_handler_lock.unlock();
			}
		}


		void dispose() {
			final ScheduledFuture<?> future = ack_expect_future;
			if ( future != null ) future.cancel( false );
		}
	}
}