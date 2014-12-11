// Copyright (c) 2010 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
// * Neither the name of Intrepid nor the
// names of its contributors may be used to endorse or promote products
// derived from this software without specific prior written permission.
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

package com.starlight.intrepid;

import com.starlight.IOKit;
import com.starlight.thread.ThreadKit;
import junit.framework.TestCase;
import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.IntrepidSetup;
import com.starlight.intrepid.Registry;
import com.starlight.intrepid.VMID;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 *
 */
public class LeaseTest extends TestCase {
	public void testSystemProperties() {
		assertEquals( "System property 'intrepid.lease.duration' must be set " +
			"to '2000' when running unit tests.", "2000",
			System.getProperty( "intrepid.lease.duration" ) );
		assertEquals( "System property 'intrepid.lease.prune_interval' must be set " +
			"to '1000' when running unit tests.", "1000",
			System.getProperty( "intrepid.lease.prune_interval" ) );
		assertEquals( "System property 'intrepid.local_call_handler.initial_reservation' " +
			"must be set to '10000' when running unit tests.", "10000",
			System.getProperty( "intrepid.local_call_handler.initial_reservation" ) );
	}

	public void testSimpleDCGLeasing() throws Exception {
		String classpath = System.getProperty( "java.class.path" );
		String java_home_dir = System.getProperty( "java.home" );
		String file_sep = System.getProperty( "file.separator" );

		Process process = null;
		InputStream in = null;
		try {
			ProcessBuilder builder = new ProcessBuilder(
				java_home_dir + file_sep + "bin" + file_sep + "java",
				"-cp", classpath, "-Dintrepid.lease.duration=2000",
				"-Dintrepid.lease.prune_interval=1000",
				LeaseTest.class.getName() + "$TestServer" );

			process = builder.start();

			in = process.getInputStream();

			BufferedReader reader = new BufferedReader( new InputStreamReader( in ) );
			String line = reader.readLine();
			int pass = 0;
			while( line == null ) {
				line = reader.readLine();
				pass++;
				if ( pass == 10 ) {
					throw new Exception( "Too many passes for port" );
				}
			}

			int port = Integer.parseInt( line );
			System.out.println( "Port is: " + port );

			new OutputEcho( reader ).start();

			Intrepid intrepid = Intrepid.create( null );
			VMID vmid = intrepid.connect( InetAddress.getByName( "127.0.0.1" ), port,
				null, "server" );

			Registry registry = intrepid.getRemoteRegistry( vmid );

			LeaseServer server = ( LeaseServer ) registry.lookup( "server" );

			List original = new ArrayList();
			original.add( "Rob" );
			original.add( "was" );
			original.add( "here" );

			List proxy = ( List ) intrepid.createProxy( original );

			ReferenceQueue<List> queue = new ReferenceQueue<List>();

			MyWeakReference original_ref =
				new MyWeakReference( original, queue, "original" );
			MyWeakReference proxy_ref =
				new MyWeakReference( proxy, queue, "proxy" );

			server.registerProxy( proxy );
			proxy = null;
			original = null;

			System.out.println( "Waiting 10 seconds..." );
			ThreadKit.sleep( 10000 );

			assertNull( queue.poll() );

			System.out.println( "Objects are still live (good). De-registering proxy..." );

			server.deregisterProxy();

			System.out.println( "Waiting 45 seconds..." );
			boolean found_original_ref = false;
			boolean found_proxy_ref = false;
			for ( int i = 0; i < 45; i++ ) {
				System.gc();
				ThreadKit.sleep( 1000 );

				MyWeakReference ref = ( MyWeakReference ) queue.poll();
				if ( ref == null ) continue;

				if ( ref.id.equals( "original" ) ) found_original_ref = true;
				else found_proxy_ref = true;
//				assertNotNull( ref );
				System.out.println( "Collected: " + ref );

				if ( found_original_ref && found_proxy_ref ) break;

//				ref = ( MyWeakReference ) queue.poll();
//				assertNotNull( ref );
//				System.out.println( "Collected: " + ref );
			}

			assertTrue( found_original_ref );
			assertTrue( found_proxy_ref );
		}
		finally {
			if ( process != null ) process.destroy();
			IOKit.close( in );
		}
	}

	public static class TestServer implements LeaseServer {
		private Object proxy;

		TestServer() {
			Timer timer = new Timer( "GC'er", true );
			timer.schedule( new TimerTask() {
				@Override
				public void run() {
					System.gc();
					System.out.println( "--GC--" );
				}
			}, 2000, 2000 );
		}

		// com.starlight.intrepid.LeaseTest$TestServer
		public static void main( String[] args ) throws Exception {
			Intrepid intrepid = Intrepid.create( new IntrepidSetup().openServer() );
			System.out.println( intrepid.getServerPort() );

			intrepid.getLocalRegistry().bind( "server", new TestServer() );
		}

		@Override
		public void deregisterProxy() {
			proxy = null;
			System.out.println( "Proxy de-registered on server" );
		}

		@Override
		public void registerProxy( Object proxy ) {
			this.proxy = proxy;
			System.out.println( "Proxy registered on server" );
		}
	}


	public static interface LeaseServer {
		public void registerProxy( Object proxy );
		public void deregisterProxy();
	}


	private static class MyWeakReference extends WeakReference<List> {
		private final String id;

		public MyWeakReference( List referent, ReferenceQueue<? super List> q,
			String id ) {
			
			super( referent, q );

			this.id = id;
		}

		@Override
		public String toString() {
			return id;
		}
	}


	private static class OutputEcho extends Thread {
		private final BufferedReader reader;

		OutputEcho( BufferedReader reader ) {
			setDaemon( true );

			this.reader = reader;
		}

		@Override
		public void run() {
			try {
				String line;
				while( ( line = reader.readLine() ) != null ) {
					System.out.println( "ECHO> " + line );
				}
			}
			catch( IOException ex ) {
				// ignore
			}
		}
	}
}
