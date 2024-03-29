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

import com.starlight.intrepid.ObjectCodec;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.message.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;


/**
 *
 */
public final class MessageEncoder  {


	/**
	 * {@link SessionInitIMessage}-specific encoding, since the protocol version
	 * is unavailable at this point.
	 *
	 * @see #encode(IMessage, byte, DataSink)
	 */
	public static int encodeSessionInit( SessionInitIMessage message,
		DataSink data_sink, ObjectCodec codec ) throws Exception {

		return encode_internal( message.getType().getID(),
			sink -> encodeSessionInit0( message, sink, codec ), data_sink );
	}

	/**
	 * {@link SessionInitResponseIMessage}-specific encoding, since the protocol version
	 * is unavailable at this point.
	 *
	 * @see #encode(IMessage, byte, DataSink)
	 */
	public static int encodeSessionInitResponse( SessionInitResponseIMessage message,
		DataSink data_sink, ObjectCodec codec ) throws Exception {

		return encode_internal( message.getType().getID(),
			sink -> encodeSessionInitResponse0( message, sink, codec ), data_sink );
	}


	/**
	 * Encode the data necessary to write the message. The first piece of the encoding
	 * is the content length, which is a big tricky because it's not known prior to
	 * writing. To make this work in a generic way with driver implementation, the data
	 * is written to two buffers: the length and data. Once the encoding is done, the
	 * complete message is written by writing the length buffer, followed by the data
	 * buffer.
	 *
	 * @param data_sink       Buffer to which the majority of the message data will be
	 *                        written (could be large).
	 *
	 * @return	the number of bytes written
	 */
	public static int encode( IMessage message, byte proto_version,
		DataSink data_sink ) throws Exception {

		if ( proto_version < 4 ) {
			throw new IllegalArgumentException("Invalid protocol version: " + proto_version);
		}

		IMessageType type = message.getType();
		if ( type == IMessageType.SESSION_INIT ||
			type == IMessageType.SESSION_INIT_RESPONSE ) {

			throw new IllegalArgumentException( "Logic error: the general encode " +
				"method may not be used for SessionInit and SessionInitResponse" );
		}

		Encoder encoder_function;
		switch ( message.getType() ) {
			case SESSION_TOKEN_CHANGE:
				encoder_function = sink -> encodeSessionTokenChange(
					( SessionTokenChangeIMessage ) message, proto_version, sink );
				break;

			case SESSION_CLOSE:
				encoder_function = sink -> encodeSessionClose(
					( SessionCloseIMessage ) message, proto_version, sink );
				break;

			case INVOKE:
				encoder_function = sink -> encodeInvoke(
					( InvokeIMessage ) message, sink );
				break;

			case INVOKE_RETURN:
				encoder_function = sink -> encodeInvokeReturn(
					( InvokeReturnIMessage ) message, proto_version, sink );
				break;

			case INVOKE_INTERRUPT:
				encoder_function = sink -> encodeInvokeInterrupt(
					( InvokeInterruptIMessage ) message, proto_version, sink );
				break;

			case INVOKE_ACK:
				encoder_function = sink -> encodeInvokeAck(
					( InvokeAckIMessage ) message, proto_version, sink );
				break;

			case LEASE:
				encoder_function = sink -> encodeLease(
					( LeaseIMessage ) message, proto_version, sink );
				break;

			case LEASE_RELEASE:
				encoder_function = sink -> encodeLeaseRelease(
					( LeaseReleaseIMessage ) message, proto_version, sink );
				break;

			case CHANNEL_INIT:
				encoder_function = sink -> encodeChannelInit(
					( ChannelInitIMessage ) message, proto_version, sink );
				break;

			case CHANNEL_INIT_RESPONSE:
				encoder_function = sink -> encodeChannelInitResponse(
					( ChannelInitResponseIMessage ) message, proto_version, sink );
				break;

			case CHANNEL_DATA:
				encoder_function = sink -> encodeChannelData(
					( ChannelDataIMessage ) message, proto_version, sink );
				break;

			case CHANNEL_DATA_ACK:
				encoder_function = sink -> encodeChannelDataAck(
					( ChannelDataAckIMessage ) message, proto_version, sink );
				break;

			case CHANNEL_CLOSE:
				encoder_function = sink -> encodeChannelClose(
					( ChannelCloseIMessage ) message, proto_version, sink );
				break;

			case PING:
				encoder_function = sink -> encodePing(
					( PingIMessage ) message, proto_version, sink );
				break;

			case PING_RESPONSE:
				encoder_function = sink -> encodePingResponse(
					( PingResponseIMessage ) message, proto_version, sink );
				break;

			default:
				throw new UnsupportedOperationException( "Unknown message type: " +
					message );
		}
		
		return encode_internal( type.getID(), encoder_function, data_sink );
	}


	/**
	 * @return	the number of bytes written
	 */
	private static int encode_internal( byte type_id, Encoder encoder_function,
		DataSink data_sink ) throws Exception {


		final DataSink.Tracking tracking_sink = data_sink.trackWritten();
			
		// TYPE
		tracking_sink.put( type_id );

		encoder_function.encode( tracking_sink );

		return tracking_sink.bytesWritten();
	}


	// NOTE: protocol version is unknown!
	private static void encodeSessionInit0( SessionInitIMessage message,
		DataSink buffer, ObjectCodec codec ) throws IOException {

		// VERSION
		buffer.put( ( byte ) 4 );

		// MIN PROTOCOL VERSION
		buffer.put( message.getMinProtocolVersion() );

		// PREF PROTOCOL VERSION
		buffer.put( message.getPrefProtocolVersion() );

		// VMID
		writeVMID(message.getInitiatorVMID(), buffer);

		// CONNECTION ARGS
		putModernObject( message.getConnectionArgs(), buffer, codec );

		// SERVER PORT
		if ( message.getInitiatorServerPort() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			buffer.putInt( message.getInitiatorServerPort().intValue() );
		}

		// RECONNECT TOKEN
		putModernObject( message.getReconnectToken(), buffer, codec );

		// REQUESTED ACK RATE
		buffer.put( message.getRequestedAckRateSec() );
	}


	// NOTE: protocol version is unknown!
	private static void encodeSessionInitResponse0( SessionInitResponseIMessage message,
		DataSink buffer, ObjectCodec codec ) throws IOException {

		// VERSION
		buffer.put( ( byte ) 3 );       // NOTE: Version 4 should change VMID
										//       and RECONNECT TOKEN. See MessageDecoder.

		// PROTOCOL VERSION
		buffer.put( message.getProtocolVersion() );

		// VMID
		writeVMID(message.getResponderVMID(), buffer );

		// SERVER PORT
		if ( message.getResponderServerPort() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			buffer.putInt( message.getResponderServerPort().intValue() );
		}

		// RECONNECT TOKEN
		putModernObject( message.getReconnectToken(), buffer, codec );

		// ACK RATE
		buffer.put( message.getAckRateSec() );
	}


	private static void encodeInvoke( InvokeIMessage message,
		DataSink buffer ) throws Exception {

		// CALL ID
		buffer.putInt( message.getCallID() );

		// OBJECT ID
		buffer.putInt( message.getObjectID() );

		// METHOD ID
		buffer.putInt( message.getMethodID() );

		// FLAGS
		byte flags = 0;
		if ( message.getArgs() != null ) flags |= 0x01;
		// 0x02 is unused (as of version 4)
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
		writeString(message.getPersistentName(), buffer);

		// USER CONTEXT
		if ( message.getUserContext() != null ) {
			putObject( message.getUserContext(), buffer );
		}
	}


	private static void encodeInvokeReturn( InvokeReturnIMessage message,
		byte proto_version, DataSink buffer ) throws Exception {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 0 );
		}

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
		byte proto_version, DataSink buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 0 );
		}

		// CALL ID
		buffer.putInt( message.getCallID() );
	}


	private static void encodeInvokeAck( InvokeAckIMessage message, byte proto_version,
		DataSink buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 0 );
		}

		// CALL ID
		buffer.putInt( message.getCallID() );
	}


	private static void encodeSessionTokenChange(SessionTokenChangeIMessage message,
		byte proto_version, DataSink buffer ) throws IOException {

		assert proto_version >= 4;

		// NEW RECONNECT TOKEN
		if ( message.getNewReconnectToken() == null ) buffer.put( ( byte ) 0 );
		else {
			buffer.put( ( byte ) 1 );
			putObject( message.getNewReconnectToken(), buffer );
		}
	}


	private static void encodeSessionClose( SessionCloseIMessage message,
		byte proto_version, DataSink buffer ) throws IOException {

		assert proto_version >= 4;

		// PROTOCOL VERSION
		buffer.put( proto_version );

		// REASON
		String reason = message.getReason().orElse( null );
		writeString(reason, buffer);

		// AUTH FAILURE
		buffer.put( message.isAuthFailure() ? ( byte ) 1 : 0 );
	}


	private static void encodeLease( LeaseIMessage message, byte proto_version,
		DataSink buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 0 );
		}

		// OID's
		int[] oids = message.getOIDs();
		buffer.put( ( byte ) oids.length );
		for ( int oid : oids ) {
			buffer.putInt( oid );
		}
	}


	private static void encodeLeaseRelease( LeaseReleaseIMessage message,
		byte proto_version, DataSink buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 0 );
		}

		// OID's
		int[] oids = message.getOIDs();
		buffer.put( ( byte ) oids.length );
		for ( int oid : oids ) {
			buffer.putInt( oid );
		}
	}


	private static void encodeChannelInit( ChannelInitIMessage message,
		byte proto_version, DataSink buffer ) throws IOException {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 1 );
		}

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

		// RX WINDOW
		buffer.putInt( message.getRxWindow() );
	}

	private static void encodeChannelInitResponse( ChannelInitResponseIMessage message,
		byte proto_version, DataSink buffer ) throws IOException {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 1 );
		}

		// REQUEST ID
		buffer.putInt( message.getRequestID() );
		
		// REJECTED
		if ( !message.isSuccessful() ) {
			buffer.put( ( byte ) 1 );

			// REJECT REASON
			String reject_reason = message.getRejectReason().orElse( null );
			writeString(reject_reason, buffer);
		}
		else {
			buffer.put( ( byte ) 0 );

			// RX WINDOW
			buffer.putInt( message.getRxWindow() );
		}
	}

	private static void encodeChannelData( ChannelDataIMessage message,
		byte proto_version, DataSink buffer ) {

		if ( proto_version < 3 ) {
			// VERSION  - removed in proto 3
			buffer.put( ( byte ) 0 );
		}

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

		for( int i = 0; i < buffer_count; i++ ) {
			ByteBuffer nio_buffer = message.getBuffer( i );
			buffer.put( nio_buffer );
		}

		if ( proto_version >= 3 ) {
			// MESSAGE ID
			buffer.putShort( message.getMessageID() );
		}
	}

	private static void encodeChannelDataAck( ChannelDataAckIMessage message,
		byte proto_version, DataSink buffer ) {

		assert proto_version >= 3 :
			"Invalid proto version for sending acks: " + proto_version;

		// CHANNEL ID
		buffer.putShort( message.getChannelID() );

		// MESSAGE ID
		buffer.putShort( message.getMessageID() );

		// RX WINDOW SIZE
		int new_window_size = message.getNewRxWindow();
		if ( new_window_size < 0 ) {
			buffer.put( 0x80 );
		}
		else {
			buffer.putInt( new_window_size );
		}
	}

	private static void encodeChannelClose( ChannelCloseIMessage message,
		byte proto_version, DataSink buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 0 );
		}

		// CHANNEL ID
		buffer.putShort( message.getChannelID() );
	}

	private static void encodePing( PingIMessage message, byte proto_version,
		DataSink buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 0 );
		}

		// SEQUENCE NUMBER
		buffer.putShort( message.getSequenceNumber() );
	}

	private static void encodePingResponse( PingResponseIMessage message,
		byte proto_version, DataSink buffer ) {

		if ( proto_version < 3 ) {
			// VERSION      - removed in proto 3
			buffer.put( ( byte ) 0 );
		}

		// SEQUENCE NUMBER
		buffer.putShort( message.getSequenceNumber() );
	}

	private static void writeVMID(VMID vmid, DataSink buffer) throws IOException {
		UUID uuid = vmid.uuid();
		buffer.putLong(uuid.getLeastSignificantBits());
		buffer.putLong(uuid.getMostSignificantBits());
		writeString(vmid.hint(), buffer);
	}

	private static void writeString(String string, DataSink buffer) throws CharacterCodingException {
		if (string == null) buffer.putInt(0);
		else {
			byte[] data = string.getBytes(StandardCharsets.UTF_8);
			buffer.putInt(data.length);
			buffer.put(data, 0, data.length);
		}
	}


	private static void putModernObject(Object object, DataSink buffer, ObjectCodec codec) throws IOException {
		if (object == null) {
			buffer.putInt(0);
			return;
		}

		try (ByteArrayOutputStream bout = new ByteArrayOutputStream(8 * 1024) ) {
			codec.writeObject(object, bout);

			byte[] data = bout.toByteArray();
			buffer.putInt(data.length);
			buffer.put(data, 0, data.length);
		}
	}

	private static void putObject( Object object, DataSink buffer ) throws IOException {
		try (ObjectOutputStream out = new ObjectOutputStream(buffer.outputStream())) {
			out.writeUnshared(object);
		}
	}




	@FunctionalInterface
	private interface Encoder {
		void encode( DataSink data_sink ) throws Exception;
	}
}
