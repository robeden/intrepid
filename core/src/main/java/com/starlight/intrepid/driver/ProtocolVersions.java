package com.starlight.intrepid.driver;

import java.util.OptionalInt;


/**
 *
 */
public class ProtocolVersions {
	/** Current protocol version desired by this instance. */
	// Protocol version history:
	// 0              - initial release
	// 1 (2012-06-10) - reconnect token handling
	//                  introduction of SessionTokenChangeIMessage
	// 2 (2014-02-20) - method invocation acknowledgement
	//                  introduction of InvokeAckIMessage
	// 3 (1.7.0)      - byte channel RX windows (ChannelDataAckIMessage, etc.)
	//                  UTF-8 strings
	//                  Change ChannelInitResponse.reject_reason to string
	//                  Drop version header from all messages, except ChannelInit
	public static final byte PROTOCOL_VERSION = 3;  // REMINDER: update tests on change!

	/** Minimum protocol version this instance can handle. */
	public static final byte MIN_PROTOCOL_VERSION;
	static {
		int min_proto = Integer.getInteger( "intrepid.min_supported_protocol", 3 );
		if ( min_proto < 0 || min_proto > PROTOCOL_VERSION ) {
			throw new IllegalArgumentException( "Invalid minimum supported protocol " +
				"specified via system property: " + min_proto );
		}
		MIN_PROTOCOL_VERSION = ( byte ) min_proto;
	}



	/**
	 * This will return the negotiated protocol version based of what the peer and this
	 * instance support.
	 *
	 * @param peer_min_version      Minimum version supported by the peer.
	 * @param peer_pref_version     Version preferred by the peer.
	 *
	 * @return      The negotiated version or -1 if no suitable version was found.
	 */
	public static OptionalInt negotiateProtocolVersion( byte peer_min_version,
		byte peer_pref_version ) {

		return negotiateProtocolVersion( peer_min_version, peer_pref_version,
			MIN_PROTOCOL_VERSION, PROTOCOL_VERSION );
	}


	static OptionalInt negotiateProtocolVersion(
		byte peer_min_version, byte peer_pref_version,
		byte our_min_version, byte our_pref_version ) {

		// If they're preferred version is smaller than our minimum, then not compatible
		if ( peer_pref_version < our_min_version ||
			our_pref_version < peer_min_version ) {

			return OptionalInt.empty();
		}

		if ( our_pref_version < peer_pref_version ) {
			return OptionalInt.of( our_pref_version & 0xff );
		}
		else return OptionalInt.of( peer_pref_version & 0xff );
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


	/**
	 * Indicates whether or not channel data RX windows are supported
	 * (ChannelDataAckIMessage, etc.).
	 */
	public static boolean supportsChannelDataRxWindow( byte version ) {
		return version >= 3;
	}
}
