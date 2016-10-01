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

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;


/**
 *
 */
public class MethodMapTestApp {
	public static void main( String[] args ) throws Exception {
		System.setProperty( "java.awt.headless", "true" );

		File jar_file = new File( args[ 0 ] );

		JarInputStream in = new JarInputStream( new FileInputStream( jar_file ) );


		TLongObjectMap<Method> map = new TLongObjectHashMap<Method>();

		int count = 0;
		JarEntry entry;
		while( ( entry = in.getNextJarEntry() ) != null ) {
			if ( !entry.getName().endsWith( ".class" ) ) continue;
			if ( entry.getName().startsWith( "sun" ) ) continue;

			String class_name = entry.getName().substring(
				0, entry.getName().length() - ".class".length() ).replace( '/', '.' );

			try {
				Class clazz = Class.forName( class_name );
				MethodMap.generateMethodMap( clazz );
				count++;
			}
			catch( Throwable t ) {
				System.out.println( "Skipping: " + class_name + "  --  " + t.toString() );
			}
		}

		System.out.println( "Checked " + count + " classes." );

		in.close();
	}
}
