package com.starlight.intrepid.auth;

import com.starlight.locale.ResourceKey;

import java.net.SocketAddress;


/**
 * See {@link RequestUserCredentialReinit} for a full explanation.
 */
public interface UserCredentialReinitAuthenticationHandler extends AuthenticationHandler {
	/**
	 * Get the user credentials to be associated with a connection using the session
	 * re-init protocol (see {@link RequestUserCredentialReinit} for more
	 * information). This method can take a long time to return.
	 *
	 * @param remote_address        Address of the remote peer.
	 * @param session_source        The SPI-dependent "source" for the session. In the
	 *                              normal case, this would be a SocketChannel or Socket.
	 *                              This is optional and my return null if not supported.
	 *
	 * @return                      Credentials for the user.
	 * 
	 * @throws ConnectionAuthFailureException		Thrown if the connection should be
	 * 												rejected.
	 */
	public UserCredentialsConnectionArgs getUserCredentials( SocketAddress remote_address,
		Object session_source ) throws ConnectionAuthFailureException;


	/**
	 * This is called if an authentication failure occurs in response to the credentials
	 * returned from {@link #getUserCredentials(java.net.SocketAddress, Object)}.
	 *
	 * @param error_message     The error given by the server.
	 */
	public void notifyUserCredentialFailure( ResourceKey<String> error_message );
}