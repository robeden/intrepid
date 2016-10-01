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

import com.logicartisan.intrepid.message.LeaseIMessage;
import com.logicartisan.intrepid.spi.IntrepidSPI;
import com.logicartisan.intrepid.exception.NotConnectedException;
import com.logicartisan.intrepid.message.IMessage;
import com.logicartisan.intrepid.message.LeaseReleaseIMessage;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * The LeaseManager is a static class to manage object leases to remote VM's.
 */
class LeaseManager {
	private static final Logger LOG = LoggerFactory.getLogger( LeaseManager.class );

	static final long LEASE_DURATION_MS =
		Long.getLong( "intrepid.lease.duration",
		TimeUnit.MINUTES.toMillis( 10 ) ).longValue();

	static final int LEASE_OVERLAP_MARGIN =
		Integer.getInteger( "intrepid.lease.overlap", 3 ).intValue();

	static final long LEASE_PRUNE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(
		Long.getLong( "intrepid.lease.prune_interval",
		TimeUnit.MINUTES.toMillis( 1 ) ).longValue() );

	static final long LEASE_ERROR_RETRY_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(
		Long.getLong( "intrepid.lease.error_retry_delay", 5000 ).longValue() );

	static final long FORCE_REF_QUEUE_CHECK_TIME_NS = TimeUnit.MILLISECONDS.toNanos(
		Long.getLong( "intrepid.lease.ref_queue_check_force_time", 10000 ).longValue() );

	static final boolean DGC_DISABLED =
		System.getProperty( "intrepid.lease.disable_dgc" ) != null;

	static final boolean LEASE_DEBUGGING =
		System.getProperty( "intrepid.lease.debug" ) != null;
	static final boolean SUPER_LEASE_DEBUGGING =
		"super".equalsIgnoreCase( System.getProperty( "intrepid.lease.debug" ) );

	
	private static final Map<VMID,LocalCallHandler> local_handler_map =
		new HashMap<VMID, LocalCallHandler>();
	private static final Lock local_handler_map_lock = new ReentrantLock();

	private static final Map<VMID,TIntObjectMap<ProxyLeaseInfo>> proxy_lease_map =
		new HashMap<VMID, TIntObjectMap<ProxyLeaseInfo>>();
	private static final Lock proxy_lease_map_lock = new ReentrantLock();

	// Reference queue to ProxyInvocationHandler so we know when we're done with them.
	// This will only include handlers pointing to remote VM's.
	private static final ReferenceQueue<ProxyInvocationHandler> ref_queue =
		new ReferenceQueue<ProxyInvocationHandler>();

	private static final BlockingQueue<ProxyLeaseInfo> renew_queue =
		new DelayQueue<ProxyLeaseInfo>();
	static {
		// Put a prune flag on the queue
		renew_queue.add( new LeasePruneFlag() );
	}


	private static volatile ManagerThread manager_thread = null;


	static void registerLocalHandler( VMID vmid, LocalCallHandler handler ) {
		local_handler_map_lock.lock();
		try {
			local_handler_map.put( vmid, handler );

			if ( !DGC_DISABLED && manager_thread == null ) {
				manager_thread = new ManagerThread();
				manager_thread.start();
			}
		}
		finally {
			local_handler_map_lock.unlock();
		}
	}

	static void deregisterLocalHandler( VMID vmid ) {
		local_handler_map_lock.lock();
		try {
			local_handler_map.remove( vmid );

			if ( local_handler_map.isEmpty() && !DGC_DISABLED && manager_thread != null ) {
				manager_thread.halt();
				manager_thread = null;
			}
		}
		finally {
			local_handler_map_lock.unlock();
		}
	}


	/**
	 * Register a new proxy to be managed for DGC leases.
	 */
	static void registerProxy( ProxyInvocationHandler handler, VMID vmid, int object_id ) {
		if ( LEASE_DEBUGGING ) {
			System.out.println( "New PIH registered: " + handler + "  VMID: " + vmid +
				"  OID: " + object_id );
		}

		ProxyLeaseInfo lease_info;
		boolean created = false;
		proxy_lease_map_lock.lock();
		try {
			TIntObjectMap<ProxyLeaseInfo> vm_map = proxy_lease_map.get( vmid );
			if ( vm_map == null ) {
				vm_map = new TIntObjectHashMap<ProxyLeaseInfo>();
				proxy_lease_map.put( vmid, vm_map );
			}

			lease_info = vm_map.get( object_id );
			if ( lease_info == null ) {
				lease_info = new ProxyLeaseInfo( vmid, object_id, LEASE_DURATION_MS );
				vm_map.put( object_id, lease_info );
				created = true;
			}
			else lease_info.proxy_count++;

			lease_info.addPIHReference(
				new RemoteProxyWeakReference( handler, ref_queue ) );
		}
		finally {
			proxy_lease_map_lock.unlock();
		}

		if ( created ) {
			renew_queue.add( lease_info );
		}
	}


	static void handleLease( LeaseIMessage message, LocalCallHandler local_handler,
		VMID source_vm ) {

		if ( LEASE_DEBUGGING ) {
			System.out.println( "Received lease from " + source_vm + " for: " +
				Arrays.toString( message.getOIDs() ) );
		}
		for ( int oid : message.getOIDs() ) {
			local_handler.renewLease( oid, source_vm );
		}
	}


	static void handleLeaseRelease( LeaseReleaseIMessage message,
		LocalCallHandler local_handler, VMID source_vm ) {

		if ( LEASE_DEBUGGING ) {
			System.out.println( "Received lease RELEASE from " + source_vm + " for: " +
				Arrays.toString( message.getOIDs() ) );
		}
		for ( int oid : message.getOIDs() ) {
			local_handler.giveUpLease( oid, source_vm );
		}
	}


	/**
	 * Send a lease-related message.
	 * @param vmid			Target VM
	 * @param object_id		Target object ID
	 * @param renew			Whether we're renewing (true) or releasing (false).
	 */
	private static void sendLeaseMessage( VMID vmid, int object_id, boolean renew )
		throws NotConnectedException, IOException {

		Intrepid instance = Intrepid.findInstanceWithRemoteSession( vmid );
		if ( instance == null ) {
			throw new NotConnectedException( "No instance found with connection.", vmid );
		}

		IntrepidSPI spi;
		try {
			spi = instance.getSPI();
		}
		catch ( IllegalStateException ex ) {
			throw new NotConnectedException(
				"Invalid SPI state: " + ex.getMessage(), vmid );
		}

		IMessage message;
		if ( renew ) message = new LeaseIMessage( object_id );
		else message = new LeaseReleaseIMessage( object_id );

		spi.sendMessage( vmid, message, null );
	}


	private static class ManagerThread extends Thread {
		private volatile boolean keep_going = true;


		ManagerThread() {
			super( "Intrepid LeaseManager" );
			setPriority( Thread.NORM_PRIORITY + 2 );	// Higher to avoid starvation
			setDaemon( true );
		}


		void halt() {
			keep_going = false;
			interrupt();
		}


		@Override
		public void run() {
			long last_ref_queue_check = System.nanoTime();
			ProxyLeaseInfo info = null;
			while ( keep_going ) {
				boolean reschedule = true;
				try {
					info = renew_queue.poll( 10, TimeUnit.SECONDS );
					assert info == null || !renew_queue.contains( info ) :
						"Info: " + info + "\n\nQueue: " + renew_queue;
					long post_poll_time = System.nanoTime();
					if ( LEASE_DEBUGGING ) {
						System.out.println( "--- pulled lease info from queue: " + info +
							"  Queue size: " + renew_queue.size() );
						if ( SUPER_LEASE_DEBUGGING ) {
							System.out.println( "Queue contents: " + renew_queue );
							for( ProxyLeaseInfo pli : renew_queue ) {
								System.out.println( "  " + pli );
							}

							List<ProxyLeaseInfo> list =
								new ArrayList<ProxyLeaseInfo>( renew_queue );
							Collections.sort( list );
							System.out.println();
							System.out.println( "Sorted contents:");
							for( ProxyLeaseInfo pli : list ) {
								System.out.println( "  " + pli );
							}
						}
					}
					if ( info == null ) {
						checkRefQueue();
						last_ref_queue_check = post_poll_time;
						continue;
					}

					// Make sure we're not starved for checking the ref queue
					if ( post_poll_time - last_ref_queue_check >
						FORCE_REF_QUEUE_CHECK_TIME_NS ) {

						checkRefQueue();
						last_ref_queue_check = post_poll_time;
						// fall through...
					}

					if ( info instanceof LeasePruneFlag ) {
						pruneLeases();
					}
					else {
						// Make sure we're still using the proxy
						if ( info.proxy_count <= 0 ) {
							reschedule = false;
							sendLeaseMessage( info.vmid, info.object_id, false );
							if ( LEASE_DEBUGGING ) {
								System.out.println( "Lease RELEASE sent: " +
									info.object_id + " @ " + info.vmid );
							}
						}
						else {
							sendLeaseMessage( info.vmid, info.object_id, true );
							if ( LEASE_DEBUGGING ) {
								System.out.println( "Lease renew sent: " +
									info.object_id + " @ " + info.vmid );
							}
						}
					}

					if ( reschedule ) renew_queue.add( info.next() );
				}
				catch ( Throwable t ) {
					if ( t instanceof AssertionError ) {
						LOG.error( "AssertionError caught handling leases. Info: {} " +
							" Queue: {}", info, renew_queue, t );
					}
					LOG.debug( "Exception caught in LeaseManager", t );
					if ( info != null ) {
						if ( LOG.isDebugEnabled() ) {
							LOG.debug( "Error while sending lease to {} for {}",
								info.vmid, Integer.valueOf( info.object_id ), t );
						}

						if ( reschedule ) {
							ProxyLeaseInfo new_info = info.error();
							// NOTE: will be null if we've hit the max error count
							if ( new_info != null ) {
								renew_queue.add( new_info );
							}
						}
					}
				}
				finally {
					info = null;
				}
			}
		}


		/**
		 * Check the reference queue for GC'ed ProxyInvocationHandlers (to know we're
		 * done with them).
		 */
		private void checkRefQueue() {
			RemoteProxyWeakReference ref;
			while( ( ref = ( RemoteProxyWeakReference ) ref_queue.poll() ) != null ) {
				if ( LEASE_DEBUGGING ) {
					System.out.println( "PIH no longer used: " + ref.vmid + " - " +
						ref.object_id );
				}

				proxy_lease_map_lock.lock();
				try {
					TIntObjectMap<ProxyLeaseInfo> vm_map =
						proxy_lease_map.get( ref.vmid );
					if ( vm_map == null ) {
						LOG.warn( "RemoteProxyWeakReference found in ref " +
							"queue but no corresponding entry found in " +
							"proxy_lease_map. VMID:" + ref.vmid +
							"  OID:" + ref.object_id );
						continue;
					}

					ProxyLeaseInfo lease_info = vm_map.get( ref.object_id );
					if ( lease_info == null ) {
						LOG.warn( "RemoteProxyWeakReference found in ref " +
							"queue but no corresponding ProxyLeaseInfo " +
							"entry found. VMID:" + ref.vmid +
							"  OID:" + ref.object_id );
						continue;
					}

					lease_info.removePIHReference( ref );

					lease_info.proxy_count--;
					if ( lease_info.proxy_count == 0 ) {
						vm_map.remove( ref.object_id );
						if ( vm_map.isEmpty() ) {
							proxy_lease_map.remove( ref.vmid );
						}
					}

					// NOTE: ProxyLeaseInfo may still be in the renew_queue,
					//       but that will be taken care of elsewhere in this
					//       thread (when it comes due for renewal).
				}
				finally {
					proxy_lease_map_lock.unlock();
				}
			}
		}


		private void pruneLeases() {
			if ( LEASE_DEBUGGING ) System.out.println( "--- prune leases ---" );
			local_handler_map_lock.lock();
			try {
				for( LocalCallHandler handler : local_handler_map.values() ) {
					handler.pruneLeases();
				}
			}
			finally {
				local_handler_map_lock.unlock();
			}
		}
	}


}
