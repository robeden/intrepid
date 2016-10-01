package com.logicartisan.intrepid;

import com.starlight.thread.ObjectSlot;
import junit.framework.TestCase;


/**
 *
 */
public class IntrepidInstanceListenerTest extends TestCase {
	private Intrepid instance;

	@Override
	protected void tearDown() throws Exception {
		if ( instance != null ) instance.close();
	}

	public void testInstanceListener() throws Exception {
		final ObjectSlot<VMID> vmid_slot = new ObjectSlot<VMID>();
		final ObjectSlot<Intrepid> instance_slot = new ObjectSlot<Intrepid>();

		IntrepidInstanceListener listener = new IntrepidInstanceListener() {
			@Override
			public void instanceOpened( VMID vmid, Intrepid instance ) {
				vmid_slot.set( vmid );
				instance_slot.set( instance );
			}

			@Override
			public void instanceClosed( VMID vmid ) {
				vmid_slot.set( vmid );
			}
		};

		Intrepid.addInstanceListener( listener );

		assertNull( vmid_slot.get() );
		assertNull( instance_slot.get() );

		instance = Intrepid.create( null );

		assertSame( instance, instance_slot.waitForValue( 1000 ) );
		instance_slot.clear();
		assertSame( instance.getLocalVMID(), vmid_slot.waitForValue( 1000 ) );
		vmid_slot.clear();

		instance.close();

		assertSame( instance.getLocalVMID(), vmid_slot.waitForValue( 1000 ) );
	}
}
