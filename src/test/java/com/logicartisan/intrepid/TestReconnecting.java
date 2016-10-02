package com.logicartisan.intrepid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.logicartisan.intrepid.auth.ConnectionArgs;
import com.logicartisan.intrepid.auth.UserContextInfo;
import com.starlight.thread.ThreadKit;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class TestReconnecting {
	public static void main( String[] args ) throws Exception {
		if ( args[ 0 ].equalsIgnoreCase( "server" ) ) {
			runServer();
		}
		else runClient();
	}


	private static void runServer() throws Exception {
		System.out.println( "Running server..." );

		Intrepid instance =
			Intrepid.create( new IntrepidSetup().openServer().serverPort( 11751 ) );

		instance.addConnectionListener( new ConnectionListener() {
			@Override
			public void connectionOpened( @Nonnull InetAddress host, int port,
				Object attachment, @Nonnull VMID source_vmid, @Nonnull VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				@Nonnull Object connection_type_description, byte ack_rate_sec ) {

				System.out.println( "connectionOpened: " + host + ":" + port );
			}

			@Override
			public void connectionClosed( @Nonnull InetAddress host, int port,
				@Nonnull VMID source_vmid, @Nullable VMID vmid,
				@Nullable Object attachment, boolean will_attempt_reconnect,
				@Nullable UserContextInfo user_context ) {

				System.out.println( "connectionClosed: " + host + ":" + port +
					( will_attempt_reconnect ? " will reconnect" : " will NOT reconnect" ) );
			}

			@Override
			public void connectionOpening( @Nonnull InetAddress host, int port,
				Object attachment, ConnectionArgs args,
				@Nonnull Object connection_type_description ) {

				System.out.println( "connectionOpening: " + host + ":" + port );
			}

			@Override
			public void connectionOpenFailed( @Nonnull InetAddress host, int port,
				Object attachment, Exception error, boolean will_retry ) {

				System.out.println( "connectionOpenFailed: " + host + ":" + port +
					( will_retry ? " will retry" : " will NOT retry" ) );
			}
		} );
	}


	private static void runClient() throws Exception {
		System.out.println( "Running client..." );

		final Intrepid intrepid = Intrepid.create( new IntrepidSetup() );

		System.out.println( "Connecting to 127.0.0.1:11751...");

		final VMID vmid = intrepid.tryConnect( InetAddress.getByName( "127.0.0.1" ), 11751,
			null, null, Long.MAX_VALUE, TimeUnit.MILLISECONDS );
		System.out.println( "Connected: " + vmid );

		intrepid.addConnectionListener( new ConnectionListener() {
			@Override
			public void connectionOpened( @Nonnull InetAddress host, int port,
				Object attachment, @Nonnull VMID source_vmid, @Nonnull VMID vmid,
				UserContextInfo user_context, VMID previous_vmid,
				@Nonnull Object connection_type_description, byte ack_rate_sec ) {

				System.out.println( "connectionOpened: " + host + ":" + port );
			}

			@Override
			public void connectionClosed( @Nonnull InetAddress host, int port,
				@Nonnull VMID source_vmid, @Nullable VMID vmid,
				@Nullable Object attachment, boolean will_attempt_reconnect,
				@Nullable UserContextInfo user_context ) {

				System.out.println( "connectionClosed: " + host + ":" + port +
					( will_attempt_reconnect ? " will reconnect" : " will NOT reconnect" ) );
			}

			@Override
			public void connectionOpening( @Nonnull InetAddress host, int port,
				Object attachment, ConnectionArgs args,
				@Nonnull Object connection_type_description ) {}

			@Override
			public void connectionOpenFailed( @Nonnull InetAddress host, int port,
				Object attachment, Exception error, boolean will_retry ) {}
		} );

		new Thread() {
			@Override
			public void run() {
				while( true ) {
					ThreadKit.sleep( 1000 );

					System.out.print( "Ping..." );
					try {
						long time = intrepid.ping( vmid, 2, TimeUnit.SECONDS );
						System.out.println( "successful: " + time );
					}
					catch( Exception ex ) {
						System.out.println( "failed: " + ex );
					}
				}
			}
		}.start();
	}
}
