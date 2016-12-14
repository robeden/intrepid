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

package com.logicartisan.intrepid.message;

/**
 *
 */
public enum IMessageType {
	// WARNING: ID is just a byte
	SESSION_INIT( 			1 ),
	SESSION_INIT_RESPONSE( 	2 ),

	SESSION_TOKEN_CHANGE(   5 ),    // Added in protocol version 1

	SESSION_CLOSE(			9 ),

	INVOKE(					10 ),
	INVOKE_RETURN(			11 ),
	INVOKE_INTERRUPT(		12 ),
	INVOKE_ACK(             13 ),   // Added in protocol version 2

	LEASE(                  20 ),
	LEASE_RELEASE(          21 ),

	CHANNEL_INIT(           30 ),
	CHANNEL_INIT_RESPONSE(  31 ),
	CHANNEL_DATA(           32 ),
	CHANNEL_CLOSE(          33 ),

	PING(                   40 ),
	PING_RESPONSE(          41 );



	private final byte id;

	private IMessageType( int id ) {
		if ( id < 1 || id > Byte.MAX_VALUE ) {
			throw new IllegalArgumentException( "Invalid ID: " + id );
		}

		this.id = ( byte ) id;
	}


	public byte getID() {
		return id;
	}


	public static IMessageType findByID( byte id ) {
		for( IMessageType type : values() ) {
			if ( type.id == id ) return type;
		}

		return null;
	}
}
