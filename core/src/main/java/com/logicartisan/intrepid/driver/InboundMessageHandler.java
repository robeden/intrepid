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

package com.logicartisan.intrepid.driver;

import com.logicartisan.intrepid.auth.ConnectionArgs;
import com.logicartisan.intrepid.message.IMessage;


/**
 * Interface for handling received messages.
 */
public interface InboundMessageHandler {
	/**
	 * Validates that the received message is permissible given the session state.
	 */
	void validateReceivedMessage( SessionInfo session_info, IMessage message,
		boolean locally_initiated_session ) throws CloseSessionIndicator;


	/**
	 * Called when a message is received.
	 *
	 * @param session_info	Info for the session. Note that the VMID will not be available
	 * 						initially.
	 * @param message		The received message.
	 *
	 * @throws CloseSessionIndicator	To signal that the SPI should close the session
	 * 									and free associated resources.
	 */
	IMessage receivedMessage( SessionInfo session_info, IMessage message,
		boolean locally_initiated_session ) throws CloseSessionIndicator;


	/**
	 * Called when a session is closed from the remote side.
	 *
	 * @param session_info		Info for the session.
	 * @param opened_locally	True if the connection was initiated locally.
	 * @param closed_locally	True if the connection was closed locally.
	 * @param can_reconnect		True if the SPI (the caller) has enough information to
	 * 							reconnect the session. This does not necessarily mean
	 * 							the reconnection will succeed... just that it's possible.
	 *
	 * @return	True if the SPI should attempt to reconnect the session.
	 */
	boolean sessionClosed( SessionInfo session_info, boolean opened_locally,
		boolean closed_locally, boolean can_reconnect );


	/**
	 * Called when a session is opened. This should trigger session
	 * initialization.
	 *
	 * @param session_info		Info for the session.
	 * @param opened_locally	Indicates whether or not the session was opened locally.
	 *
	 * @throws CloseSessionIndicator	To signal that the SPI should close the session
	 * 									and free associated resources.
	 *
	 * @return If non-null, this message will be sent to the session.
	 */
	IMessage sessionOpened( SessionInfo session_info, boolean opened_locally,
		ConnectionArgs connection_args ) throws CloseSessionIndicator;
}