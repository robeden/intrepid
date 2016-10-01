package com.logicartisan.intrepid.auth;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


/**
 * A special derivation of ConnectionArgs that can be used in a special situation where
 * a connection should be started from a "server" type connection but requires user
 * credentials from the peer to fully initialize the system. The flow is as follows:
 * <pre>
 *          Host A                  Host B
 *          ------                  ------
 *             |                      |
 *             |---------- 1 --------&gt;|
 *             |                      |
 *             |&lt;--------- 2 ---------|
 *             |                      |
 *             |---------- 3 --------&gt;|
 * </pre>
 * <ol>
 *   <li><strong>Host A</strong> opens a connection to <strong>Host B</strong> using a
 *       <tt>RequestUserCredentialReinit</tt> for the connection args in
 *       the SessionInitIMessage message.</li>
 *   <li>(Possibly after an extended period) <strong>Host B</strong> responds with another
 *       SessionInitIMessage, this time with UserCredentialsConnectionArgs (or a child
 *       class). If <strong>Host B</strong> doesn't support the re-init mechanism, a
 *       SessionInitResponseIMessage is sent back with an error and the connection is
 *       closed.</li>
 *   <li><strong>Host A</strong> responds to the SessionInitIMessage as though it were a
 *       normal connection with user credentials.</li>
 * </ol>
 * <p>
 * This is definitely a specialized situation intended for cases where the system with the
 * user ("Host B" in the above example) is not allow to establish an outbound connection
 * so support of it requires an implementation of {@link AuthenticationHandler} which
 * implements the {@link UserCredentialReinitAuthenticationHandler} interface. If the
 * installed auth handler doesn't do this, sessions with
 * RequestUserCredentialReinit connection args will be rejected.
 */
public class RequestUserCredentialReinit
	implements ConnectionArgs, Externalizable {

	private static final long serialVersionUID = 8194173945012265233L;



	@Override
	public void readExternal( ObjectInput in )
		throws IOException, ClassNotFoundException {

		// VERSION
		in.readByte();
	}

	@Override
	public void writeExternal( ObjectOutput out ) throws IOException {
		// VERSION
		out.writeByte( 0 );
	}
}
