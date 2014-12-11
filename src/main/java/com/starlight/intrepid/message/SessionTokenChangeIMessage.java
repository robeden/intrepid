package com.starlight.intrepid.message;

import java.io.Serializable;


/**
 * This message is sent periodically when a
 * {@link com.starlight.intrepid.auth.TokenReconnectAuthenticationHandler} is used with
 * {@link com.starlight.intrepid.auth.TokenReconnectAuthenticationHandler#getTokenRegenerationInterval() token expiration}.
 * In that case, this will be sent to clients when new tokens are generated.
 * <p/>
 * This message was introduced in stream version 1.
 */
public class SessionTokenChangeIMessage implements IMessage {
	private final Serializable new_reconnect_token;

	public SessionTokenChangeIMessage( Serializable new_reconnect_token ) {
		this.new_reconnect_token = new_reconnect_token;
	}

	@Override
	public IMessageType getType() {
		return IMessageType.SESSION_TOKEN_CHANGE;
	}

	public Serializable getNewReconnectToken() {
		return new_reconnect_token;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "SessionTokenChangeIMessage" );
		sb.append( "{new_reconnect_token=" ).append( new_reconnect_token );
		sb.append( '}' );
		return sb.toString();
	}
}
