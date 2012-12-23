package com.starlight.intrepid.demo.lease;

import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.IntrepidContext;
import com.starlight.intrepid.IntrepidSetup;
import com.starlight.intrepid.IntrepidTestProxyAccess;
import com.starlight.listeners.ListenerSupport;
import com.starlight.listeners.ListenerSupportFactory;
import com.starlight.thread.SharedThreadPool;

import java.util.Date;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class LeaseServer implements ServerIfc {
	private final ListenerSupport<Runnable,Void> listeners =
		ListenerSupportFactory.create( Runnable.class, true );


	@Override
	public void addListener( Runnable listener ) {
		listeners.add( listener );

		System.out.println( "Listener \"" + listener + "\" added. Object ID: " +
			IntrepidTestProxyAccess.getObjectID( listener ) );

		assert IntrepidContext.getCallingVMID().equals(
			IntrepidTestProxyAccess.getHostVMID( listener ) ) :
			IntrepidContext.getCallingVMID() + " != " +
			IntrepidTestProxyAccess.getHostVMID( listener );
	}

	@Override
	public void removeListener( Runnable listener ) {
		listeners.add( listener );

		System.out.println( "Listener \"" + listener + "\" removed from " +
			IntrepidContext.getCallingVMID() + "  Object ID: " +
			IntrepidTestProxyAccess.getObjectID( listener ) );

		assert IntrepidContext.getCallingVMID().equals(
			IntrepidTestProxyAccess.getHostVMID( listener ) ) :
			IntrepidContext.getCallingVMID() + " != " +
			IntrepidTestProxyAccess.getHostVMID( listener );
	}


	public static void main( String[] args ) throws Exception {
		Intrepid instance = Intrepid.create( new IntrepidSetup().openServer() );

		final LeaseServer server = new LeaseServer();
		instance.getLocalRegistry().bind( "server", server );

		instance.addPerformanceListener( new LeaseListener() );


		System.out.println(
			"Server listening on port " + instance.getServerPort() + "..." );


		SharedThreadPool.INSTANCE.scheduleAtFixedRate( new GarbageCreator(),
			250, 250, TimeUnit.MILLISECONDS );

		SharedThreadPool.INSTANCE.scheduleAtFixedRate( new Runnable() {
			@Override
			public void run() {
				System.out.println( new Date() + ": run()" );
				server.listeners.dispatch().run();
			}
		}, 1, 1, TimeUnit.MINUTES );
	}
}
