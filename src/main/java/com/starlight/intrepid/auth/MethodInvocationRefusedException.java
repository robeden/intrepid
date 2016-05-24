package com.starlight.intrepid.auth;

import com.starlight.intrepid.exception.IntrepidRuntimeException;

/**
 * A security exception indicating a refusal by the server to fulfill a method invocation.
 */
public class MethodInvocationRefusedException extends IntrepidRuntimeException {
	public MethodInvocationRefusedException( String message, Throwable cause ) {
		super( message, cause );
	}

	public MethodInvocationRefusedException( String message ) {
		super( message );
	}

	public MethodInvocationRefusedException( Throwable cause ) {
		super( cause );
	}

	public MethodInvocationRefusedException() {
		super();
	}
}
