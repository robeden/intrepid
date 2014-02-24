package com.starlight.intrepid.spi;

/**
 *
 */
public class ProtocolVersions {
	/** Current protocol version desired by this instance. */
	// Protocol version history:
	// 0             - initial release
	// 1 (6/10/2012) - reconnect token handling
	//                 introduction of SessionTokenChangeIMessage
	// 2 (2/20/2014) - method invocation acknowledgement
	//                 introduction of InvokeAckIMessage
	public static final byte PROTOCOL_VERSION = 2;

	/** Minimum protocol version this instance can handle. */
	public static final byte MIN_PROTOCOL_VERSION = 0;



	/**
	 * This will return the negotiated protocol version based of what the peer and this
	 * instance support.
	 *
	 * @param peer_min_version      Minimum version supported by the peer.
	 * @param peer_pref_version     Version preferred by the peer.
	 *
	 * @return      The negotiated version or -1 if no suitable version was found.
	 */
	public static byte negotiateProtocolVersion( byte peer_min_version,
		byte peer_pref_version ) {

		// If they're preferred version is smaller than our minimum, then not compatible
		if ( peer_pref_version < MIN_PROTOCOL_VERSION ||
			PROTOCOL_VERSION < peer_min_version ) {

			return -1;
		}

		if ( PROTOCOL_VERSION < peer_min_version ) {
			return PROTOCOL_VERSION;
		}
		else return peer_pref_version;
	}



	/**
	 * Indicates whether or not reconnect token handling (SessionTokenChangeIMessage)
	 * is supported in a given version.
	 */
	public static boolean supportsReconnectTokens( byte version ) {
		return version >= 1;
	}

	/**
	 * Indicates whether or not method invocation acknowledgement (InvokeAckIMessage)
	 * is supported in a given version.
	 */
	public static boolean supportsMethodAck( byte version ) {
		return version >= 2;
	}
}
