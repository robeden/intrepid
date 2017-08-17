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

package com.starlight.intrepid.driver.mina;

import com.starlight.intrepid.driver.DataSink;
import com.starlight.intrepid.driver.MessageEncoder;
import com.starlight.intrepid.driver.SessionInfo;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.IMessageType;
import com.starlight.intrepid.message.SessionInitIMessage;
import com.starlight.intrepid.message.SessionInitResponseIMessage;
import org.apache.mina.core.buffer.CachedBufferAllocator;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.buffer.IoBufferAllocator;
import org.apache.mina.core.buffer.SimpleBufferAllocator;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.util.function.IntConsumer;

import static com.starlight.intrepid.driver.mina.MINAIntrepidDriver.SESSION_INFO_KEY;


/**
 *
 */
class MINAIMessageEncoder implements ProtocolEncoder {
	private static final Logger LOG =
		LoggerFactory.getLogger( MINAIMessageEncoder.class );


	private static final IoBufferAllocator ALLOCATOR =
		System.getProperty( "intrepid.mina.encoder.allocate_cached" ) != null ?
		new CachedBufferAllocator() : new SimpleBufferAllocator();

	private static final int ALLOCATE_SIZE =
		Integer.getInteger( "intrepid.mina.encoder.allocate_size", 2_000 ).intValue();

	private static final boolean ALLOCATE_DIRECT =
		System.getProperty( "intrepid.mina.encoder.allocate_direct" ) != null;

	static {
		if ( ALLOCATOR instanceof CachedBufferAllocator ||
			ALLOCATE_SIZE != 2000 ||
			ALLOCATE_DIRECT ) {

			LOG.info( "Non-standard encoder allocation settings specified in " +
				"system properties:\n   Cached: {}\n   Size: {}\n   Direct: {}",
				ALLOCATOR instanceof CachedBufferAllocator,
				ALLOCATE_SIZE, ALLOCATE_DIRECT );
		}
	}



	@Override
	public void encode( IoSession session, Object message_obj, ProtocolEncoderOutput out )
		throws Exception {

		IMessage message = ( IMessage ) message_obj;

		IoBuffer buffer = ALLOCATOR.allocate( ALLOCATE_SIZE, ALLOCATE_DIRECT );
		buffer.setAutoExpand( true );


		// WARNING: Do not use slice() on the main buffer because that prevents
		//          auto-expansion.
		IoBuffer length_slice = IoBuffer.allocate( 4 );

		buffer.position( 4 );       // leave space for length

		DataSink length_slice_wrapper = new IoBufferWrapper( length_slice );
		DataSink buffer_wrapper = new IoBufferWrapper( buffer );
		if ( message.getType() == IMessageType.SESSION_INIT ) {
			MessageEncoder.encodeSessionInit( ( SessionInitIMessage ) message,
				length_slice_wrapper, buffer_wrapper );
		}
		else if ( message.getType() == IMessageType.SESSION_INIT_RESPONSE ) {
			MessageEncoder.encodeSessionInitResponse(
				( SessionInitResponseIMessage ) message, length_slice_wrapper,
				buffer_wrapper );
		}
		else {
			SessionInfo session_info =
				( SessionInfo ) session.getAttribute( SESSION_INFO_KEY );
			if ( session_info == null ) {
				// TODO: seeing this in unit tests
				assert false : "Unable to send " + message.getType() +
					" message since session info is unavailable";
				throw new IllegalStateException(
					"Unable to send message (session info unavailable)" );
			}
			final Byte protocol_version = session_info.getProtocolVersion();

			MessageEncoder.encode( message, protocol_version,
				length_slice_wrapper, buffer_wrapper );
		}

		prependLength( buffer, length_slice );

		out.write( buffer );
	}



	@Override
	public void dispose( IoSession session ) throws Exception {}


	static void prependLength( IoBuffer data_buffer, IoBuffer length_buffer ) {
		int data_position = data_buffer.position();

		length_buffer.flip();
		int start_position = 4 - length_buffer.remaining();
		data_buffer.position( start_position );
		data_buffer.put( length_buffer );
		data_buffer.position( data_position );

		data_buffer.flip();
		data_buffer.position( start_position );
	}


	private class IoBufferWrapper implements DataSink {
		private final IoBuffer delegate;

		IoBufferWrapper( IoBuffer delegate ) {
			this.delegate = delegate;
		}

		@Override
		public void put( int value ) {
			delegate.put( ( byte ) value );
		}

		@Override
		public void putShort( short value ) {
			delegate.putShort( value );
		}

		@Override
		public void putInt( int value ) {
			delegate.putInt( value );
		}

		@Override
		public void putLong( long value ) {
			delegate.putLong( value );
		}

		@Override
		public void put( byte[] b, int offset, int length ) {
			delegate.put( b, offset, length );
		}

		@Override
		public void putString( @Nonnull String value,
			@Nonnull CharsetEncoder encoder, @Nonnull IntConsumer byte_count_consumer )
			throws CharacterCodingException {

			int position_before = delegate.position();
			delegate.putString( value, encoder );

			// NULL terminate string!!
			// NOTE: Since UTF-16 is 2 bytes, need to insert 2 bytes properly terminate
            boolean utf16 = encoder.charset().name().startsWith( "UTF-16" );
            if ( utf16 ) {
	            delegate.putShort( ( short ) 0x00 );
            }
            else {
            	delegate.put( ( byte ) 0 );
            }

			byte_count_consumer.accept( delegate.position() - position_before );
		}

		@Override
		public void prepareForData( int length ) {
			delegate.expand( length );
		}

		@Override
		public @Nonnull OutputStream outputStream() {
			return new OutputStream() {
				@Override
				public void write( int b ) throws IOException {
					delegate.put( ( byte ) b );
				}

				@Override
				public void write( @Nonnull byte[] b ) throws IOException {
					delegate.put( b );
				}

				@Override
				public void write( @Nonnull byte[] b, int off, int len )
					throws IOException {

					delegate.put( b, off, len );
				}
			};
		}



		@Override
		public String toString() {
			return delegate.toString();
		}
	}
}
