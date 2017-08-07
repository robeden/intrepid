package com.starlight.intrepid.demo.lease;

import com.starlight.intrepid.Registry;
import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.VMID;
import com.logicartisan.common.core.thread.SharedThreadPool;

import java.net.InetAddress;
import java.util.Date;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class ListenerClient implements Runnable {
	@Override
	public void run() {
		System.out.println( new Date() + ": run()" );
	}


	public static void main( String[] args ) throws Exception {
		Intrepid intrepid = Intrepid.create( null );
		VMID server_vmid = intrepid.connect( InetAddress.getLocalHost(),
			Integer.parseInt( args[ 0 ] ), null, null );

		intrepid.addPerformanceListener( new LeaseListener() );

		Registry registry = intrepid.getRemoteRegistry( server_vmid );

		ServerIfc server = ( ServerIfc ) registry.lookup( "server" );
		server.addListener( new ListenerClient() );

		SharedThreadPool.INSTANCE.scheduleAtFixedRate( new GarbageCreator(),
			250, 250, TimeUnit.MILLISECONDS );
	}


}
