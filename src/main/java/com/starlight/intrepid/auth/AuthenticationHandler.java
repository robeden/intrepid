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

package com.starlight.intrepid.auth;

import java.net.SocketAddress;


/**
 * Interface to allow customizable handling of connection authentication/authorization
 * checking.
 */
public interface AuthenticationHandler {
	/**
	 * Determine whether a connection should be allowed or not.
	 *
	 * @param connection_args		Connection arguments, if any. See
	 * 					{@link UserCredentialsConnectionArgs}.
	 * @param remote_address        Address of the remote peer.
	 * @param session_source        The SPI-dependent "source" for the session. In the
	 *                              normal case, this would be a SocketChannel or Socket.
	 *                              This is optional and my return null if not supported.
	 *
	 * @return		An object containing context info about the user/service connecting.
	 * 				This information will be available while operating inside a call
	 * 				via the {@link com.starlight.intrepid.IntrepidContext} class. It is
	 * 				valid to return null, in which case no user information will be
	 * 				available.
	 * @throws ConnectionAuthFailureException		Thrown if the connection should be
	 * 												rejected.
	 */
	public UserContextInfo checkConnection( ConnectionArgs connection_args,
		SocketAddress remote_address, Object session_source )
		throws ConnectionAuthFailureException;
}
