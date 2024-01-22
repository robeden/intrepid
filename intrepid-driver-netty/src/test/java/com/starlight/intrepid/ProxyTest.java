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

import com.logicartisan.common.core.IOKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 *
 */
public class ProxyTest {
	private Intrepid intrepid;


	@BeforeEach
	public void setUp() throws Exception {
		intrepid = Intrepid.newBuilder().build();
	}

	@AfterEach
	public void tearDown() throws Exception {
		if ( intrepid != null ) {
			intrepid.close();
			intrepid = null;
		}
	}



	/**
	 * This tests a problem with the initial implementation in which methods were
	 * pulled from the implementation class rather than the interfaces. So, final methods
	 * in parent classes were not known by the proxy.
	 */
	@Test
	public void testFinalAbstractMethod() throws Exception {
		TestClass original = new TestClass();

		TestInterface proxy = ( TestInterface ) intrepid.createProxy( original );
		proxy = ( TestInterface ) IOKit.deserialize( IOKit.serialize( proxy ) );

		proxy.notFinalInAbstract();

		proxy.finalInAbstract();
	}


	@Test
	public void testEquals() throws Exception {
		TestClass original = new TestClass();

		TestInterface proxy = ( TestInterface ) intrepid.createProxy( original );
		assertEquals(proxy, proxy);
		assertEquals(proxy.hashCode(), proxy.hashCode());

		TestInterface proxy2 =
			( TestInterface ) IOKit.deserialize( IOKit.serialize( proxy ) );
		assertEquals(proxy, proxy2);
		assertEquals(proxy.hashCode(), proxy2.hashCode());
	}


	@Test
	public void testMultipleCreate() throws Exception {
		TestClass original = new TestClass();

		TestInterface proxy1 = ( TestInterface ) intrepid.createProxy( original );
		TestInterface proxy2 = ( TestInterface ) intrepid.createProxy( original );

		assertNotNull(proxy1);
		assertNotNull(proxy2);
		assertSame(proxy1, proxy2);

		assertEquals(proxy1, proxy2);

		TestInterface proxy3 =
			( TestInterface ) IOKit.deserialize( IOKit.serialize( proxy1 ) );
		assertNotSame(proxy1, proxy3);
		assertNotSame(proxy2, proxy3);
		assertEquals(proxy1, proxy3);
		assertEquals(proxy2, proxy3);
	}


	public interface TestInterface {
		void finalInAbstract();
		void notFinalInAbstract();
	}


	public static abstract class TestAbstract implements TestInterface {
		@Override
		public final void finalInAbstract() {}
	}


	public static class TestClass extends TestAbstract {
		@Override
		public void notFinalInAbstract() {}
	}
}
