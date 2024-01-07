package com.starlight.intrepid.tools;

import com.starlight.intrepid.ChannelAcceptor;
import com.starlight.intrepid.exception.ChannelRejectedException;
import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;


/**
 *
 */
public class PluggableChannelAcceptorTest {
	@Test
	public void testNoAcceptors() {
		PluggableChannelAcceptor main_acceptor = new PluggableChannelAcceptor();

		// Test with nothing
		try {
			main_acceptor.newChannel( null, null, "Test" );
			fail("Shouldn't have worked");
		}
		catch ( ChannelRejectedException e ) {
			// This is expected
		}
	}


	@Test
	public void testClear() {
		PluggableChannelAcceptor main_acceptor = new PluggableChannelAcceptor();

		// Add a single acceptor that takes anything
		ChannelAcceptor accepting_acceptor = EasyMock.createMock( ChannelAcceptor.class );
		EasyMock.replay( accepting_acceptor );
		main_acceptor.addDelegate( accepting_acceptor );

		// Clear delegates
		main_acceptor.removeAll();

		// Test with nothing
		try {
			main_acceptor.newChannel( null, null, "Test" );
			fail("Shouldn't have worked");
		}
		catch ( ChannelRejectedException e ) {
			// This is expected
		}

		EasyMock.verify( accepting_acceptor );
	}


	@Test
	public void testSingleAcceptor() throws Exception {
		PluggableChannelAcceptor main_acceptor = new PluggableChannelAcceptor();

		// Add a single acceptor that takes anything
		ChannelAcceptor accepting_acceptor = EasyMock.createMock( ChannelAcceptor.class );
		accepting_acceptor.newChannel( null, null, "lib/test" );
		EasyMock.replay( accepting_acceptor );
		main_acceptor.addDelegate( accepting_acceptor );

		try {
			main_acceptor.newChannel( null, null, "lib/test" );
		}
		catch( ChannelRejectedException ex ) {
			ex.printStackTrace();
			fail("Shouldn't have thrown exception");
		}

		EasyMock.verify( accepting_acceptor );


		// Clear
		main_acceptor.removeAll();


		// Add acceptor again to the front this time (should be equivalent)
		EasyMock.reset( accepting_acceptor );
		accepting_acceptor.newChannel( null, null, "test 2" );
		EasyMock.replay( accepting_acceptor );
		main_acceptor.addDelegateToFront( accepting_acceptor );

		try {
			main_acceptor.newChannel( null, null, "test 2" );
		}
		catch( ChannelRejectedException ex ) {
			ex.printStackTrace();
			fail("Shouldn't have thrown exception");
		}

		EasyMock.verify( accepting_acceptor );
	}


	@Test
	public void testMultipleAcceptors() throws Exception {
		PluggableChannelAcceptor main_acceptor = new PluggableChannelAcceptor();

		// Acceptor mocks
		ChannelAcceptor acceptor1 = EasyMock.createMock( ChannelAcceptor.class );
		ChannelAcceptor acceptor2 = EasyMock.createMock( ChannelAcceptor.class );
		ChannelAcceptor acceptor3 = EasyMock.createMock( ChannelAcceptor.class );

		// Scenario 1:
		//  1) Add acceptor 1, deny connection
		acceptor1.newChannel( null, null, "A" );
		EasyMock.expectLastCall().andThrow( new ChannelRejectedException() );
		EasyMock.replay( acceptor1, acceptor2, acceptor3 );

		main_acceptor.addDelegate( acceptor1 );

		try {
			main_acceptor.newChannel( null, null, "A" );
			fail("Shouldn't have worked");
		}
		catch( ChannelRejectedException ex ) {
			// Expected
		}

		EasyMock.verify( acceptor1, acceptor2, acceptor3 );

		// Scenario 2:
		//  1) Add acceptor 2 (to end)
		//  2) Acceptor 1 will deny connection
		//  2) Acceptor 2 will approve connection
		EasyMock.reset( acceptor1, acceptor2, acceptor3 );

		acceptor1.newChannel( null, null, "B" );
		EasyMock.expectLastCall().andThrow( new ChannelRejectedException() );
		acceptor2.newChannel( null, null, "B" );
		EasyMock.replay( acceptor1, acceptor2, acceptor3 );

		main_acceptor.addDelegate( acceptor2 );

		try {
			main_acceptor.newChannel( null, null, "B" );
		}
		catch( ChannelRejectedException ex ) {
			ex.printStackTrace();
			fail("Should have worked");
		}

		EasyMock.verify( acceptor1, acceptor2, acceptor3 );

		// Scenario 2:
		//  1) Add acceptor 3 (to beginning)
		//  2) Acceptor 3 will approve connection
		EasyMock.reset( acceptor1, acceptor2, acceptor3 );

		acceptor3.newChannel( null, null, "C" );
		EasyMock.replay( acceptor1, acceptor2, acceptor3 );

		main_acceptor.addDelegateToFront( acceptor3 );

		try {
			main_acceptor.newChannel( null, null, "C" );
		}
		catch( ChannelRejectedException ex ) {
			ex.printStackTrace();
			fail("Should have worked");
		}

		EasyMock.verify( acceptor1, acceptor2, acceptor3 );
	}
}
