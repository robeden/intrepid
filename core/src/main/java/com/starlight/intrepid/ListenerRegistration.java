package com.starlight.intrepid;

/**
 *
 */
public interface ListenerRegistration {
	/**
	 * Remove the listener (if a remove method was provided) and cancel listening for
	 * connection events.
	 */
	void remove();


	/**
	 * Returns the current connection state.
	 *
	 * @return  True if there is an active connection.
	 */
	boolean isCurrentlyConnected();
}
