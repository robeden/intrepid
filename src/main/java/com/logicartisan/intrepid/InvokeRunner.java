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

import com.logicartisan.common.core.listeners.ListenerSupport;
import com.logicartisan.common.core.thread.ScheduledExecutor;
import com.logicartisan.common.core.thread.ThreadKit;
import com.logicartisan.intrepid.auth.UserContextInfo;
import com.logicartisan.intrepid.exception.IllegalProxyDelegateException;
import com.logicartisan.intrepid.message.InvokeAckIMessage;
import com.logicartisan.intrepid.message.InvokeIMessage;
import com.logicartisan.intrepid.message.InvokeReturnIMessage;
import com.logicartisan.intrepid.spi.IntrepidSPI;
import gnu.trove.map.TIntObjectMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;


/**
 *
 */
class InvokeRunner implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger( InvokeRunner.class );

	private final InvokeIMessage message;
	private final VMID source;
	private final InetAddress source_address;
	private final UserContextInfo user_context;
	private final IntrepidSPI spi;
	private final LocalCallHandler local_handler;
	private final Intrepid instance;

	private final TIntObjectMap<InvokeRunner> runner_map;
	private final Lock runner_map_lock;

	private final ListenerSupport<PerformanceListener,?> performance_listeners;

	private final ScheduledFuture<?> ack_future;


	Thread my_thread;

	InvokeRunner( InvokeIMessage message, VMID source, InetAddress source_address,
		UserContextInfo user_context, IntrepidSPI spi, LocalCallHandler local_handler,
		Intrepid instance, TIntObjectMap<InvokeRunner> runner_map, Lock runner_map_lock,
		ListenerSupport<PerformanceListener,?> performance_listeners, boolean needs_ack,
		byte ack_rate_sec, ScheduledExecutor ack_executor ) {

		this.message = message;
		this.source = source;
		this.source_address = source_address;
		this.user_context = user_context;
		this.spi = spi;
		this.local_handler = local_handler;
		this.instance = instance;
		this.runner_map = runner_map;
		this.runner_map_lock = runner_map_lock;
		this.performance_listeners = performance_listeners;

		if ( needs_ack && ack_rate_sec > 0 ) {
			ack_future = ack_executor.scheduleWithFixedDelay( new AckRunnable(),
				ack_rate_sec & 0xFFL, ack_rate_sec & 0xFFL, TimeUnit.SECONDS );
		}
		else ack_future = null;
	}


	void interrupt() {
		if ( my_thread != null ) my_thread.interrupt();
	}


	@Override
	public void run() {
		long start = 0;
		final boolean perf_stats = message.isServerPerfStatsRequested();
		if ( perf_stats ) start = System.nanoTime();

		my_thread = Thread.currentThread();
		final String original_thread_name = my_thread.getName();
		Object result = null;
		boolean result_was_thrown = false;
		try {
			my_thread.setName( "Intrepid invoke (call:" +
				message.getCallID() + " source: " + source + ")" );

			Integer new_object_id = null;
			try {
				// If the invoke has a persistent name, see if the object has a new ID
				if ( message.getPersistentName() != null ) {
					LocalRegistry registry = local_handler.getLocalRegistry();
					Object object = registry.lookup( message.getPersistentName() );
					if ( object != null && object instanceof Proxy ) {
						new_object_id = Integer.valueOf(
							( ( Proxy ) object ).__intrepid__getObjectID() );
//						System.out.println( "  New ID: " + new_object_id + "  Old ID: " +
//							message.getObjectID() + "  Call ID: " + message.getCallID() );
					}
				}

				IntrepidContext.setCallInfo( instance, source, source_address,
					user_context );
				result = local_handler.invoke(
					new_object_id != null ?
						new_object_id.intValue() : message.getObjectID(),
					message.getMethodID(), message.getArgs(), false,
					message.getPersistentName() );

				if ( result != null ) result = createArgForSerialization( result );
			}
			catch( Throwable throwable ) {
				result = throwable;
				result_was_thrown = true;
			}

			try {
				spi.sendMessage( source, new InvokeReturnIMessage( message.getCallID(),
					result, result_was_thrown, new_object_id, perf_stats ?
					Long.valueOf( System.nanoTime() - start ) : null ), null );
			}
			catch ( Throwable throwable ) {
				if ( LOG.isDebugEnabled() ) {
					LOG.debug(
						"Unable to send return message for call {} to {} (will retry)",
						Integer.valueOf( message.getCallID() ), source,
						throwable );
				}

				if ( throwable instanceof IOException ) {
					result = throwable;
					result_was_thrown = true;
				}

				try {
					spi.sendMessage( source, new InvokeReturnIMessage( message.getCallID(),
						throwable, result_was_thrown, new_object_id, perf_stats ?
							Long.valueOf( System.nanoTime() - start ) : null ), null );
				}
				catch ( Exception e ) {
					if ( LOG.isInfoEnabled() ) {
						LOG.info( "Unable to send return message for call {} to {} " +
							"(will NOT retry)",
							Integer.valueOf( message.getCallID() ), source,
							throwable );
					}
				}
			}
		}
		finally {
			if ( ack_future != null ) {
				ack_future.cancel( false );
			}

			IntrepidContext.clearCallInfo();

			my_thread.setName( original_thread_name );

			// Remove ourselves from the runner_map
			runner_map_lock.lock();
			try {
				runner_map.remove( message.getCallID() );
			}
			finally {
				runner_map_lock.unlock();
			}

			if ( performance_listeners.hasListeners() ) {
				performance_listeners.dispatch().inboundRemoteCallCompleted(
					instance.getLocalVMID(), System.currentTimeMillis(),
					message.getCallID(), result, result_was_thrown );
			}
		}
	}


	private Object createArgForSerialization( Object arg ) {
		if ( !( arg instanceof ForceProxy ) && arg instanceof Serializable ) return arg;

		// Try to wrap the object in a proxy
		try {
			return local_handler.createProxy( arg, null );
		}
		catch( IllegalProxyDelegateException ex ) {
			// skip the switch-out
		}

		return arg;
	}


	private class AckRunnable implements Runnable {
		@Override
		public void run() {
			for( int i = 0; i < 3; i++ ) {
				if ( i != 0 ) {
					ThreadKit.sleep( 500 );
				}

				try {
					spi.sendMessage( source,
						new InvokeAckIMessage( message.getCallID() ), null );

					if ( LOG.isDebugEnabled() ) {
						LOG.debug( "Sent ack for invoke {} to {}",
							Integer.valueOf( message.getCallID() ), source );
					}

					break;
				}
				catch ( IOException e ) {
					LOG.warn( "Unable to send ack for invoke {} to {}. Will retry: {}",
						Integer.valueOf( message.getCallID() ), source,
						Boolean.valueOf( i != 2 ) );
				}
			}
		}
	}
}
