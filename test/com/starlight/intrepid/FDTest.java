package com.starlight.intrepid;

import com.starlight.thread.ThreadKit;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class FDTest {
	public static void main( String[] args ) throws Exception {
		Intrepid instance = Intrepid.create( null );

		System.out.println( "Will connect to: " + args[ 0 ] );

		while( true ) {
			try {
			instance.tryConnect( InetAddress.getByName( args[ 0 ] ), 11753, null, null,
				5, TimeUnit.SECONDS );
			}
			catch( Exception ex ) {
				System.out.println( "Connection failed: " + ex );
			}

			ThreadKit.sleep( 5000 );
		}
	}
}
