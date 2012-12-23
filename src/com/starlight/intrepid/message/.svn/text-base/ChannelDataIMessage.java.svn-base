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


/**
 *
 */
public class ChannelDataIMessage implements IMessage {
	private final short channel_id;
	private final ByteBuffer single_buffer;
	private final ByteBuffer[] multi_buffer;


	public ChannelDataIMessage( short channel_id, ByteBuffer buffer ) {
		this.channel_id = channel_id;
		this.single_buffer = buffer;
		this.multi_buffer = null;
	}

	public ChannelDataIMessage( short channel_id, ByteBuffer... buffers ) {
		this.channel_id = channel_id;
		this.single_buffer = null;
		this.multi_buffer = buffers;
	}


	@Override
	public IMessageType getType() {
		return IMessageType.CHANNEL_DATA;
	}


	public short getChannelID() {
		return channel_id;
	}


	public int getBufferCount() {
		if ( single_buffer != null ) return 1;
		else return multi_buffer.length;
	}


	public ByteBuffer getBuffer( int index ) {
		if ( single_buffer != null ) {
			if ( index == 0 ) return single_buffer;
			else throw new IndexOutOfBoundsException( "Invalid index: " + index );
		}
		else return multi_buffer[ index ];
	}
}
