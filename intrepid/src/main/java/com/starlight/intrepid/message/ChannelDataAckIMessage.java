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

/**
 * Sent periodically in response to {@link ChannelDataIMessage} messages to acknowledge
 * receipt in order to manage the flow window for a channel. The message contains
 * a {@link #getMessageID() message ID} which indicates which message and all messages
 * preceding it are processed by the receiver.
 *
 * @see <a href="https://bitbucket.org/robeden/intrepid/issues/15">Issue #15 (originating feature)</a>
 *
 * @since Protocol v. 3
 */
public class ChannelDataAckIMessage implements IMessage {
	private final short channel_id;
	private final short message_id;

	private final int new_rx_window;


	public ChannelDataAckIMessage( short channel_id, short message_id,
		int new_rx_window ) {

		this.channel_id = channel_id;
		this.message_id = message_id;
		this.new_rx_window = new_rx_window;
	}


	@Override
	public IMessageType getType() {
		return IMessageType.CHANNEL_DATA_ACK;
	}


	public short getChannelID() {
		return channel_id;
	}

	public short getMessageID() {
		return message_id;
	}

	/**
	 * The new receive window size (might be the same as the old receive window size).
	 * A value less than zero indicates the last specified value should be used.
	 *
	 * @see ChannelInitIMessage#getRxWindow()
	 * @see ChannelInitResponseIMessage#getRxWindow()
	 */
	public int getNewRxWindow() {
		return new_rx_window;
	}



	@Override
	public String toString() {
		return "ChannelDataAckIMessage{" +
			"channel_id=" + channel_id +
			", message_id=" + message_id +
			", new_rx_window=" + new_rx_window +
			'}';
	}



	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		ChannelDataAckIMessage that = ( ChannelDataAckIMessage ) o;

		if ( channel_id != that.channel_id ) return false;
		if ( message_id != that.message_id ) return false;
		return new_rx_window == that.new_rx_window;
	}

	@Override
	public int hashCode() {
		int result = ( int ) channel_id;
		result = 31 * result + ( int ) message_id;
		result = 31 * result + new_rx_window;
		return result;
	}
}
