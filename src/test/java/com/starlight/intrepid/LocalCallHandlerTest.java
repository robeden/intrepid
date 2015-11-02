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

import com.starlight.IOKit;
import com.starlight.intrepid.exception.IllegalProxyDelegateException;
import junit.framework.TestCase;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 *
 */
public class LocalCallHandlerTest extends TestCase {
	private Intrepid intrepid;



	@Override
	protected void tearDown() throws Exception {
		if ( intrepid != null ) intrepid.close();
	}



	public void testFindProxyInterfaces() {
		try {
			Class[] c = LocalCallHandler.findProxyInterfaces( Object.class );
			fail( "Shouldn't have found interfaces: " + Arrays.toString( c ) );
		}
		catch( IllegalProxyDelegateException ex ) {
			// this is good
		}

		try {
			Class[] c = LocalCallHandler.findProxyInterfaces( String.class );
			Set<Class> class_set = new HashSet<Class>( Arrays.asList( c ) );

			assertTrue( class_set.contains( Serializable.class ) );
			assertTrue( class_set.contains( Comparable.class ) );
			assertTrue( class_set.contains( CharSequence.class ) );
		}
		catch( IllegalProxyDelegateException ex ) {
			fail( "Should be a valid proxy: " + ex );
		}

		try {
			Class[] c = LocalCallHandler.findProxyInterfaces( ConcurrentHashMap.class );
			Set<Class> class_set = new HashSet<Class>( Arrays.asList( c ) );

			assertTrue( class_set.contains( Serializable.class ) );
			assertTrue( class_set.contains( Map.class ) );
			assertTrue( class_set.contains( ConcurrentMap.class ) );
		}
		catch( IllegalProxyDelegateException ex ) {
			fail( "Should be a valid proxy: " + ex );
		}
	}


	public void testStringProxy() throws Exception {
		intrepid = Intrepid.create(
			new IntrepidSetup().spi( new StubIntrepidSPI() ) );
		try {
			String source = "this is my test string";
			CharSequence proxy = ( CharSequence ) intrepid.createProxy( source );

			// Make sure we got back a different object
			assertNotSame( source, proxy );

			// Make sure it's actually a proxy
			assertTrue( proxy instanceof Proxy );

			// Test method calls...
			assertEquals( source.length(), proxy.length() );
			for( int i = 0; i < source.length(); i++ ) {
				assertEquals( source.charAt( i ), proxy.charAt( i ) );
			}
		}
		finally {
			if ( intrepid != null ) intrepid.close();
		}
	}


	public void testSerializedStringProxy() throws Exception {
		intrepid =
			Intrepid.create( new IntrepidSetup().spi( new StubIntrepidSPI() ) );
		try {
			String source = "this is my test string";
			CharSequence proxy = ( CharSequence ) intrepid.createProxy( source );

			proxy = ( CharSequence ) IOKit.deserialize( IOKit.serialize( proxy ) );

			// Make sure we got back a different object
			assertNotSame( source, proxy );

			// Make sure it's actually a proxy
			assertTrue( proxy instanceof Proxy );

			// Test method calls...
			assertEquals( source.length(), proxy.length() );
			for( int i = 0; i < source.length(); i++ ) {
				assertEquals( source.charAt( i ), proxy.charAt( i ) );
			}
		}
		finally {
			if ( intrepid != null ) intrepid.close();
		}
	}
}
