package com.logicartisan.intrepid.demo.lease;

/**
 *
 */
public class GarbageCreator implements Runnable {
	@Override
	public void run() {
		Object[] junk = new Object[ 10000 ];
		if ( false ) System.out.println( "Junk: " + junk );
	}
}
