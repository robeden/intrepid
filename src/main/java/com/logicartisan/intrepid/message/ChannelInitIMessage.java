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

package com.logicartisan.intrepid.message;

import java.io.Serializable;


/**
 *
 */
public class ChannelInitIMessage implements IMessage {
	private final int request_id;
	private final Serializable attachment;
	private final short channel_id;

	public ChannelInitIMessage( int request_id, Serializable attachment,
		short channel_id ) {
		
		this.request_id = request_id;
		this.attachment = attachment;
		this.channel_id = channel_id;
	}


	@Override
	public IMessageType getType() {
		return IMessageType.CHANNEL_INIT;
	}


	public int getRequestID() {
		return request_id;
	}

	public Serializable getAttachment() {
		return attachment;
	}
	
	public short getChannelID() {
		return channel_id;
	}

	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		ChannelInitIMessage that = ( ChannelInitIMessage ) o;

		if ( channel_id != that.channel_id ) return false;
		if ( request_id != that.request_id ) return false;
		if ( attachment != null ? !attachment.equals( that.attachment ) :
			that.attachment != null ) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = request_id;
		result = 31 * result + ( attachment != null ? attachment.hashCode() : 0 );
		result = 31 * result + ( int ) channel_id;
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "ChannelInitIMessage" );
		sb.append( "{attachment=" ).append( attachment );
		sb.append( ", request_id=" ).append( request_id );
		sb.append( ", channel_id=" ).append( channel_id );
		sb.append( '}' );
		return sb.toString();
	}
}