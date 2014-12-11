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

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;


/**
 *
 */
class MethodMap {
	private static Map<Class,TIntObjectMap<Method>> method_map_cache =
		new ConcurrentHashMap<Class, TIntObjectMap<Method>>();


	static TIntObjectMap<Method> generateMethodMap( Class clazz ) {
		TIntObjectMap<Method> to_return = method_map_cache.get( clazz );
		if ( to_return != null ) return to_return;

		to_return = new TIntObjectHashMap<Method>();

		CRC32 crc = new CRC32();

		// NOTE: this does not include methods from extended interfaces (see below)
		Method[] methods = clazz.getDeclaredMethods();
		for( Method method : methods ) {
			crc.reset();

			byte[] bites = method.getName().getBytes();
			crc.update( bites );

			Class return_class = method.getReturnType();
			if ( return_class != null ) {
				bites = return_class.getName().getBytes();
				crc.update( bites );
			}

			for( Class arg_class : method.getParameterTypes() ) {
				bites = arg_class.getName().getBytes();
				crc.update( bites );
			}

			long l_value = crc.getValue();
			int value = ( int )( l_value ^ ( l_value >>> 32 ) );
			if ( value == 0 ) value = 1;		// don't allow 0

			Method collision = to_return.put( value, method );
			assert collision == null :
				"Collision (" + value + "): " + method + " - " + collision;
		}

		method_map_cache.put( clazz, to_return );

		// See if this interface implements other interfaces.
		Class[] ifcs = clazz.getInterfaces();
		if ( ifcs != null && ifcs.length != 0 ) {
			for( Class ifc : ifcs ) {
				TIntObjectMap<Method> ifc_methods = generateMethodMap( ifc );
				if ( !ifc_methods.isEmpty() ) to_return.putAll( ifc_methods );
			}
		}

		return to_return;
	}

	static TObjectIntMap<MethodIDTemplate> generateReverseMethodMap(
		TIntObjectMap<Method> map ) {

		TObjectIntMap<MethodIDTemplate> to_return =
			new TObjectIntHashMap<MethodIDTemplate>();
		int[] keys = map.keys();
		for( int key : keys ) {
			to_return.put( new MethodIDTemplate( map.get( key ) ), key );
		}
		return to_return;
	}
}
