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

package com.starlight.intrepid.spi.mina;

import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.IntrepidSetup;
import junit.framework.TestCase;


/**
 *
 */
public class MINAIntrepidSPITest extends TestCase {
	public void testAnonymousPortServer() {
		Intrepid instance = null;
		try {
			MINAIntrepidSPI spi = new MINAIntrepidSPI();
			instance = Intrepid.create( new IntrepidSetup().openServer().spi( spi ) );

			System.out.println( "Server address: " + spi.getServerAddress() );
			assertNotNull( spi.getServerAddress() );
		}
		catch( Exception ex ) {
			ex.printStackTrace();
			fail( "Unexpected error received: " + ex );
		}
		finally {
			if ( instance != null ) instance.close();
		}
	}

	public void testSpecificPortServer() {
		Intrepid instance = null;
		try {
			MINAIntrepidSPI spi = new MINAIntrepidSPI();
			instance = Intrepid.create(
				new IntrepidSetup().openServer().spi( spi ).serverPort( 11751 ) );

			System.out.println( "Server address: " + spi.getServerAddress() );
			assertNotNull( spi.getServerAddress() );
			assertEquals( 11751, spi.getServerAddress().getPort() );
		}
		catch( Exception ex ) {
			ex.printStackTrace();
			fail( "Unexpected error received: " + ex );
		}
		finally {
			if ( instance != null ) instance.close();
		}
	}
}
