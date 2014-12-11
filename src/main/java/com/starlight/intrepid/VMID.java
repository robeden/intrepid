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

package com.starlight.intrepid;

import com.starlight.IOKit;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;


/**
 * 
 */
public final class VMID implements Externalizable {
	private long lsb;
	private long msb;
	private String hint_text;


	/** FOR EXTERNALIZATION ONLY!!! */
	public VMID() {
		assert IOKit.isBeingDeserialized();
	}


	VMID( UUID uuid, String hint_text ) {
		this.lsb = uuid.getLeastSignificantBits();
		this.msb = uuid.getMostSignificantBits();
		this.hint_text = hint_text;
	}


	// NOTE: hint_text is intentionally excluded
	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		VMID vmid = ( VMID ) o;

		if ( lsb != vmid.lsb ) return false;
		if ( msb != vmid.msb ) return false;

		return true;
	}

	// NOTE: hint_text is intentionally excluded
	@Override
	public int hashCode() {
		int result = ( int ) ( lsb ^ ( lsb >>> 32 ) );
		result = 31 * result + ( int ) ( msb ^ ( msb >>> 32 ) );
		return result;
	}


	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();

		if ( hint_text != null ) {
			buf.append( hint_text );
			buf.append( '(' );
		}

		buf.append( Long.toHexString( msb ) );
		buf.append( '-' );
		buf.append( Long.toHexString( lsb ) );

		if ( hint_text != null ) {
			buf.append( ')' );
		}

		return buf.toString();
	}


	@Override
	public void readExternal( ObjectInput in )
		throws IOException, ClassNotFoundException {

		// VERSION
		in.readByte();

		// MOST SIGNIFICANT BITS
		msb = in.readLong();

		// LEAST SIGNIFICANT BITS
		lsb = in.readLong();

		// HINT TEXT
		hint_text = IOKit.readString( in );
	}

	@Override
	public void writeExternal( ObjectOutput out ) throws IOException {
		// VERSION
		out.writeByte( 0 );

		// MOST SIGNIFICANT BITS
		out.writeLong( msb );

		// LEAST SIGNIFICANT BITS
		out.writeLong( lsb );

		// HINT TEXT
		IOKit.writeString( out, hint_text );
	}
}
