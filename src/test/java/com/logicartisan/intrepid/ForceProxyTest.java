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

package com.logicartisan.intrepid;

import junit.framework.TestCase;

import java.io.Serializable;
import java.net.InetAddress;


/**
 *
 */
public class ForceProxyTest extends TestCase {
	private Intrepid server_instance;
	private Intrepid client_instance;

	private Server server;


	@Override
	protected void setUp() throws Exception {

		// Make sure we test the full stack. See comment on
		// "Intrepid.disable_inter_instance_bridge" for more info.
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		server_instance = Intrepid.create( new IntrepidSetup().serverPort( 11751 )
			.vmidHint( "server" ).openServer() );
		server_instance.getLocalRegistry().bind( "server", new ServerImpl() );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			11751, null, null );
		assertNotNull( server_vmid );

		Registry registry = client_instance.getRemoteRegistry( server_vmid );
		server = ( Server ) registry.lookup( "server" );
	}

	@Override
	protected void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}

	public void testChildNoForce() {
		Object object = new ChildNoForceImpl();
		assertFalse( object instanceof ForceProxy );
		server.checkProxy( object, false );
	}

	public void testChildForce() {
		Object object = new ChildForceImpl();
		assertTrue( object instanceof ForceProxy );
		server.checkProxy( object, true );
	}

	public void testParentForceChildNoForce() {
		Object object = new ParentForceChildNoForceImpl();
		assertTrue( object instanceof ForceProxy );
		server.checkProxy( object, true );
	}

	public void testParentForceChildForce() {
		Object object = new ParentForceChildForceImpl();
		assertTrue( object instanceof ForceProxy );
		server.checkProxy( object, true );
	}

	public void testParentNoForceChildForce() {
		Object object = new ParentNoForceChildForceImpl();
		assertTrue( object instanceof ForceProxy );
		server.checkProxy( object, true );
	}

	public void testParentNoForceChildNoForce() {
		Object object = new ParentNoForceChildNoForceImpl();
		assertFalse( object instanceof ForceProxy );
		server.checkProxy( object, false );
	}

	public void testForceParentNoForceChildNoForce() {
		Object object = new ForceParentNoForceChildNoForceImpl();
		assertTrue( object instanceof ForceProxy );
		server.checkProxy( object, true );
	}



	public static interface ChildNoForce extends Serializable {}

	public static interface ChildForce extends Serializable, ForceProxy {}

	public static interface ParentForceChildNoForce
		extends ChildNoForce, Serializable, ForceProxy {}

	public static interface ParentForceChildForce
		extends ChildForce, Serializable, ForceProxy {}

	public static interface ParentNoForceChildForce extends ChildForce, Serializable {}

	public static interface ParentNoForceChildNoForce extends ChildNoForce, Serializable {}


	public static class ChildNoForceImpl implements ChildNoForce {}

	public static class ChildForceImpl implements ChildForce {}

	public static class ParentForceChildNoForceImpl implements ParentForceChildNoForce {}

	public static class ParentForceChildForceImpl implements ParentForceChildForce {}

	public static class ParentNoForceChildForceImpl implements ParentNoForceChildForce {}

	public static class ParentNoForceChildNoForceImpl
		implements ParentNoForceChildNoForce {}

	public static class ForceParentNoForceChildNoForceImpl
		implements ParentNoForceChildNoForce, ForceProxy {}


	public static interface Server {
		public void checkProxy( Object proxy, boolean expect_proxy );
	}

	public class ServerImpl implements Server {
		@Override
		public void checkProxy( Object proxy, boolean expect_proxy ) {
			assertEquals( expect_proxy + " != " + Intrepid.isProxy( proxy ) + ": " +
				proxy.getClass(), expect_proxy, Intrepid.isProxy( proxy ) );
		}
	}
}
