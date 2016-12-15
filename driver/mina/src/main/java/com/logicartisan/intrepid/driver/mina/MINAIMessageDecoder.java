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

package com.logicartisan.intrepid.driver.mina;

import com.logicartisan.intrepid.VMID;
import com.logicartisan.intrepid.driver.DataSource;
import com.logicartisan.intrepid.driver.MessageDecoder;
import com.logicartisan.intrepid.driver.SessionCloseOption;
import com.logicartisan.intrepid.message.IMessage;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.EOFException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.util.function.IntConsumer;


/**
 *
 */
class MINAIMessageDecoder extends CumulativeProtocolDecoder {
	private static final Logger LOG =
		LoggerFactory.getLogger( MINAIMessageDecoder.class );


	private final VMID vmid;
	private final ThreadLocal<VMID> deserialization_context_vmid;

	MINAIMessageDecoder( VMID vmid, ThreadLocal<VMID> deserialization_context_vmid ) {
		this.vmid = vmid;
		this.deserialization_context_vmid = deserialization_context_vmid;
	}


	@Override
	protected boolean doDecode( IoSession session, IoBuffer in,
		ProtocolDecoderOutput out ) throws Exception {

		deserialization_context_vmid.set( vmid );
		try {
			int position_before = in.position();
			IMessage message = MessageDecoder.decode( new IoBufferWrapper( in ),
				( response, close_option ) -> {
					System.out.println( "Response: " + response );
					session.write( response );

					if ( close_option != null ) {
						long flush_time = 0;
						if ( close_option == SessionCloseOption.ATTEMPT_FLUSH ) {
							flush_time = 2000;
						}
						CloseHandler.close( session, flush_time );
					}
				} );
			if ( message == null ) {
				in.position( position_before );
				return false;
			}
			else {
				out.write( message );
				return true;
			}
		}
		catch( Exception ex ) {
			LOG.warn( "Error during decode", ex );
			throw ex;
		}
		finally {
			deserialization_context_vmid.remove();
		}
	}


	private class IoBufferWrapper implements DataSource {
		private final IoBuffer delegate;


		IoBufferWrapper( IoBuffer delegate ) {
			this.delegate = delegate;
		}



		@Override
		public @Nonnull String hex() {
			return delegate.getHexDump();
		}

		@Override
		public byte get() {
			return delegate.get();
		}

		@Override
		public short getShort() {
			return delegate.getShort();
		}

		@Override
		public int getInt() {
			return delegate.getInt();
		}

		@Override
		public long getLong() {
			return delegate.getLong();
		}

		@Override
		public void getFully( @Nonnull byte[] destination ) throws EOFException {
			try {
				delegate.get( destination );
			}
			catch( BufferUnderflowException ex ) {
				EOFException eof = new EOFException();
				eof.initCause( ex );
				throw eof;
			}
		}

		@Override
		public @Nonnull String getString( @Nonnull CharsetDecoder decoder,
			@Nonnull IntConsumer byte_count_consumer ) throws CharacterCodingException {

			int position_before = delegate.position();
			String value = delegate.getString( decoder );
			byte_count_consumer.accept( delegate.position() - position_before );
			return value;
		}

		@Override
		public @Nonnull InputStream inputStream() {
			return delegate.asInputStream();
		}

		@Override
		public boolean request( long byte_count ) {
			return delegate.remaining() >= byte_count;
		}
	}
}
