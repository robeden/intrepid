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

import com.starlight.IOKit;
import com.starlight.ValidationKit;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


/**
 * A simple implementation of {@link UserContextInfo} that simply
 * tracks the user's name.
 */
public class SimpleUserContextInfo implements UserContextInfo, Externalizable {
	static final long serialVersionUID = -8424532623990656703L;

	private String user_name;


	/** FOR EXTERNALIZATION ONLY!!! */
	public SimpleUserContextInfo() {
		assert IOKit.isBeingDeserialized();
	}


	public SimpleUserContextInfo( String user_name ) {
		ValidationKit.checkNonnull( user_name, "user_name" );

		this.user_name = user_name;
	}


	@Override
	public String getUserName() {
		return user_name;
	}


	@Override
	public String toString() {
		return "SimpleUserContextInfo{user_name='" + user_name + "'}";
	}


	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		SimpleUserContextInfo that = ( SimpleUserContextInfo ) o;

		if ( user_name != null ? !user_name.equals( that.user_name ) :
			that.user_name != null ) return false;

		return true;
	}

	@Override
	public int hashCode() {
		return user_name != null ? user_name.hashCode() : 0;
	}

	
	@Override
	public void writeExternal( ObjectOutput out ) throws IOException {
		// VERSION
		out.writeByte( 0 );

		// USER NAME
		out.writeUTF( user_name );
	}

	@Override
	public void readExternal( ObjectInput in )
		throws IOException, ClassNotFoundException {

		// VERSION
		in.readByte();

		// USER NAME
		user_name = in.readUTF();
	}
}
