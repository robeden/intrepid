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

package com.logicartisan.intrepid.driver;

import com.logicartisan.intrepid.message.*;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;


/**
 *
 */
public final class MessageEncoder  {
	private static final CharsetEncoder STRING_ENCODER =
		Charset.forName( "UTF-16" ).newEncoder();

	private static final int MAX_SINGLE_SHORT_LENGTH = 0x7FFF;
	private static final int DUAL_SHORT_FLAG = 0x8000;



	/**
	 * Encode the data necessary to write the message. The first piece of the encoding
	 * is the content length, which is a big tricky because it's not known prior to
	 * writing. To make this work in a generic way with driver implementation, the data
	 * is written to two buffers: the length and data. Once the encoding is done, the
	 * complete message is written by writing the length buffer, followed by the data
	 * buffer.
	 *
	 * @param length_sink     Sink to which the first part of the message will be
	 *                        written. No more than an int worth of data will be written.
	 * @param data_sink       Buffer to which the majority of the message data will be
	 *                        written (could be large).
	 */
	public static void encode( IMessage message, DataSink length_sink,
		DataSink data_sink ) throws Exception {

		final DataSink.Tracking tracking_sink = data_sink.trackWritten();
			
		// TYPE
		tracking_sink.put( message.getType().getID() );

		switch ( message.getType() ) {
			case SESSION_INIT:
				encodeSessionInit( ( SessionInitIMessage ) message, tracking_sink );
				break;

			case SESSION_INIT_RESPONSE:
				encodeSessionInitResponse( ( SessionInitResponseIMessage ) message,
					tracking_sink );
				break;

			case SESSION_TOKEN_CHANGE:
				encodeSesssionTokenChange( ( SessionTokenChangeIMessage ) message,
					tracking_sink );
				break;

			case SESSION_CLOSE:
				encodeSessionClose( ( SessionCloseIMessage ) message, tracking_sink );
				break;

			case INVOKE:
				encodeInvoke( ( InvokeIMessage ) message, tracking_sink );
				break;

			case INVOKE_RETURN:
				encodeInvokeReturn( ( InvokeReturnIMessage ) message, tracking_sink );
				break;

			case INVOKE_INTERRUPT:
				encodeInvokeInterrupt( ( InvokeInterruptIMessage ) message, tracking_sink );
				break;

			case INVOKE_ACK:
				encodeInvokeAck( ( InvokeAckIMessage ) message, tracking_sink );
				break;

			case LEASE:
				encodeLease( ( LeaseIMessage ) message, tracking_sink );
				break;

			case LEASE_RELEASE:
				encodeLeaseRelease( ( LeaseReleaseIMessage ) message, tracking_sink );
				break;

			case CHANNEL_INIT:
				encodeChannelInit( ( ChannelInitIMessage ) message, tracking_sink );
				break;

			case CHANNEL_INIT_RESPONSE:
				encodeChannelInitResponse(
					( ChannelInitResponseIMessage ) message, tracking_sink );
				break;

			case CHANNEL_DATA:
				encodeChannelData( ( ChannelDataIMessage ) message, tracking_sink );
				break;

			case CHANNEL_CLOSE:
				encodeChannelClose( ( ChannelCloseIMessage ) message, tracking_sink );
				break;

			case PING:
				encodePing( ( PingIMessage ) message, tracking_sink );
				break;

			case PING_RESPONSE:
				encodePingResponse( ( PingResponseIMessage ) message, tracking_sink );
				break;

			default:
				throw new UnsupportedOperationException( "Unknown message type: " +
					message );
		}

		putDualShortLength( length_sink, tracking_sink.bytesWritten() );
	}


	private static void encodeInvoke( InvokeIMessage message, DataSink buffer )
		throws Exception {

		// VERSION
		buffer.put( ( byte ) 0 );

		// CALL ID
		buffer.putInt( message.getCallID() );

		// OBJECT ID
		buffer.putInt( message.getObjectID() );

		// METHOD ID
		buffer.putInt( message.getMethodID() );

		// FLAGS
		byte flags = 0;
		if ( message.getArgs() != null ) flags |= 0x01;
		if ( message.getPersistentName() != null ) flags |= 0x02;
		if ( message.getUserContext() != null ) flags |= 0x04;
		if ( message.isServerPerfStatsRequested() ) flags |= 0x08;
		buffer.put( flags );

		// ARGS
		Object[] args = message.getArgs();
		if ( args != null ) {
			buffer.put( ( byte ) args.length );
			for ( Object arg : args ) {
				putObject( arg, buffer );
			}
		}

		// PERSISTENT NAME
		if ( message.getPersistentName() != null ) {
			buffer.putString( message.getPersistentName(), STRING_ENCODER, c -> {} );
		}

		// USER CONTEXT
		if ( message.getUserContext() != null ) {
			putObject( message.getUserContext(), buffer );
		}
	}


	private static void encodeInvokeReturn( InvokeReturnIMessage message,
		DataSink buffer ) throws Exception {

		// VERSION
		buffer.put( ( byte ) 0 );

		// CALL ID
		buffer.putInt( message.getCallID() );

		Object value = message.getValue();

		// FLAGS
		//   1 - has normal value (1 & 2 cannot both be set)
		//   2 - has thrown value (1 & 2 cannot both be set)
		//   4 - has new object ID
		//   8 - has server time
		byte flags = 0;

		if ( value != null ) {
			if ( message.isThrown() ) flags |= 0x02;
			else flags |= 0x01;
		}
		if ( message.getNewObjectID() != null ) flags |= 0x04;
		if ( message.getServerTimeNano() != null ) flags |= 0x08;

		buffer.put( flags );

		// VALUE
		if ( value != null ) {
			putObject( value, buffer );
//			writeObject( value, buffer, message.getCallID() );
		}

		// NEW OBJECT ID
		if ( message.getNewObjectID() != null ) {
			buffer.putInt( message.getNewObjectID().intValue() );
		}

		// SERVER TIME
		if ( message.getServerTimeNano() != null ) {
			buffer.putLong( message.getServerTimeNano().longValue() );
		}
	}


	private static void encodeInvokeInterrupt( InvokeInterruptIMessage message,
		DataSink buffer ) {

		// VERSION
		buffer.put( ( byte ) 0 );

		// CALL ID
		buffer.putInt( message.getCallID() );
	}


	private static void encodeInvokeAck( InvokeAckIMessage message, DataSink buffer ) {
		// VERSION
		buffer.put( ( byte ) 0 );

		// CALL ID
		buffer.putInt( message.getCallID() );
	}


	private static void encodeSessionInit( SessionInitIMessage message, DataSink buffer )
		throws IOException {

		// VERSION
		buffer.put( ( byte ) 3 );

		// MIN PROTOCOL VERSION
		buffer.put( message.getMinProtocolVersion() );

		// PREF PROTOCOL VERSION
		buffer.put( message.getPrefProtocolVersion() );

		// VMID
		putObject( message.getInitiatorVMID(), buffer );

		// CONNECTION ARGS
		if ( message.getConnectionArgs() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			putObject( message.getConnectionArgs(), buffer );
		}

		// SERVER PORT
		if ( message.getInitiatorServerPort() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			buffer.putInt( message.getInitiatorServerPort().intValue() );
		}

		// RECONNECT TOKEN
		if ( message.getReconnectToken() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			putObject( message.getReconnectToken(), buffer );
		}

		// REQUESTED ACK RATE
		buffer.put( message.getRequestedAckRateSec() );
	}


	private static void encodeSessionInitResponse( SessionInitResponseIMessage message,
		DataSink buffer ) throws IOException {

		// VERSION
		buffer.put( ( byte ) 3 );

		// PROTOCOL VERSION
		buffer.put( message.getProtocolVersion() );

		// VMID
		putObject( message.getResponderVMID(), buffer );

		// SERVER PORT
		if ( message.getResponderServerPort() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			buffer.putInt( message.getResponderServerPort().intValue() );
		}

		// RECONNECT TOKEN
		if ( message.getReconnectToken() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			putObject( message.getReconnectToken(), buffer );
		}

		// ACK RATE
		buffer.put( message.getAckRateSec() );
	}


	private static void encodeSesssionTokenChange( SessionTokenChangeIMessage message,
		DataSink buffer ) throws IOException {

		// VERSION
		buffer.put( ( byte ) 0 );

		// NEW RECONNECT TOKEN
		if ( message.getNewReconnectToken() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			putObject( message.getNewReconnectToken(), buffer );
		}
	}


	private static void encodeSessionClose( SessionCloseIMessage message, DataSink buffer )
		throws IOException {
		// VERSION
		buffer.put( ( byte ) 0 );

		// REASON
		if ( message.getReason() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			putObject( message.getReason(), buffer );
		}

		// AUTH FAILURE
		buffer.put( message.isAuthFailure() ? ( byte ) 1 : 0 );
	}


	private static void encodeLease( LeaseIMessage message, DataSink buffer ) {
		// VERSION
		buffer.put( ( byte ) 0 );

		// OID's
		int[] oids = message.getOIDs();
		buffer.put( ( byte ) oids.length );
		for ( int oid : oids ) {
			buffer.putInt( oid );
		}
	}


	private static void encodeLeaseRelease( LeaseReleaseIMessage message, DataSink buffer ) {
		// VERSION
		buffer.put( ( byte ) 0 );

		// OID's
		int[] oids = message.getOIDs();
		buffer.put( ( byte ) oids.length );
		for ( int oid : oids ) {
			buffer.putInt( oid );
		}
	}


	static void encodeChannelInit( ChannelInitIMessage message, DataSink buffer )
		throws IOException {
		
		// VERSION
		buffer.put( ( byte ) 0 );

		// REQUEST ID
		buffer.putInt( message.getRequestID() );

		// ATTACHMENT
		if ( message.getAttachment() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			putObject( message.getAttachment(), buffer );
		}
		
		// CHANNEL ID
		buffer.putShort( message.getChannelID() );
	}

	static void encodeChannelInitResponse( ChannelInitResponseIMessage message,
		DataSink buffer ) throws IOException {

		// VERSION
		buffer.put( ( byte ) 0 );

		// REQUEST ID
		buffer.putInt( message.getRequestID() );
		
		// REJECTED
		if ( !message.isSuccessful() ) {
			buffer.put( ( byte ) 1 );

			// REJECT REASON
			if ( message.getRejectReason() == null ) buffer.put( ( byte ) 0 );
			else {
				buffer.put( ( byte ) 1 );
				putObject( message.getRejectReason(), buffer );
			}
		}
		else {
			buffer.put( ( byte ) 0 );
		}
	}

	private static void encodeChannelData( ChannelDataIMessage message, DataSink buffer ) {
		// VERSION
		buffer.put( ( byte ) 0 );

		// CHANNEL ID
		buffer.putShort( message.getChannelID() );

		// DATA
		int length = 0;
		final int buffer_count = message.getBufferCount();
		for( int i = 0; i < buffer_count; i++ ) {
			length += message.getBuffer( i ).remaining();
		}
		buffer.putInt( length );

		buffer.prepareForData( length );

		byte[] copy_buffer = new byte[ 102400 ];
		for( int i = 0; i < buffer_count; i++ ) {
			ByteBuffer nio_buffer = message.getBuffer( i );

			while( nio_buffer.hasRemaining() ) {
				int to_read = Math.min( copy_buffer.length, nio_buffer.remaining() );
				nio_buffer.get( copy_buffer, 0, to_read );
				buffer.put( copy_buffer, 0, to_read );
			}
		}
	}

	private static void encodeChannelClose( ChannelCloseIMessage message, DataSink buffer ) {
		// VERSION
		buffer.put( ( byte ) 0 );

		// CHANNEL ID
		buffer.putShort( message.getChannelID() );
	}

	private static void encodePing( PingIMessage message, DataSink buffer ) {
		// VERSION
		buffer.put( ( byte ) 0 );

		// SEQUENCE NUMBER
		buffer.putShort( message.getSequenceNumber() );
	}

	private static void encodePingResponse( PingResponseIMessage message, DataSink buffer ) {
		// VERSION
		buffer.put( ( byte ) 0 );

		// SEQUENCE NUMBER
		buffer.putShort( message.getSequenceNumber() );
	}


//	private void writeObject( Object object, IoBuffer buffer, int call_id )
//		throws IOException {
//
//		if ( object == null ) {
//			if ( DEBUG ) System.out.println( ">>> WRITE: null" );
//			buffer.put( SerializationShortCut.NULL.getID() );
//			return;
//		}
//
//		if ( object instanceof Number ) {
//			if ( object instanceof Byte ) {
//				if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): byte" );
//				buffer.put( SerializationShortCut.BYTE.getID() );
//				buffer.put( ( ( Byte ) object ).byteValue() );
//				return;
//			}
//			else if ( object instanceof Short ) {
//				if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): short" );
//				buffer.put( SerializationShortCut.SHORT.getID() );
//				buffer.putShort( ( ( Short ) object ).shortValue() );
//				return;
//			}
//			else if ( object instanceof Integer ) {
//				if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): int" );
//				buffer.put( SerializationShortCut.INT.getID() );
//				buffer.putInt( ( ( Integer ) object ).byteValue() );
//				return;
//			}
//			else if ( object instanceof Long ) {
//				if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): long" );
//				buffer.put( SerializationShortCut.LONG.getID() );
//				buffer.putLong( ( ( Long ) object ).byteValue() );
//				return;
//			}
//			else if ( object instanceof Float ) {
//				if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): float" );
//				buffer.put( SerializationShortCut.FLOAT.getID() );
//				buffer.putFloat( ( ( Float ) object ).byteValue() );
//				return;
//			}
//			else if ( object instanceof Double ) {
//				if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): double" );
//				buffer.put( SerializationShortCut.DOUBLE.getID() );
//				buffer.putDouble( ( ( Double ) object ).byteValue() );
//				return;
//			}
//			// can fall through if BigInt, etc.
//		}
//		else if ( object instanceof Boolean ) {
//			if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): boolean" );
//			buffer.put( ( ( Boolean ) object ).booleanValue() ?
//				SerializationShortCut.BOOLEAN_TRUE.getID() :
//				SerializationShortCut.BOOLEAN_FALSE.getID() );
//			return;
//		}
//		else if ( object instanceof byte[] ) {
//			if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): byte[]" );
//			buffer.put( SerializationShortCut.BYTE_ARRAY.getID() );
//
//			byte[] data = ( byte[] ) object;
////			System.out.println( "Write byte array: " + data.length );
//			putDualShortLength( buffer, data.length, buffer.position() );
//			buffer.put( ( byte[] ) object );
//			return;
//		}
//		else if ( object instanceof String ) {
//			if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): string" );
//			buffer.put( SerializationShortCut.STRING.getID() );
//			try {
//				buffer.putString( ( String ) object, STRING_ENCODER );
//				buffer.put( ( byte ) 0x00 );           // NULL terminate string!!!
//			}
//			catch ( CharacterCodingException e ) {
//				// Shouldn't happen
//				throw new IntrepidRuntimeException( e );
//			}
//			return;
//		}
//
//		if ( DEBUG ) System.out.println( ">>> WRITE (" + call_id + "): object" );
//		buffer.put( SerializationShortCut.NONE.getID() );
//		IoBufferSerialization.putObject( object, buffer );
//	}


	/**
	 * @return		True if it's using a full int
	 */
	static boolean putDualShortLength( DataSink buffer, int length ) {
		assert length >= 0 : "Invalid length: " + length;

		if ( length > MAX_SINGLE_SHORT_LENGTH ) {
			buffer.putShort( ( short ) ( ( length >>> 16 ) | DUAL_SHORT_FLAG ) );
			buffer.putShort( ( short ) length );
			return true;
		}
		else {
			buffer.putShort( ( short ) length );
			return false;
		}
	}


	private static void putObject( Object object, DataSink buffer ) throws IOException {
		try ( ObjectOutputStream out = new ObjectOutputStream( buffer.outputStream() ) ) {
			out.writeUnshared( object );
		}
	}
}
