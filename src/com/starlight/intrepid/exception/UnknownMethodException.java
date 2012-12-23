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

package com.starlight.intrepid.exception;

import java.lang.reflect.Method;


/**
 *
 */
public class UnknownMethodException extends IntrepidRuntimeException {
	public UnknownMethodException( int method_id ) {
		super( createDescription( method_id, null ) );
	}

	public UnknownMethodException( int method_id, Method method ) {
		super( createDescription( method_id, method ) );
	}


	private static String createDescription( int id, Method method ) {
		StringBuilder buf = new StringBuilder( "Unknown method: " );
		buf.append( id );
		if ( method != null ) {
			buf.append( " [" );
			buf.append( method.getName() );
			buf.append( "(" );
			boolean first = true;
			for ( Class type : method.getParameterTypes() ) {
				if ( first ) first = false;
				else buf.append( ',' );

				buf.append( type.getSimpleName() );
			}
			buf.append( ")" );
			buf.append( "]" );
		}
		return buf.toString();
	}
}
