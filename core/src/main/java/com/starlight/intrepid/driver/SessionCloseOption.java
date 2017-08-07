package com.starlight.intrepid.driver;

/**
 *
 */
public enum SessionCloseOption {
	/** Attempt to flush in-flight messages before closing the session. */
	ATTEMPT_FLUSH,

	/** Close the session immediately, regardless of in-flight messages. */
	IMMEDIATE
}
