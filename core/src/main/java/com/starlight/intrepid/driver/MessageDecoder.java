// Copyright (c) 2010 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Intrepid nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.starlight.intrepid.driver;

import com.logicartisan.common.core.IOKit;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.exception.ServerException;
import com.starlight.intrepid.message.*;
import com.starlight.locale.ResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.UUID;
import java.util.function.BiFunction;


/**
 *
 */
public final class MessageDecoder {
	private static final Logger LOG = LoggerFactory.getLogger( MessageDecoder.class );

	@SuppressWarnings( "DeprecatedIsStillUsed" )
	@Deprecated
	private static final CharsetDecoder UTF16_DECODER =     // proto 0-2
		Charset.forName( "UTF-16" ).newDecoder();
	private static final CharsetDecoder UTF8_DECODER =      // proto 3+
		Charset.forName( "UTF-8" ).newDecoder();



	/**
	 * @return      The message, if the content was successfully consumed. The meaning of
	 *              null will vary for implementations. For a blocking I/O
	 *              implementation, this false will indicate that stream is bad
	 *              because we were unable to retrieve enough data before the end of the
	 *              stream was reached. For a non-blocking implementation (such as MINA),
	 *              this will likely mean that the position of the buffer should be reset
	 *              to its original position (before the decode attempt) and further data
	 *              should be awaited.
	 */
	public static @Nullable IMessage decode( @Nonnull DataSource source,
		@Nullable Byte proto_version,
		@Nonnull ResponseHandler response_handler,
		@Nonnull BiFunction<UUID,String,VMID> vmid_creator ) {

		if ( LOG.isTraceEnabled() ) {
			LOG.trace( "Decoder called: {}", source.hex() );
		}

		// Need at least 4 bytes
		if ( !source.request( 4 ) ) return null;

		// LENGTH
		int length = getDualShortLength( source );
		if ( !source.request( length ) ) {
			LOG.trace( "Decoder not enough data. Length: {} Buffer: {}", length, source );
			return null;
		}

		final DataSource.Tracking tracking_source = source.trackRead();

		// TYPE
		byte type = tracking_source.get();

		IMessageType message_type = IMessageType.findByID( type );
		assert message_type != null : "Unknown type: " + type;

		LOG.trace( "Message type: {} (proto version={})", message_type, proto_version );

		final IMessage message;
		if ( proto_version == null ) {
			switch ( message_type ) {
				case SESSION_INIT:
					message = decodeSessionInit(
						tracking_source, response_handler, vmid_creator );
					break;

				case SESSION_INIT_RESPONSE:
					message = decodeSessionInitResponse(
						tracking_source, response_handler, vmid_creator );
					break;

				case SESSION_CLOSE:
					// A SessionClose will happen here for things like auth failure
					message = decodeSessionClose( tracking_source );
					break;

				default:
					LOG.warn( "Message type {} was received when the protocol version " +
						"is unknown (session init incomplete?)", message_type );
					response_handler.sendMessage(
						new SessionCloseIMessage(
							"Invalid message type: " + message_type, false ),
						SessionCloseOption.ATTEMPT_FLUSH );
					return null;
			}
		}
		else {
			switch ( message_type ) {
				// NOTE: Allowing session init messages even if proto version is known.
				//       Old intrepid versions allow re-init
				case SESSION_INIT:
					message = decodeSessionInit(
						tracking_source, response_handler, vmid_creator );
					break;

				// NOTE: Allowing session init messages even if proto version is known.
				//       Old intrepid versions allow re-init
				case SESSION_INIT_RESPONSE:
					message = decodeSessionInitResponse(
						tracking_source, response_handler, vmid_creator );
					break;

				case SESSION_TOKEN_CHANGE:
					message = decodeSessionTokenChange( proto_version, tracking_source );
					break;

				case SESSION_CLOSE:
					message = decodeSessionClose( tracking_source );
					break;

				case INVOKE:
					message = decodeInvoke( proto_version, tracking_source,
						response_handler );
					break;

				case INVOKE_RETURN:
					message = decodeInvokeReturn( proto_version, tracking_source );
					break;

				case INVOKE_INTERRUPT:
					message = decodeInvokeInterrupt( proto_version, tracking_source );
					break;

				case INVOKE_ACK:
					message = decodeInvokeAck( proto_version, tracking_source );
					break;

				case LEASE:
					message = decodeLease( proto_version, tracking_source );
					break;

				case LEASE_RELEASE:
					message = decodeLeaseRelease( proto_version, tracking_source );
					break;

				case CHANNEL_INIT:
					message = decodeChannelInit( proto_version, tracking_source,
						response_handler );
					break;

				case CHANNEL_INIT_RESPONSE:
					message = decodeChannelInitResponse( proto_version, tracking_source );
					break;

				case CHANNEL_DATA:
					try {
						message = decodeChannelData( proto_version, tracking_source );
					} catch ( EOFException e ) {
						return null;           // Didn't have enough data
					}
					break;

				case CHANNEL_DATA_ACK:
					message = decodeChannelDataAck( proto_version, tracking_source );
					break;

				case CHANNEL_CLOSE:
					message = decodeChannelClose( proto_version, tracking_source );
					break;

				case PING:
					message = decodePing( proto_version, tracking_source );
					break;

				case PING_RESPONSE:
					message = decodePingResponse( proto_version, tracking_source );
					break;

				default:
					LOG.warn( "Unknown message type: {}", message_type );
					response_handler.sendMessage(
						new SessionCloseIMessage(
							"Invalid message type: " + message_type, false ),
						SessionCloseOption.ATTEMPT_FLUSH );
					return null;
			}
		}

		if ( tracking_source.bytesRead() < length ) {
			// NOTE: This is a possibly normal case which can occur when data can't be
			//       deserialized (i.e., user error).
			LOG.debug( "Read too few bytes. Expected: {} Read: {}",
				length, tracking_source.bytesRead() );
			while( tracking_source.bytesRead() < length ) {
				tracking_source.get();
			}
		}

		return message;
	}


	// NOTE: protocol version is unknown
	private static SessionInitIMessage decodeSessionInit( @Nonnull DataSource buffer,
		@Nonnull ResponseHandler response_handler,
		@Nonnull BiFunction<UUID,String,VMID> vmid_creator ) {

		// VERSION
		byte version = buffer.get();

		// MIN PROTOCOL VERSION
		byte min_protocol_version = buffer.get();

		// PREF PROTOCOL VERSION
		byte pref_protocol_version = buffer.get();

		VMID vmid;
		ConnectionArgs connection_args;
		try {
			// VMID
			vmid = readVMID( version < 4, buffer, vmid_creator );

			// CONNECTION ARGS
			connection_args = readPossiblyModernObject( version < 4, true, buffer );
		}
		catch( Exception ex ) {
			LOG.info( "Error while decoding session init vmid/args", ex );

			// Write an error, close the session and return nothing
			response_handler.sendMessage(
				new SessionCloseIMessage(
					"Unable to de-serialize session init info: " + ex.toString(), false ),
				SessionCloseOption.ATTEMPT_FLUSH );
			return null;
		}

		// SERVER PORT
		Integer server_port;
		if ( version == 0 ) server_port = null;
		else {
			if ( buffer.get() == 0 ) server_port = null;
			else server_port = Integer.valueOf( buffer.getInt() );
		}

		// RECONNECT TOKEN
		Serializable reconnect_token;
		if ( version < 2 ) reconnect_token = null;
		else {
			try {
				reconnect_token = readPossiblyModernObject( version < 4, true, buffer );
			}
			catch( Exception ex ) {
				LOG.info( "Error while decoding session init reconnect token", ex );
				reconnect_token = null;
			}
		}

		// REQUESTED ACK RATE
		byte ack_rate = -1;
		if ( version >= 3 ) ack_rate = buffer.get();

		return new SessionInitIMessage( vmid, server_port, connection_args,
			min_protocol_version, pref_protocol_version, reconnect_token, ack_rate );
	}


	// NOTE: protocol version is unknown
	private static SessionInitResponseIMessage decodeSessionInitResponse(
		@Nonnull DataSource buffer,
		@Nonnull ResponseHandler response_handler,
		@Nonnull BiFunction<UUID,String,VMID> vmid_creator ) {

		// VERSION
		byte version = buffer.get();

		// PROTOCOL VERSION
		byte protocol_version = buffer.get();

		VMID vmid;
		try {
			// VMID
			vmid = readVMID( version < 4, buffer, vmid_creator );
		}
		catch( Exception ex ) {
			LOG.info( "Error while decoding session init response vmid", ex );

			// Write an error, close the session and return nothing
			response_handler.sendMessage(
				new SessionCloseIMessage(
					"Unable to de-serialize session init info: " + ex, false ),
				SessionCloseOption.ATTEMPT_FLUSH );
			return null;
		}

		// SERVER PORT
		Integer server_port;
		if ( version == 0 ) server_port = null;
		else {
			if ( buffer.get() == 0 ) server_port = null;
			else server_port = Integer.valueOf( buffer.getInt() );
		}

		// RECONNECT TOKEN
		Serializable reconnect_token;
		if ( version < 2 ) reconnect_token = null;
		else {
			try {
				reconnect_token = readPossiblyModernObject( version < 4, true, buffer );
			}
			catch( Exception ex ) {
				LOG.info( "Error while decoding session init response " +
					"reconnect token", ex );
				reconnect_token = null;
			}
		}

		// ACK RATE
		byte ack_rate;
		if ( version >= 3 ) ack_rate = buffer.get();
		else ack_rate = 0;      // not supported

		return new SessionInitResponseIMessage( vmid, server_port, protocol_version,
			reconnect_token, ack_rate );
	}


	private static InvokeIMessage decodeInvoke( byte proto_version,
		@Nonnull DataSource buffer, @Nonnull ResponseHandler response_handler ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// CALL ID
		int call_id = buffer.getInt();

		// OBJECT ID
		int object_id = buffer.getInt();

		// METHOD ID
		int method_id = buffer.getInt();

		// FLAGS
		byte flags = buffer.get();

		boolean has_args = ( flags & 0x01 ) != 0;
		boolean has_persistent_name = ( flags & 0x02 ) != 0;
		boolean has_user_context = ( flags & 0x04 ) != 0;
		boolean server_perf_stats_requested = ( flags & 0x08 ) != 0;

		// ARGS
		Object[] args;
		if ( !has_args ) args = null;
		else {
			try {
				int length = buffer.get() & 0xFF;					// WARNING: Max of 255
				args = new Object[ length ];
				for( int i = 0; i < args.length; i++ ) {
					args[ i ] = readObject( buffer );
				}
			}
			catch( Exception ex ) {
				LOG.info( "Unable to de-serialize method argument", ex );
				// Write an error and return nothing
				response_handler.sendMessage(
					new InvokeReturnIMessage( call_id,
						new ServerException( "Error de-serializing method argument", ex ),
						true, null, null ), null );
				return null;
			}
		}

		// PERSISTENT NAME
		String persistent_name = null;
		if ( has_persistent_name ) {
			try {
				//noinspection deprecation
				persistent_name = buffer.getString(
					proto_version >= 3 ? UTF8_DECODER : UTF16_DECODER,
					c -> {} );

				LOG.trace( "  decoded persistent name: {}", persistent_name );
			}
			catch ( CharacterCodingException ex ) {
				LOG.info( "Error decoding object persistent name", ex );
				// Write an error and return nothing
				response_handler.sendMessage(
					new InvokeReturnIMessage( call_id,
						new ServerException( "Error decoding object persistent name", ex ),
						true, null, null ), null );
				return null;
			}
		}

		// USER CONTEXT
		UserContextInfo user_context = null;
		if ( has_user_context ) {
			try {
				if ( LOG.isTraceEnabled() ) {
					LOG.trace( "Hex dump after context position: {}", buffer.hex() );
				}

				user_context = readObject( buffer );
			}
			catch( Exception ex ) {
				LOG.info( "Error de-serializing user context", ex );
				// Write an error and return nothing
				response_handler.sendMessage(
					new InvokeReturnIMessage( call_id,
						new ServerException( "Error de-serializing user context", ex ),
						true, null, null ), null );
				return null;
			}
		}

		return new InvokeIMessage( call_id, object_id, persistent_name, method_id, args,
			user_context, server_perf_stats_requested );
	}


	private static InvokeReturnIMessage decodeInvokeReturn( byte proto_version,
		@Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// CALL ID
		int call_id = buffer.getInt();

		Object value;
		boolean is_thrown = false;

		// FLAGS
		//   1 - has normal value (1 & 2 cannot both be set)
		//   2 - has thrown value (1 & 2 cannot both be set)
		//   4 - has new object ID
		//   8 - has server time
		byte flags = buffer.get();

		boolean has_normal_value = ( flags & 0x01 ) != 0;
		boolean has_thrown_value = ( flags & 0x02 ) != 0;
		boolean has_new_object_id = ( flags & 0x04 ) != 0;
		boolean has_server_time = ( flags & 0x08 ) != 0;

		// VALUE
		if ( has_normal_value || has_thrown_value ) {
			try {
				value = readObject( buffer );
				is_thrown = has_thrown_value;
			}
			catch ( Exception e ) {
				value = e;
				is_thrown = true;
			}
		}
		else value = null;

		// NEW OBJECT ID
		Integer new_object_id = null;
		if ( has_new_object_id ) {
			new_object_id = Integer.valueOf( buffer.getInt() );
		}

		// SERVER TIME
		Long server_time = null;
		if ( has_server_time ) {
			server_time = Long.valueOf( buffer.getLong() );
		}

		return new InvokeReturnIMessage( call_id, value, is_thrown, new_object_id,
			server_time );
	}


	private static InvokeInterruptIMessage decodeInvokeInterrupt(
		byte proto_version, @Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// CALL ID
		int call_id = buffer.getInt();

		return new InvokeInterruptIMessage( call_id );
	}


	private static InvokeAckIMessage decodeInvokeAck( byte proto_version,
		@Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// CALL ID
		int call_id = buffer.getInt();

		return new InvokeAckIMessage( call_id );
	}


	private static SessionTokenChangeIMessage decodeSessionTokenChange(
		byte proto_version, @Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}


		// NEW RECONNECT TOKEN
		Serializable reconnect_token;
		try {
			if ( buffer.get() > 0 ) {
				reconnect_token = readObject( buffer );
			}
			else reconnect_token = null;
		}
		catch( Exception ex ) {
			LOG.warn( "Error while decoding session token change reconnect token", ex );
			reconnect_token = null;
		}

		return new SessionTokenChangeIMessage( reconnect_token );
	}


	private static SessionCloseIMessage decodeSessionClose( @Nonnull DataSource buffer ) {
		// VERSION or PROTOCOL_VERSION
		byte version_or_protocol_version = buffer.get();

		byte proto_version;
		if ( version_or_protocol_version == 0 ) proto_version = 2;
		else proto_version = version_or_protocol_version;

		// REASON
		String reason = readStringOrLegacyResourceKey( proto_version, buffer );

		// AUTH FAILURE
		boolean is_auth_failure = buffer.get() != 0;

		return new SessionCloseIMessage( reason, is_auth_failure );
	}


	private static LeaseIMessage decodeLease( byte proto_version,
		@Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// OID's
		int[] oids = new int[ buffer.get() & 0xff ];
		for ( int i = 0; i < oids.length; i++ ) {
			oids[ i ] = buffer.getInt();
		}

		return new LeaseIMessage( oids );
	}

	private static LeaseReleaseIMessage decodeLeaseRelease( byte proto_version,
		@Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// OID's
		int[] oids = new int[ buffer.get() & 0xff ];
		for ( int i = 0; i < oids.length; i++ ) {
			oids[ i ] = buffer.getInt();
		}

		return new LeaseReleaseIMessage( oids );
	}


	private static ChannelInitIMessage decodeChannelInit( byte proto_version,
		@Nonnull DataSource buffer, @Nonnull ResponseHandler response_handler ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// REQUEST ID
		int request_id = buffer.getInt();

		// ATTACHMENT
		Serializable attachment = null;
		if ( buffer.get() != 0 ) {
			try {
				attachment = readObject( buffer );
			}
			catch( Exception ex ) {
				LOG.warn( "Error while decoding channel init attachment", ex );

				// Write an error, close the session and return nothing
				response_handler.sendMessage(
					new ChannelInitResponseIMessage( request_id,
						"Unable to de-serialize channel attachment" ),
					SessionCloseOption.ATTEMPT_FLUSH );
				return null;
			}
		}

		// CHANNEL ID
		short channel_id = buffer.getShort();

		// RX WINDOW - added in proto 3
		int rx_window = ( proto_version >= 3 ) ? buffer.getInt() : 0;

		return new ChannelInitIMessage( request_id, attachment, channel_id, rx_window );
	}

	private static ChannelInitResponseIMessage decodeChannelInitResponse(
		byte proto_version, @Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// REQUEST ID
		int request_id = buffer.getInt();

		// REJECTED
		boolean rejected = buffer.get() == 1;

		if ( rejected ) {
			// REJECT REASON
			String reason = readStringOrLegacyResourceKey( proto_version, buffer );

			return new ChannelInitResponseIMessage( request_id, reason );
		}
		else {
			// RX WINDOW - added in proto 3
			int rx_window = ( proto_version >= 3 ) ? buffer.getInt() : 0;

			return new ChannelInitResponseIMessage( request_id, rx_window );
		}
	}

	private static ChannelDataIMessage decodeChannelData( byte proto_version,
		@Nonnull DataSource buffer ) throws EOFException {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// CHANNEL ID
		short channel_id = buffer.getShort();

		// DATA
		int length = buffer.getInt();

		// TODO: caching?
		byte[] data = new byte[ length ];
		buffer.getFully( data );
		ByteBuffer data_buffer = ByteBuffer.wrap( data );

		// MESSAGE ID - added in proto 3
		short message_id = ( proto_version >= 3 ) ? buffer.getShort() : 0;

		return ChannelDataIMessage.create( channel_id, message_id, data_buffer );
	}

	private static ChannelDataAckIMessage decodeChannelDataAck(
		byte proto_version, @Nonnull DataSource buffer ) {

		assert proto_version >= 3 :
			"Invalid proto version for channel data ack: " + proto_version;

		// CHANNEL ID
		short channel_id = buffer.getShort();

		// MESSAGE ID
		short message_id = buffer.getShort();

		// NEW WINDOW SIZE
		int new_window_size;
		byte bite_one = buffer.get();
		if ( ( bite_one & 0x80 ) != 0 ) {
			new_window_size = -1;
		}
		else {
			byte two = buffer.get();
			byte three = buffer.get();
			byte four = buffer.get();


			new_window_size = ( ( bite_one & 0xFF ) << 24 ) |  ( ( two & 0xFF ) << 16 ) |
				( ( three & 0xFF ) << 8 ) | ( ( four & 0xFF ) );
		}

		return new ChannelDataAckIMessage( channel_id, message_id, new_window_size );
	}

	private static ChannelCloseIMessage decodeChannelClose( byte proto_version,
		@Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// CHANNEL ID
		short channel_id = buffer.getShort();

		return new ChannelCloseIMessage( channel_id );
	}

	private static PingIMessage decodePing( byte proto_version,
		@Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// SEQUENCE NUMBER
		short seq_number = buffer.getShort();

		return new PingIMessage( seq_number );
	}

	private static PingResponseIMessage decodePingResponse( byte proto_version,
		@Nonnull DataSource buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.get();
		}

		// SEQUENCE NUMBER
		short seq_number = buffer.getShort();

		return new PingResponseIMessage( seq_number );
	}

//	private Object readObject( IoBuffer buffer, int call_id )
//		throws IOException, ClassNotFoundException {
//
//		byte shortcut_id = buffer.get();
//
//		SerializationShortCut shortcut = SerializationShortCut.findByID( shortcut_id );
//		if ( shortcut == null ) {
//			throw new ClassNotFoundException( "Unknown serialization shortcut: " +
//				shortcut_id );
//		}
//
//		switch ( shortcut ) {
//			case NONE:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): object" );
//				return IoBufferSerialization.getObject( buffer );
//
//			case NULL:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): null" );
//				return null;
//
//			case BYTE_ARRAY:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): byte[]" );
//				int length = getDualShortLength( buffer );
//				if ( DEBUG ) System.out.println( "read byte array: " + length );
//				byte[] data = new byte[ length ];
//				buffer.get( data );
//				return data;
//
//			case BYTE:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): byte" );
//				return Byte.valueOf( buffer.get() );
//
//			case SHORT:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): short" );
//				return Short.valueOf( buffer.getShort() );
//
//			case INT:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): int" );
//				return Integer.valueOf( buffer.getInt() );
//
//			case LONG:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): long" );
//				return Long.valueOf( buffer.getLong() );
//
//			case FLOAT:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): float" );
//				return Float.valueOf( buffer.getFloat() );
//
//			case DOUBLE:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): double" );
//				return Double.valueOf( buffer.getDouble() );
//
//			case BOOLEAN_TRUE:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): boolean (true)" );
//				return Boolean.TRUE;
//			case BOOLEAN_FALSE:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): boolean (false)" );
//				return Boolean.FALSE;
//
//			case STRING:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): string" );
//				try {
//					return buffer.getString( STRING_DECODER );
//				}
//				catch ( CharacterCodingException e ) {
//					// Shouldn't happen
//					throw new IntrepidRuntimeException( e );
//				}
//
//			default:
//				if ( DEBUG ) System.out.println( ">>> READ (" + call_id + "): ERROR" );
//				assert false : "Unhandled SerializationShortCut: " + shortcut;
//				throw new ClassNotFoundException( "Unhandled serialization shortcut: " +
//					shortcut );
//		}
//	}


	// Visible for testing
	static int getDualShortLength( @Nonnull DataSource buffer ) {
		short s_length = buffer.getShort();
		if ( s_length < 0 ) {
			short low_short = buffer.getShort();
			return ( ( s_length & 0x7FFF ) << 16 ) | ( low_short & 0xFFFF );
		}
		else return s_length & 0xFFFF;
	}



	private static <T> T readObject( DataSource source )
		throws IOException, ClassNotFoundException {

		// Hint to StarLight Common's IOKit that we're doing a bunch of deserialization
		IOKit.DESERIALIZATION_HINT.set( Boolean.TRUE );
		try ( ObjectInputStream in = new ObjectInputStream( source.inputStream() ) ) {
			//noinspection unchecked
			return ( T ) in.readObject();
		}
		finally {
			IOKit.DESERIALIZATION_HINT.remove();
		}
	}


	private static @Nullable String readStringOrLegacyResourceKey(
		byte proto_version, @Nonnull DataSource buffer ) {

		if ( buffer.get() != 0 ) {
			if ( proto_version >= 3 ) {
				try {
					return buffer.getString( UTF8_DECODER, c -> {} );
				}
				catch ( CharacterCodingException ex ) {
					LOG.warn( "Unable to decode channel rejection reason", ex );
				}
			}
			else {
				try {
					//noinspection unchecked
					ResourceKey<String> reject_reason = readObject( buffer );
					return reject_reason.getValue();
				}
				catch ( Exception ex ) {
					LOG.warn( "Unable to decode channel rejection reason (old " +
						"proto version={})", proto_version, ex );
				}
			}
		}

		return null;
	}


	private static @Nonnull VMID readVMID( boolean is_legacy,
		@Nonnull DataSource buffer, BiFunction<UUID,String,VMID> vmid_creator )
		throws IOException, ClassNotFoundException {

		if ( !is_legacy ) {
			long lsb = buffer.getLong();
			long hsb = buffer.getLong();
			String hint_text = null;
			int length = buffer.getShort() & 0xFFFF;
			if ( length != 0 ) {
				hint_text = buffer.getString( UTF8_DECODER, length );
			}
			return vmid_creator.apply( new UUID( hsb, lsb ), hint_text );
		}
		else {
			return ( VMID ) readObject( buffer );
		}
	}

	private static @Nullable <T> T readPossiblyModernObject( boolean is_legacy,
		boolean modern_length_is_short, @Nonnull DataSource buffer )
		throws IOException, ClassNotFoundException {

		if ( is_legacy ) {
			if ( buffer.get() == 0 ) return null;

			return readObject( buffer );
		}
		else {
			int length = modern_length_is_short ?
				buffer.getShort() & 0xFFFF : buffer.getInt();
			if ( length == 0 ) return null;

			// TODO #11: Object decoder should go here
			byte[] data = new byte[ length ];
			buffer.getFully( data );
			try ( ObjectInputStream in =
				new ObjectInputStream( new ByteArrayInputStream( data ) ) ) {

				//noinspection unchecked
				return ( T ) in.readObject();
			}
		}
	}
}
