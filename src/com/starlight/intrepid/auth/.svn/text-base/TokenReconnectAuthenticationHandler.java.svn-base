package com.starlight.intrepid.auth;

import java.io.Serializable;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;


/**
 * This extension to {@link AuthenticationHandler} allows reconnection using a token
 * rather than just the username and password. The reconnect token is passed to the client
 * as part of the {@link com.starlight.intrepid.message.SessionInitResponseIMessage}. When
 * provided, the client will supply the token back to the server in the
 * {@link com.starlight.intrepid.message.SessionInitIMessage} when reconnecting.
 * <p/>
 * The reconnect token must be considered optional. That is, if the token cannot be passed
 * to the client (for example, due to a serialization error), the server should be able to
 * deal with not having the token provided.
 */
public abstract class TokenReconnectAuthenticationHandler
	implements AuthenticationHandler {

	/**
	 * Determine whether a connection should be allowed or not. This is equivalent to
	 * {@link AuthenticationHandler#checkConnection} but adds the <tt>reconnect_token</tt>
	 * as a possible argument.
	 *
	 * @param connection_args		Connection arguments, if any. See
	 * 					{@link com.starlight.intrepid.auth.UserCredentialsConnectionArgs}.
	 * @param remote_address        Address of the remote peer.
	 * @param session_source        The SPI-dependent "source" for the session. In the
	 *                              normal case, this would be a SocketChannel or Socket.
	 *                              This is optional and my return null if not supported.
	 * @param reconnect_token       The reconnect token provided by the client.
	 *                              This should be considered an optional argument, even
	 *                              if it is known that a client has previously been
	 *                              provided a token.
	 *
	 * @return		An object containing context info about the user/service connecting.
	 * 				This information will be available while operating inside a call
	 * 				via the {@link com.starlight.intrepid.IntrepidContext} class. It is
	 * 				valid to return null, in which case no user information will be
	 * 				available.
	 * @throws ConnectionAuthFailureException		Thrown if the connection should be
	 * 												rejected.
	 */
	public abstract UserContextInfo checkConnection( ConnectionArgs connection_args,
		SocketAddress remote_address, Object session_source, Serializable reconnect_token )
		throws ConnectionAuthFailureException;

	/**
	 * This will be called following a successful
	 * {@link AuthenticationHandler#checkConnection} call to allow the handler to provide
	 * a reconnect token for the session. It is not required that one be provided.
	 *
	 * @param user_context      The context returned from <tt>checkConnection</tt>.
	 * @param connection_args   The connection arguments originally provided to
	 *                          <tt>checkConnection</tt>.
	 * @param remote_address    The remote address of the caller, which was originally
	 *                          provided to <tt>checkConnection</tt>.
	 * @param session_source    The session source that was originally
	 *                          provided to <tt>checkConnection</tt>.
	 * @param previous_reconnect_token  The previous reconnect token (if applicable),
	 *                          which was originally provided to <tt>checkConnection</tt>.
	 *
	 * @return                  The session reconnect token.
	 */
	public abstract Serializable generateReconnectToken( UserContextInfo user_context,
		ConnectionArgs connection_args, SocketAddress remote_address,
		Object session_source, Serializable previous_reconnect_token );


	/**
	 * This returns the interval (in seconds) at which new reconnect tokens should be
	 * generated for active session that were initially given tokens. If a value greater
	 * than zero is return, the {@link #generateReconnectToken} method will be called at
	 * this interval to recreate a token for sessions, which will then be given to the
	 * client, if possible.
	 * <p/>
	 * It is advisable for the server to allow some overlap in tokens to allow for
	 * transmission delays. For example, if the interval is 1 hour, making tokens valid
	 * for an hour and ten minutes would be reasonable.
	 * <p/>
	 * The default implementation is 1 hour, but this can be overridden to adjust as
	 * desired.
	 *
	 * @return                  The interval (in seconds) at which session reconnect
	 *                          tokens should be regenerated.
	 */
	public int getTokenRegenerationInterval() {
		return ( int ) TimeUnit.HOURS.toSeconds( 1 );
	}



	@Override
	public final UserContextInfo checkConnection( ConnectionArgs connection_args,
		SocketAddress remote_address, Object session_source )
		throws ConnectionAuthFailureException {

		return checkConnection( connection_args, remote_address, session_source, null );
	}
}
