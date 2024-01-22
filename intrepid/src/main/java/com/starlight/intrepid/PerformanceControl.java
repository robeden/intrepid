package com.starlight.intrepid;

/**
 * This interface provides control over various performance tunings that are generally
 * used for debugging.
 */
public interface PerformanceControl {
	/**
	 * Used to add an artificial delay before sending messages to simulate network
	 * latency.
	 *
	 * @param delay_ms  The delay in milliseconds or null for none.
	 */
	void setMessageSendDelay( Long delay_ms );
}
