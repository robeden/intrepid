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

package com.logicartisan.intrepid.spi.mina;

import com.logicartisan.common.core.IOKit;
import com.logicartisan.intrepid.VMID;
import com.logicartisan.intrepid.auth.ConnectionArgs;
import com.logicartisan.intrepid.auth.UserContextInfo;
import com.logicartisan.intrepid.exception.ServerException;
import com.logicartisan.intrepid.message.*;
import com.starlight.locale.FormattedTextResourceKey;
import com.starlight.locale.ResourceKey;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;


/**
 *
 */
class IMessageDecoder extends CumulativeProtocolDecoder {
	private static final Logger LOG = LoggerFactory.getLogger( IMessageDecoder.class );

	private static final boolean DEBUG = false;

	private static final CharsetDecoder STRING_DECODER =
		Charset.forName( "UTF-16" ).newDecoder();

	private final VMID vmid;
	private final ThreadLocal<VMID> deserialization_context_vmid;

	IMessageDecoder( VMID vmid, ThreadLocal<VMID> deserialization_context_vmid ) {
		this.vmid = vmid;
		this.deserialization_context_vmid = deserialization_context_vmid;
	}


	@Override
	protected boolean doDecode( IoSession session, IoBuffer in,
		ProtocolDecoderOutput out ) throws Exception {

		deserialization_context_vmid.set( vmid );

		// Hint to StarLight Common's IOKit that we're doing a bunch of deserialization
		IOKit.DESERIALIZATION_HINT.set( Boolean.TRUE );
		try {
			IMessage message = decode( in, session );

			if ( message == null ) return false;
			else {
				out.write( message );
				return true;
			}
		}
		finally {
			IOKit.DESERIALIZATION_HINT.remove();
			deserialization_context_vmid.remove();
		}
	}


	static IMessage decode( IoBuffer buffer, IoSession session ) {
		if ( DEBUG ) System.out.println( "*** Decoder called: " + buffer.getHexDump() );

		// Need at least 4 bytes
		if ( buffer.remaining() < 4 ) return null;

		// Remember the initial position
		int starting_position = buffer.position();

		// LENGTH
		int length = getDualShortLength( buffer );
		if ( buffer.remaining() < length ) {
			if ( DEBUG ) System.out.println( "*** Decoder not enough data. Remaining: " +
				buffer.remaining() + "  Length: " + length + "  Buffer: " + buffer );
			buffer.position( starting_position );
			return null;
		}

		int position_after_length = buffer.position();

		// TYPE
		byte type = buffer.get();
		IMessageType message_type = IMessageType.findByID( type );
		assert message_type != null : "Unknown type: " + type;

		if ( DEBUG ) System.out.println( "*** Message type: " + message_type );

		final IMessage message;
		switch ( message_type ) {
			case SESSION_INIT:
				message = decodeSessionInit( buffer, session );
				break;

			case SESSION_INIT_RESPONSE:
				message = decodeSessionInitResponse( buffer, session );
				break;

			case SESSION_TOKEN_CHANGE:
				message = decodeSessionTokenChange( buffer );
				break;

			case SESSION_CLOSE:
				message = decodeSessionClose( buffer );
				break;

			case INVOKE:
				message = decodeInvoke( buffer, session );
				break;

			case INVOKE_RETURN:
				message = decodeInvokeReturn( buffer );
				break;

			case INVOKE_INTERRUPT:
				message = decodeInvokeInterrupt( buffer );
				break;

			case INVOKE_ACK:
				message = decodeInvokeAck( buffer );
				break;

			case LEASE:
				message = decodeLease( buffer );
				break;

			case LEASE_RELEASE:
				message = decodeLeaseRelease( buffer );
				break;

			case CHANNEL_INIT:
				message = decodeChannelInit( buffer, session );
				break;

			case CHANNEL_INIT_RESPONSE:
				message = decodeChannelInitResponse( buffer );
				break;

			case CHANNEL_DATA:
				message = decodeChannelData( buffer );
				break;

			case CHANNEL_CLOSE:
				message = decodeChannelClose( buffer );
				break;

			case PING:
				message = decodePing( buffer );
				break;

			case PING_RESPONSE:
				message = decodePingResponse( buffer );
				break;

			default:
				assert false : "Unhandled type: " + message_type;
				message = null;
		}

		// Make sure all the expected data was consumed
		if ( buffer.position() < position_after_length + length ) {
			if ( DEBUG ) System.err.println( "WARNING: Unconsumed data from decode of " +
				message_type + ": " +
				( ( position_after_length + length ) - buffer.position() ) );
			buffer.position( position_after_length + length );
		}

		return message;
	}


	static InvokeIMessage decodeInvoke( IoBuffer buffer, IoSession session ) {
		// VERSION
		buffer.get();

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
//					args[ i ] = readObject( buffer, call_id );
					args[ i ] = IoBufferSerialization.getObject( buffer );
				}
			}
			catch( Exception ex ) {
				LOG.info( "Unable to de-serialize method argument", ex );
				// Write an error and return nothing
				session.write( new InvokeReturnIMessage( call_id,
					new ServerException( "Error de-serializing method argument", ex ),
					true, null, null ) );
				return null;
			}
		}

		// PERSISTENT NAME
		String persistent_name = null;
		if ( has_persistent_name ) {
			try {
				if ( DEBUG ) {
					System.out.println( "(In) Position before persistent name: " +
						buffer.position() );
				}

				persistent_name = buffer.getString( STRING_DECODER );

				if ( DEBUG ) {
					System.out.println( "  decoded persistent name: " + persistent_name );
					System.out.println( "(In) Position after persistent name: " +
						buffer.position() );
				}
			}
			catch ( CharacterCodingException ex ) {
				LOG.info( "Error decoding object persistent name", ex );
				// Write an error and return nothing
				session.write( new InvokeReturnIMessage( call_id,
					new ServerException( "Error decoding object persistent name", ex ),
					true, null, null ) );
				return null;
			}
		}

		// USER CONTEXT
		UserContextInfo user_context = null;
		if ( has_user_context ) {
			try {
				if ( DEBUG ) {
					System.out.println( "(In) Position before user context: " +
						buffer.position() );
					System.out.println( "Hex dump after context position: " +
						buffer.getHexDump() );
				}

				user_context =
					( UserContextInfo ) IoBufferSerialization.getObject( buffer );
			}
			catch( Exception ex ) {
				LOG.info( "Error de-serializing user context", ex );
				// Write an error and return nothing
				session.write( new InvokeReturnIMessage( call_id,
					new ServerException( "Error de-serializing user context", ex ),
					true, null, null ) );
				return null;
			}
		}

		return new InvokeIMessage( call_id, object_id, persistent_name, method_id, args,
			user_context, server_perf_stats_requested );
	}


	private static InvokeReturnIMessage decodeInvokeReturn( IoBuffer buffer ) {
		// VERSION
		buffer.get();

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
//				value = readObject( buffer, -call_id );
				value = IoBufferSerialization.getObject( buffer );
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


	private static InvokeInterruptIMessage decodeInvokeInterrupt( IoBuffer buffer ) {
		// VERSION
		buffer.get();

		// CALL ID
		int call_id = buffer.getInt();

		return new InvokeInterruptIMessage( call_id );
	}


	private static InvokeAckIMessage decodeInvokeAck( IoBuffer buffer ) {
		// VERSION
		buffer.get();

		// CALL ID
		int call_id = buffer.getInt();

		return new InvokeAckIMessage( call_id );
	}


	private static SessionInitIMessage decodeSessionInit( IoBuffer buffer,
		IoSession session ) {
		
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
			vmid = ( VMID ) IoBufferSerialization.getObject( buffer );

			// CONNECTION ARGS
			if ( buffer.get() > 0 ) {
				connection_args =
					( ConnectionArgs ) IoBufferSerialization.getObject( buffer );
			}
			else connection_args = null;
		}
		catch( Exception ex ) {
			LOG.info( "Error while decoding session init vmid/args", ex );

			// Write an error, close the session and return nothing
			session.write( new SessionCloseIMessage( new FormattedTextResourceKey(
				Resources.ERROR_DESERIALIZING_SESSION_INIT_INFO, ex.toString() ),
				false ) );
			CloseHandler.close( session );
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
			if ( buffer.get() == 0 ) reconnect_token = null;
			else {
				try {
					reconnect_token =
						( Serializable ) IoBufferSerialization.getObject( buffer );
				}
				catch( Exception ex ) {
					LOG.info( "Error while decoding session init reconnect token", ex );
					reconnect_token = null;
				}
			}
		}

		// REQUESTED ACK RATE
		byte ack_rate = -1;
		if ( version >= 3 ) ack_rate = buffer.get();

		return new SessionInitIMessage( vmid, server_port, connection_args,
			min_protocol_version, pref_protocol_version, reconnect_token, ack_rate );
	}


	private static SessionInitResponseIMessage decodeSessionInitResponse( IoBuffer buffer,
		IoSession session ) {

		// VERSION
		byte version = buffer.get();

		// PROTOCOL VERSION
		byte protocol_version = buffer.get();

		VMID vmid;
		try {
			// VMID
			vmid = ( VMID ) IoBufferSerialization.getObject( buffer );
		}
		catch( Exception ex ) {
			LOG.info( "Error while decoding session init response vmid", ex );

			// Write an error, close the session and return nothing
			session.write( new SessionCloseIMessage( new FormattedTextResourceKey(
				Resources.ERROR_DESERIALIZING_SESSION_INIT_INFO, ex.toString() ),
				false ) );
			CloseHandler.close( session );
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
			if ( buffer.get() == 0 ) reconnect_token = null;
			else {
				try {
					reconnect_token =
						( Serializable ) IoBufferSerialization.getObject( buffer );
				}
				catch( Exception ex ) {
					LOG.warn(
						"Error while decoding session init response reconnect " + "token",
						ex );
					reconnect_token = null;
				}
			}
		}

		// ACK RATE
		byte ack_rate;
		if ( version >= 3 ) ack_rate = buffer.get();
		else ack_rate = 0;      // not supported

		return new SessionInitResponseIMessage( vmid, server_port, protocol_version,
			reconnect_token, ack_rate );
	}


	private static SessionTokenChangeIMessage decodeSessionTokenChange( IoBuffer buffer ) {
		// VERSION
		buffer.get();


		// NEW RECONNECT TOKEN
		Serializable reconnect_token;
		try {
			if ( buffer.get() > 0 ) {
				reconnect_token =
					( Serializable ) IoBufferSerialization.getObject( buffer );
			}
			else reconnect_token = null;
		}
		catch( Exception ex ) {
			LOG.warn( "Error while decoding session token change reconnect " +
				"token", ex );
			reconnect_token = null;
		}

		return new SessionTokenChangeIMessage( reconnect_token );
	}


	private static SessionCloseIMessage decodeSessionClose( IoBuffer buffer ) {
		// VERSION
		buffer.get();

		// REASON
		ResourceKey<String> reason;
		try {
			if ( buffer.get() > 0 ) {
				//noinspection unchecked
				reason = ( ResourceKey<String> ) IoBufferSerialization.getObject( buffer );
			}
			else reason = null;
		}
		catch( Exception ex ) {
			// If we can't de-serialize the session close message, there's no point
			// notifying the other side (since they're closing the connection).
			return null;
		}

		// AUTH FAILURE
		boolean is_auth_failure = buffer.get() != 0;

		return new SessionCloseIMessage( reason, is_auth_failure );
	}


	private static LeaseIMessage decodeLease( IoBuffer buffer ) {
		// VERSION
		buffer.get();

		// OID's
		int[] oids = new int[ buffer.get() & 0xff ];
		for ( int i = 0; i < oids.length; i++ ) {
			oids[ i ] = buffer.getInt();
		}

		return new LeaseIMessage( oids );
	}

	private static LeaseReleaseIMessage decodeLeaseRelease( IoBuffer buffer ) {
		// VERSION
		buffer.get();

		// OID's
		int[] oids = new int[ buffer.get() & 0xff ];
		for ( int i = 0; i < oids.length; i++ ) {
			oids[ i ] = buffer.getInt();
		}

		return new LeaseReleaseIMessage( oids );
	}


	static ChannelInitIMessage decodeChannelInit( IoBuffer buffer,
		IoSession session ) {

		// VERSION
		buffer.get();

		// REQUEST ID
		int request_id = buffer.getInt();

		// ATTACHMENT
		Serializable attachment = null;
		if ( buffer.get() != 0 ) {
			try {
				attachment = ( Serializable ) IoBufferSerialization.getObject( buffer );
			}
			catch( Exception ex ) {
			LOG.info( "Error while decoding channel init attachment", ex );

				// Write an error, close the session and return nothing
				session.write( new ChannelInitResponseIMessage( request_id,
					new FormattedTextResourceKey(
					Resources.ERROR_DESERIALIZING_CHANNEL_ATTACHMENT, ex.toString() ) ) );
				CloseHandler.close( session );
				return null;
			}
		}

		// CHANNEL ID
		short channel_id = buffer.getShort();

		return new ChannelInitIMessage( request_id, attachment, channel_id );
	}

	static ChannelInitResponseIMessage decodeChannelInitResponse( IoBuffer buffer ) {
		// VERSION
		buffer.get();

		// REQUEST ID
		int request_id = buffer.getInt();

		// REJECTED
		boolean rejected = buffer.get() == 1;

		if ( rejected ) {
			// REJECT REASON
			ResourceKey<String> reject_reason;
			if ( buffer.get() == 0 ) reject_reason = null;
			else {
				try {
					//noinspection unchecked
					reject_reason =
						( ResourceKey<String> ) IoBufferSerialization.getObject( buffer );
				}
				catch( Exception ex ) {
					reject_reason = null;
				}
			}

			return new ChannelInitResponseIMessage( request_id, reject_reason );
		}
		else return new ChannelInitResponseIMessage( request_id );
	}

	private static ChannelDataIMessage decodeChannelData( IoBuffer buffer ) {
		// VERSION
		buffer.get();

		// CHANNEL ID
		short channel_id = buffer.getShort();

		// DATA
		int length = buffer.getInt();

		// TODO: caching?
		byte[] data = new byte[ length ];
		buffer.get( data );
		ByteBuffer data_buffer = ByteBuffer.wrap( data );

		return new ChannelDataIMessage( channel_id, data_buffer );
	}

	private static ChannelCloseIMessage decodeChannelClose( IoBuffer buffer ) {
		// VERSION
		buffer.get();

		// CHANNEL ID
		short channel_id = buffer.getShort();

		return new ChannelCloseIMessage( channel_id );
	}

	private static PingIMessage decodePing( IoBuffer buffer ) {
		// VERSION
		buffer.get();

		// SEQUENCE NUMBER
		short seq_number = buffer.getShort();

		return new PingIMessage( seq_number );
	}

	private static PingResponseIMessage decodePingResponse( IoBuffer buffer ) {
		// VERSION
		buffer.get();

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


	static int getDualShortLength( IoBuffer buffer ) {
		short s_length = buffer.getShort();
		if ( s_length < 0 ) {
			short low_short = buffer.getShort();
			return ( ( s_length & 0x7FFF ) << 16 ) | ( low_short & 0xFFFF );
		}
		else return s_length & 0xFFFF;
	}
}
