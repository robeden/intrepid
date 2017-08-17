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

package com.starlight.intrepid.driver;

import com.starlight.intrepid.message.SessionCloseIMessage;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Optional;


/**
 * Throwable to indicate that the SPI should close the session.
 * <p>
 * This class will be serializable due to class hierarchy, but does not support
 * serialization. If an attempt is made to serialize it, an exception will be thrown.
 */
@SuppressWarnings( "serial" )
public final class CloseSessionIndicator extends Throwable {
	// There is no serialVersionUID because this class does not support serialization



	private final SessionCloseIMessage reason_message;
	private final String server_reason_message;


	public CloseSessionIndicator( @Nullable String server_reason_message ) {
		this.server_reason_message = server_reason_message;
		this.reason_message = null;
	}

	/**
	 * @param reason_message	If non-null, this packet will be sent to the session
	 * 							before closing to describe why it's being closed.
	 */
	public CloseSessionIndicator( SessionCloseIMessage reason_message ) {
		this.reason_message = reason_message;
		this.server_reason_message = null;
	}


	public SessionCloseIMessage getReasonMessage() {
		return reason_message;
	}

	public Optional<String> getServerReasonMessage() {
		return Optional.ofNullable( server_reason_message );
	}


	// Prevent serialization (since the message isn't serializable and this is meant for
	// internal SPI stuff anyway.
	private void writeObject( ObjectOutputStream oos ) throws IOException {
		throw new IOException( "CloseSessionIndicators should never be serialized" );
	}
}
