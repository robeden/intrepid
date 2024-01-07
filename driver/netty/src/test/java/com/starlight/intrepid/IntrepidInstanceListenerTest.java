package com.starlight.intrepid;

import com.logicartisan.common.core.thread.ObjectSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 *
 */
public class IntrepidInstanceListenerTest {
	private Intrepid instance;

	@AfterEach
	public void tearDown() throws Exception {
		if ( instance != null ) instance.close();
	}

	@Test
	public void testInstanceListener() throws Exception {
		final ObjectSlot<VMID> vmid_slot = new ObjectSlot<>();
		final ObjectSlot<Intrepid> instance_slot = new ObjectSlot<>();

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

		assertNull(vmid_slot.get());
		assertNull(instance_slot.get());

		instance = Intrepid.newBuilder().build();

		assertSame(instance, instance_slot.waitForValue( 1000 ));
		instance_slot.clear();
		assertSame(instance.getLocalVMID(), vmid_slot.waitForValue( 1000 ));
		vmid_slot.clear();

		instance.close();

		assertSame(instance.getLocalVMID(), vmid_slot.waitForValue( 1000 ));
	}
}
