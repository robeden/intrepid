package com.starlight.intrepid;

/**
 *
 */
public class TestListenerLeases /*implements Runnable*/ {
//	public static void main( String[] args ) throws Exception {
//		new Thread( "GC" ) {
//			@Override
//			public void run() {
//				while( true ) {
//					ThreadKit.sleep( 5000 );
//
//					System.out.println( "--gc--" );
//					System.gc();
//				}
//			}
//		}.start();
//
//		if ( args[ 0 ].equalsIgnoreCase( "server" ) ) runServer();
//		else runClient();
//	}
//
//
//	private static void runServer() throws Exception {
//		ServerThread server = new ServerThread();
//
//		Intrepid intrepid =
//			Intrepid.newBuilder().openServer().serverPort( 11223 ) );
//		intrepid.getLocalRegistry().bind( "test", server );
//		server.start();
//
//		UIKit.testComponent( new LeaseDebugPane( intrepid ) );
//	}
//
//	private static void runClient() throws Exception {
//		TestListenerLeases test = new TestListenerLeases();
//
//		Intrepid intrepid = Intrepid.newBuilder().build();
//		VMID server_vmid =
//			intrepid.connect( InetAddress.getByName( "127.0.0.1" ), 11223, null, null );
//		Registry registry = intrepid.getRemoteRegistry( server_vmid );
//		Server server = ( Server ) registry.lookup( "test" );
//		server.addListener( test );
//		System.out.println( "Listener registered" );
//
//		UIKit.testComponent( new LeaseDebugPane( intrepid ) );
//	}
//
//
//	public void run() {
//		System.out.println( "Client notified" );
//	}
//
//
//	public static class ServerThread extends Thread implements Server {
//		private final ListenerSupport<Runnable,Void> listeners =
//			ListenerSupportFactory.create( Runnable.class, false );
//
//
//		@Override
//		public void addListener( Runnable run ) {
//			listeners.add( run );
//		}
//
//		@Override
//		public void run() {
//			System.out.println( "Server running..." );
//			while( true ) {
//				ThreadKit.sleep( 10000 );
//
//				listeners.dispatch().run();
//			}
//		}
//	}
//
//
//	public static interface Server {
//		public void addListener( Runnable run );
//	}
}
