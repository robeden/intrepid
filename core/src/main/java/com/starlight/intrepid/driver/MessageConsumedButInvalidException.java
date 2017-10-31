package com.starlight.intrepid.driver;

/**
 * Indicates that a message was entirely consumed by {@link MessageDecoder}, but that
 * the message was invalid and so will not be provided.
 */
public class MessageConsumedButInvalidException extends Exception {
	MessageConsumedButInvalidException( String message ) {
		super( message );
	}

	MessageConsumedButInvalidException( String message, Throwable cause ) {
		super( message, cause );
	}
}
