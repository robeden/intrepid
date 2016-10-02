package com.logicartisan.intrepid;

import com.logicartisan.intrepid.exception.ChannelRejectedException;

import java.io.Serializable;
import java.nio.channels.ByteChannel;


/**
 * This interface implements the server-side of channel (stream) capabilities. The
 * acceptor is called on the server when a client initiates a connection.
 */
public interface ChannelAcceptor {
	/**
	 * Called when a new inbound channel is received. This should return quickly and
	 * should not do any I/O work with the channel in the same thread.
	 */
	public void newChannel( ByteChannel channel, VMID source_vmid,
		Serializable attachment ) throws ChannelRejectedException;
}