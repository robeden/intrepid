package com.logicartisan.intrepid;

/**
 *
 */
public interface ListenerRegistration {
	/**
	 * Remove the listener (if a remove method was provided) and cancel listening for
	 * connection events.
	 */
	public void remove();


	/**
	 * Returns the current connection state.
	 *
	 * @return  True if there is an active connection.
	 */
	public boolean isCurrentlyConnected();
}
