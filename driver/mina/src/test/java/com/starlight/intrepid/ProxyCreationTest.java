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

import com.logicartisan.common.core.IOKit;
import junit.framework.TestCase;


/**
 *
 */
public class ProxyCreationTest extends TestCase {
	Intrepid instance;

	@Override
	protected void tearDown() throws Exception {
		if ( instance != null ) instance.close();
	}


	public void testDuplicateProxyCreation() throws Exception {
		instance = Intrepid.create( null );

		CommTest.ServerImpl delegate =
			new CommTest.ServerImpl( true, instance.getLocalVMID() );

		CommTest.Server proxy = ( CommTest.Server ) instance.createProxy( delegate );

		// Another creation should return the same proxy
		CommTest.Server proxy2 = ( CommTest.Server ) instance.createProxy( delegate );

		assertSame( proxy, proxy2 );
	}


	public void testIDsAndClose() throws Exception {
		instance = Intrepid.create( null );

		CommTest.ServerImpl delegate =
			new CommTest.ServerImpl( true, instance.getLocalVMID() );

		CommTest.Server proxy = ( CommTest.Server ) instance.createProxy( delegate );

		int first_object_id = ( ( Proxy ) proxy ).__intrepid__getObjectID();



		// Close and restart the instance. This should flush the internal maps causing a
		// new ID for the proxy when re-created.
		instance.close();
		instance = Intrepid.create( null );
		proxy = ( CommTest.Server ) instance.createProxy( delegate );

		int second_object_id = ( ( Proxy ) proxy ).__intrepid__getObjectID();

		assertFalse( first_object_id + " == " + second_object_id,
			first_object_id == second_object_id );
	}


	public void testComplexInterface() throws Exception {
		instance = Intrepid.create( null );

		ABImpl original = new ABImpl();

		IfcAB proxy = ( IfcAB ) instance.createProxy( original );
		proxy.a();
		proxy.b();
		proxy.ab();

		// Serialize the proxy to make sure it's not holding a direct reference
		proxy = ( IfcAB ) IOKit.deserialize( IOKit.serialize( proxy ) );
		proxy.a();
		proxy.b();
		proxy.ab();
	}


	public static interface IfcA {
		public void a();
	}

	public static interface IfcB {
		public void b();
	}

	public static interface IfcAB extends IfcA, IfcB {
		public void ab();
	}


	public static class ABImpl implements IfcAB {
		@Override
		public void ab() {
			System.out.println( "ab called" );
		}

		@Override
		public void a() {
			System.out.println( "a called" );
		}

		@Override
		public void b() {
			System.out.println( "b called" );
		}
	}
}
