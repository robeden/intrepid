package com.starlight.intrepid;

import gnu.trove.set.hash.TCustomHashSet;
import gnu.trove.strategy.IdentityHashingStrategy;

import java.util.Set;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;


/**
*
*/
class ProxyLeaseInfo implements Delayed {
	private static final int LEASE_MAX_ERRORS =
		Integer.getInteger( "intrepid.lease.max_errors", 10 ).intValue();


	final VMID vmid;
	final int object_id;
	final long lease_duration_ms;

	volatile int proxy_count = 1;

	private volatile int error_count;
	protected volatile long next_lease_expected;

	// This list exists because the references will disappear from the ref queue
	// if no reference is held to them. Note that this is reference to the
	// WeakReference, not the object they're referencing (the PIH).
	@SuppressWarnings( { "MismatchedQueryAndUpdateOfCollection" } )
	private final Set<RemoteProxyWeakReference> ref_list =
		new TCustomHashSet<>( new IdentityHashingStrategy<>() );

	ProxyLeaseInfo( VMID vmid, int object_id, long lease_duration_ms ) {
		this.vmid = vmid;
		this.object_id = object_id;
		this.lease_duration_ms = lease_duration_ms;

		// First lease is right away...
		next_lease_expected = System.nanoTime();
	}

	ProxyLeaseInfo error() {
		error_count++;
		if ( error_count > LEASE_MAX_ERRORS ) return null;

		// Try again
		next_lease_expected = System.nanoTime() + LeaseManager.LEASE_ERROR_RETRY_DELAY_NS;
		return this;
	}

	protected ProxyLeaseInfo next() {
		error_count = 0;

		// To make sure we renew the lease in time, renew when half the rime is
		// gone/remaining.
		long addition = ( long ) ( lease_duration_ms / ( double ) LeaseManager.LEASE_OVERLAP_MARGIN );
		addition -= 250;    // a little cushion to make sure 3 (with the default) full
							// leases can happen before the object is dropped.
		next_lease_expected =
			System.nanoTime() + TimeUnit.MILLISECONDS.toNanos( addition );
		return this;
	}


	void addPIHReference( RemoteProxyWeakReference ref ) {
		ref_list.add( ref );
	}

	void removePIHReference( RemoteProxyWeakReference ref ) {
		ref_list.remove( ref );
	}


	@Override
	public long getDelay( TimeUnit unit ) {
		long delay = unit.convert( next_lease_expected - System.nanoTime(),
			TimeUnit.NANOSECONDS );
		if ( LeaseManager.SUPER_LEASE_DEBUGGING ) {
			System.out.println( "*** DELAY: " + delay + " " + unit );
		}
		return delay;
	}

	@Override
	public int compareTo( Delayed o ) {
		long self = getDelay( TimeUnit.NANOSECONDS );
		long other = o.getDelay( TimeUnit.NANOSECONDS );

		if ( self < other ) return -1;
		else if ( self == other ) return 0;
		else return 1;
	}


	public String toString() {
		return "ProxyLeaseInfo{" +
			", vmid=" + vmid +
			", object_id=" + object_id +
			", delay(ms)=" + getDelay( TimeUnit.MILLISECONDS ) +
			", next_lease_expected=" + next_lease_expected +
			", error_count=" + error_count +
			", proxy_count=" + proxy_count +
			'}';
	}
}
