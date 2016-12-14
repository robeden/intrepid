package com.logicartisan.intrepid.exception;

import com.logicartisan.intrepid.Intrepid;

import java.io.IOException;


/**
 * Thrown when authentication/authorization failed when
 * {@link Intrepid#connect a connection attempt} fails.
 */
public class ConnectionFailureException extends IOException {
	private static final long serialVersionUID = 8540842569376607552L;



	public ConnectionFailureException( Throwable throwable ) {
		super( throwable );
	}

	public ConnectionFailureException( String s, Throwable throwable ) {
		super( s, throwable );
	}

	public ConnectionFailureException( String s ) {
		super( s );
	}

	public ConnectionFailureException() {
		super();
	}
}
