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

import com.starlight.intrepid.auth.UserContextInfo;

import java.net.InetAddress;


/**
 * Allows access to context information when inside a remote call.
 */
public class IntrepidContext {
	private static final ThreadLocal<VMID> calling_vmid = new ThreadLocal<VMID>();
	private static final ThreadLocal<InetAddress> calling_address =
		new ThreadLocal<InetAddress>();
	private static final ThreadLocal<UserContextInfo> calling_user =
		new ThreadLocal<UserContextInfo>();
	private static final ThreadLocal<Intrepid> active_instance =
		new ThreadLocal<Intrepid>();

	// Hidden constructor
	private IntrepidContext() {}


	/**
	 * Returns true if the current thread is currently inside an Intrepid call (local or
	 * remote).
	 */
	public static boolean isCall() {
		return calling_vmid.get() != null;
	}


	/**
	 * Returns the VMID which invoked the method which we're inside the context of.
	 *
	 * @see #isCall()
	 */
	public static VMID getCallingVMID() {
		return calling_vmid.get();
	}


	/**
	 * Returns the InetAddress of the host that invoked the method which we're inside
	 * the context of. This will be null if not applicable, either because we're not in
	 * a call, the call comes from the local VM, or the host information isn't available
	 * from the SPI.
	 *
	 * @see #isCall()
	 */
	public static InetAddress getCallingHost() {
		return calling_address.get();
	}


	/**
	 * Returns the UserContextInfo under which this call is operating, if any. Note that
	 * it is possible to be inside a call but not have a UserContextInfo.
	 */
	public static UserContextInfo getUserInfo() {
		return calling_user.get();
	}


	/**
	 * Returns the instance involved in the current call, or null if not in an Intrepid
	 * call.
	 */
	public static Intrepid getActiveInstance() {
		return active_instance.get();
	}


	static void setCallInfo( Intrepid instance, VMID source, InetAddress source_address,
		UserContextInfo user_context ) {

		active_instance.set( instance );
		calling_vmid.set( source );
		calling_address.set( source_address );
		calling_user.set( user_context );
	}

	static void clearCallInfo() {
		active_instance.remove();
		calling_vmid.remove();
		calling_address.remove();
		calling_user.remove();
	}
}
