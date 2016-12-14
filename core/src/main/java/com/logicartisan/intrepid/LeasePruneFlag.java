package com.logicartisan.intrepid;

/**
*
*/
class LeasePruneFlag extends ProxyLeaseInfo {
	LeasePruneFlag() {
		super( null, -1, LeaseManager.LEASE_PRUNE_INTERVAL_NS );

		next();
	}


	@Override
	protected ProxyLeaseInfo next() {
		next_lease_expected = System.nanoTime() + LeaseManager.LEASE_PRUNE_INTERVAL_NS;
		return this;
	}
}
