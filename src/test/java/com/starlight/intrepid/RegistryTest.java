// Copyright (c) 2010 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Intrepid nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
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

import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.IntrepidSetup;
import com.starlight.intrepid.LocalRegistry;
import com.starlight.intrepid.exception.ObjectNotBoundException;
import com.starlight.thread.ThreadKit;
import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;


/**
 *
 */
public class RegistryTest extends TestCase {
	private Intrepid instance = null;

	@Override
	protected void tearDown() throws Exception {
		if ( instance != null ) instance.close();
	}


	public void testBinding() throws Exception {
		instance = Intrepid.create( new IntrepidSetup().spi( new StubIntrepidSPI() ) );

		LocalRegistry registry = instance.getLocalRegistry();

		try {
			registry.lookup( "lib/test" );
			fail( "Exception should have been thrown" );
		}
		catch( ObjectNotBoundException ex ) {
			// this is good
		}

		registry.bind( "lib/test", new CommTest.ClientImpl( false ) );

		try {
			CommTest.Client client = ( CommTest.Client ) registry.lookup( "lib/test" );
			// Should get here
		}
		catch ( Exception ex ) {
			ex.printStackTrace();
			fail( "Unexpected exception: " + ex );
		}

		registry.unbind( "lib/test" );

		try {
			registry.lookup( "lib/test" );
			fail( "Exception should have been thrown" );
		}
		catch( ObjectNotBoundException ex ) {
			// this is good
		}
	}


	public void testTryLookup() throws Exception {
		instance = Intrepid.create( new IntrepidSetup().spi( new StubIntrepidSPI() ) );

		final LocalRegistry registry = instance.getLocalRegistry();


		// Test timeout
		long start = System.currentTimeMillis();
		CommTest.Client client =
			( CommTest.Client ) registry.tryLookup( "lib/test", 3, TimeUnit.SECONDS );
		long time = System.currentTimeMillis() - start;
		assertNull( client );
		assertTrue( String.valueOf( time ), time >= 3000 );


		// Test bind during lookup
		new Thread() {
			@Override
			public void run() {
				ThreadKit.sleep( 5000 );
				registry.bind( "lib/test", new CommTest.ClientImpl( false ) );
			}
		}.start();

		start = System.currentTimeMillis();

		client = ( CommTest.Client ) registry.tryLookup( "lib/test", 15, TimeUnit.SECONDS );

		time = System.currentTimeMillis() - start;

		assertNotNull( client );
		assertTrue( time >= 4000 && time <= 6000 );	// 1 second slop


		// Test interrupt
		final Thread current = Thread.currentThread();

		new Thread() {
			@Override
			public void run() {
				ThreadKit.sleep( 2000 );
				current.interrupt();
			}
		}.start();

		try {
			registry.tryLookup( "not_there", 10, TimeUnit.SECONDS );
			fail( "Should have been interrupted" );
		}
		catch( InterruptedException ex ) {
			// this is good
		}
	}
}
