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

package com.logicartisan.intrepid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.logicartisan.intrepid.auth.ConnectionArgs;
import com.logicartisan.intrepid.auth.UserContextInfo;

import java.net.InetAddress;


/**
 * Interface allows monitoring of the status of sessions.
 */
public interface ConnectionListener {
	/**
	 * Called when a connection is opened (or re-opened).
	 *
	 * @param attachment                    Connection attachment, if any.
	 * @param source_vmid                   VMID of the instance from which the message
	 *                                      originated.
	 * @param vmid                          VMID of the peer for the connection.
	 * @param user_context                  User context, if any.
	 * @param previous_vmid                 If the connection has been re-opened and the
	 *                                      peer's VMID has changed, this will contain
	 *                                      the old VMID.
	 * @param connection_type_description   An SPI-specific description of the connection
	 * @param ack_rate_sec                  Rate of method acks in seconds.
	 */
	public void connectionOpened( @Nonnull InetAddress host, int port,
		@Nullable Object attachment, @Nonnull VMID source_vmid, @Nonnull VMID vmid,
		@Nullable UserContextInfo user_context, @Nullable VMID previous_vmid,
		@Nonnull Object connection_type_description, byte ack_rate_sec );

	/**
	 * Called when a connection is broken.
	 *
	 * @param source_vmid                   VMID of the instance from which the message
     *                                      originated.
	 * @param vmid                          VMID of the peer for the connection.
	 * @param attachment                    Connection attachment, if any.
	 * @param will_attempt_reconnect        Indicates whether or not a reconnection
	 * @param user_context                  User context, if any.
	 */
	public void connectionClosed( @Nonnull InetAddress host, int port,
		@Nonnull VMID source_vmid, @Nullable VMID vmid, @Nullable Object attachment,
		boolean will_attempt_reconnect, @Nullable UserContextInfo user_context );


	/**
	 * Called when a connection attempt is being made. This will be followed by either
	 * {@link #connectionOpened} or {@link #connectionOpenFailed}.
	 *
	 * @param connection_type_description   An SPI-specific description of the connection
	 */
	public void connectionOpening( @Nonnull InetAddress host,
		int port, @Nullable Object attachment, @Nullable ConnectionArgs args,
		@Nonnull Object connection_type_description );

	/**
	 * Called after {@link #connectionOpening} if the connection could not be made.
	 */
	public void connectionOpenFailed( @Nonnull InetAddress host,
		int port, @Nullable Object attachment, @Nullable Exception error,
		boolean will_retry );
}
