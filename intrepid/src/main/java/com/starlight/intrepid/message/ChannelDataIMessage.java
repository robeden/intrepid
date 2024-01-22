// Copyright (c) 2011 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
// * Neither the name of Intrepid nor the
// names of its contributors may be used to endorse or promote products
// derived from this software without specific prior written permission.
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

package com.starlight.intrepid.message;

import java.nio.ByteBuffer;
import java.util.Arrays;


public abstract class ChannelDataIMessage implements IMessage {

	public static ChannelDataIMessage create( short channel_id, short message_id,
		ByteBuffer buffer ) {

		return new SingleBufferChannelDataIMessage( channel_id, message_id, buffer );
	}

	public static ChannelDataIMessage create( short channel_id, short message_id,
		ByteBuffer... buffers ) {

		return new MultiBufferChannelDataIMessage( channel_id, message_id, buffers );
	}


	private final short channel_id;
	private final short message_id;

	private ChannelDataIMessage( short channel_id, short message_id ) {
		this.channel_id = channel_id;
		this.message_id = message_id;
	}


	@Override
	public IMessageType getType() {
		return IMessageType.CHANNEL_DATA;
	}


	public short getChannelID() {
		return channel_id;
	}

	public short getMessageID() {
		return message_id;
	}


	public abstract int getBufferCount();
	public abstract ByteBuffer getBuffer( int index );



	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		ChannelDataIMessage that = ( ChannelDataIMessage ) o;

		if ( channel_id != that.channel_id ) return false;
		return message_id == that.message_id;
	}

	@Override
	public int hashCode() {
		int result = ( int ) channel_id;
		result = 31 * result + ( int ) message_id;
		return result;
	}



	private static class SingleBufferChannelDataIMessage extends ChannelDataIMessage {
		private final ByteBuffer buffer;


		SingleBufferChannelDataIMessage( short channel_id, short message_id,
			ByteBuffer buffer ) {

			super( channel_id, message_id );

			this.buffer = buffer;
		}



		@Override
		public int getBufferCount() {
			return 1;
		}

		@Override
		public ByteBuffer getBuffer( int index ) {
			if ( index != 0 ) {
				throw new IllegalArgumentException( "Invalid index: " + index );
			}
			return buffer;
		}

		@Override
		public String toString() {
			return "ChannelDataIMessage{" +
				"channel_id=" + getChannelID() +
				", message_id=" + getMessageID() +
				", buffer=" + buffer +
				'}';
		}
	}

	private static class MultiBufferChannelDataIMessage extends ChannelDataIMessage {
		private final ByteBuffer[] buffers;


		MultiBufferChannelDataIMessage( short channel_id, short message_id,
			ByteBuffer... buffers ) {

			super( channel_id, message_id );

			this.buffers = buffers;
		}

		@Override
		public int getBufferCount() {
			return buffers.length;
		}

		@Override
		public ByteBuffer getBuffer( int index ) {
			return buffers[ index ];
		}


		@Override
		public String toString() {
			return "ChannelDataIMessage{" +
				"channel_id=" + getChannelID() +
				", message_id=" + getMessageID() +
				", buffers=" + Arrays.toString( buffers ) +
				'}';
		}
	}
}
