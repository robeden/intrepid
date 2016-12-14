package com.logicartisan.intrepid.demo.lease;

/**
 *
 */
public interface ServerIfc {
	public void addListener( Runnable listener );
	public void removeListener( Runnable listener );
}
