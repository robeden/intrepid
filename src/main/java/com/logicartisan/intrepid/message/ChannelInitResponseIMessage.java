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

import com.starlight.locale.ResourceKey;


/**
 *
 */
public class ChannelInitResponseIMessage implements IMessage {
	private final int request_id;
	private final boolean rejected;
	private final ResourceKey<String> reject_reason;


	/**
	 * Use this constructor to indicate a successful channel creation.
	 */
	public ChannelInitResponseIMessage( int request_id ) {
		this.request_id = request_id;
		this.rejected = false;
		this.reject_reason = null;
	}


	/**
	 * Use this constructor to indicate a failed channel creation.
	 */
	public ChannelInitResponseIMessage( int request_id,
		ResourceKey<String> reject_reason ) {

		this.request_id = request_id;
		this.rejected = true;
		this.reject_reason = reject_reason;
	}


	@Override
	public IMessageType getType() {
		return IMessageType.CHANNEL_INIT_RESPONSE;
	}


	public int getRequestID() {
		return request_id;
	}

	/**
	 * Indicates whether or not the channel creation was successful.
	 */
	public boolean isSuccessful() {
		return !rejected;
	}


	/**
	 * Returns the reason the channel could not be created, which might be null
	 * if no reason was provided by the server.
	 */
	public ResourceKey<String> getRejectReason() {
		return reject_reason;
	}


	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		ChannelInitResponseIMessage that = ( ChannelInitResponseIMessage ) o;

		if ( rejected != that.rejected ) return false;
		if ( request_id != that.request_id ) return false;
		if ( reject_reason != null ? !reject_reason.equals( that.reject_reason ) :
			that.reject_reason != null ) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = request_id;
		result = 31 * result + ( rejected ? 1 : 0 );
		result = 31 * result + ( reject_reason != null ? reject_reason.hashCode() : 0 );
		return result;
	}


	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "ChannelInitResponseIMessage" );
		sb.append( "{reject_reason=" ).append( reject_reason );
		sb.append( ", request_id=" ).append( request_id );
		sb.append( ", rejected=" ).append( rejected );
		sb.append( '}' );
		return sb.toString();
	}
}
