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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.util.Arrays;


/**
 *
 */
class MethodIDTemplate implements Externalizable {
	private String name;
	private String[] param_type_names;

	public MethodIDTemplate() {}

	MethodIDTemplate( Method method ) {
		set( method );
	}


	void set( Method method ) {
		this.name = method.getName();

		Class[] param_types = method.getParameterTypes();
		if ( param_types == null || param_types.length == 0 ) param_type_names = null;
		else {
			param_type_names = new String[ param_types.length ];
			for( int i = 0; i < param_type_names.length; i++ ) {
				param_type_names[ i ] = param_types[ i ].getName();
			}
		}
	}


	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append( name );
		buf.append( "(" );
		if ( param_type_names != null ) {
			boolean first = true;
			for( String param : param_type_names ) {
				if ( first ) first = false;
				else buf.append( "," );

				buf.append( param );
			}
		}
		return buf.append( ")" ).toString();
	}


	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		MethodIDTemplate that = ( MethodIDTemplate ) o;

		if ( name != null ? !name.equals( that.name ) : that.name != null ) return false;
		if ( !Arrays.equals( param_type_names, that.param_type_names ) ) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int result = name != null ? name.hashCode() : 0;
		result = 31 * result +
			( param_type_names != null ? Arrays.hashCode( param_type_names ) : 0 );
		return result;
	}


	@Override
	public void readExternal( ObjectInput in )
		throws IOException, ClassNotFoundException {

		// VERSION
		in.readByte();

		// NAME
		name = in.readUTF();

		// PARAMS
		short length = in.readShort();
		if ( length == -1 ) param_type_names = null;
		else {
			param_type_names = new String[ length ];
			for( int i = 0; i < param_type_names.length; i++ ) {
				param_type_names[ i ] = in.readUTF();
			}
		}
	}

	@Override
	public void writeExternal( ObjectOutput out ) throws IOException {
		// VERSION
		out.writeByte( 0 );

		// NAME
		out.writeUTF( name );

		// PARAMS
		if ( param_type_names == null ) out.writeShort( -1 );
		else {
			out.writeShort( ( short ) param_type_names.length );
			for( String param : param_type_names ) {
				out.writeUTF( param );
			}
		}
	}
}
