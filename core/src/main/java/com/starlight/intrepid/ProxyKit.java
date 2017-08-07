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

/**
 *
 */
class ProxyKit {
	/**
	 * Indicates whether or not the given object is a proxy.
	 */
	static boolean isProxy( Object object ) {
		return object != null && object instanceof Proxy;
	}


	/**
	 * Indicates whether or not the given object is a local proxy.
	 *
	 * @see #isProxy(Object)
	 */
	static boolean isProxyLocal( Object object, VMID local_vmid ) {
		if ( !( object instanceof Proxy ) ) return false;

		VMID vmid = ( ( Proxy ) object ).__intrepid__getHostVMID();
		return vmid.equals( local_vmid );
	}


	/**
	 * If the given object is a local proxy, this returns the object it delegates calls
	 * to. If it is not a proxy or the proxy is not local, it returns null.
	 *
	 * @see #isProxy(Object)
	 * @see #isProxyLocal(Object,VMID)
	 */
	static Object getLocalProxyDelegate( Object object, VMID local_vmid ) {
		if ( !isProxyLocal( object, local_vmid ) ) return null;

		return ( ( Proxy ) object ).__intrepid__getLocalDelegate();
	}


	/**
	 * Returns the VMID the given proxy object points to. If the object is not a proxy or
	 * is local, it returns null.
	 *
	 * @see #isProxy(Object)
	 * @see #isProxyLocal(Object,VMID)
	 */
	static VMID getRemoteProxyVMID( Object object, VMID local_vmid ) {
		if ( isProxyLocal( object, local_vmid ) ) return null;

		return ( ( Proxy ) object ).__intrepid__getHostVMID();
	}


	/**
	 * Returns the VMID the given proxy object points to. If the object is not a proxy,
	 * null is returned.
	 *
	 * @see #isProxy(Object)
	 */
	static VMID getProxyVMID( Object object ) {
		if ( !isProxy( object ) ) return null;

		return ( ( Proxy ) object ).__intrepid__getHostVMID();
	}
}
