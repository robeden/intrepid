package com.logicartisan.intrepid;

/**
 * Listener which can be used to be notified when a new instance is created or closed.
 */
public interface IntrepidInstanceListener {
	void instanceOpened( VMID vmid, Intrepid instance );

	void instanceClosed( VMID vmid );
}
