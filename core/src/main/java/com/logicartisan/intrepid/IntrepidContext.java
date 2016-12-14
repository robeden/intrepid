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

import com.logicartisan.intrepid.auth.UserContextInfo;

import java.net.InetAddress;


/**
 * Allows access to context information when inside a remote call.
 */
public class IntrepidContext {
	private static final ThreadLocal<CallInfo> CALL_INFO = new ThreadLocal<>();


	// Hidden constructor
	private IntrepidContext() {}


	/**
	 * Returns true if the current thread is currently inside an Intrepid call (local or
	 * remote).
	 */
	public static boolean isCall() {
		return CALL_INFO.get() != null;
	}


	/**
	 * Returns the VMID which invoked the method which we're inside the context of.
	 *
	 * @see #isCall()
	 */
	public static VMID getCallingVMID() {
		CallInfo info = CALL_INFO.get();
		return info == null
			? null
			: info.source;
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
		CallInfo info = CALL_INFO.get();
		return info == null
			? null
			: info.source_address;
	}


	/**
	 * Returns the UserContextInfo under which this call is operating, if any. Note that
	 * it is possible to be inside a call but not have a UserContextInfo.
	 */
	public static UserContextInfo getUserInfo() {
		CallInfo info = CALL_INFO.get();
		return info == null
			? null
			: info.user_context;
	}


	/**
	 * Returns the instance involved in the current call, or null if not in an Intrepid
	 * call.
	 */
	public static Intrepid getActiveInstance() {
		CallInfo info = CALL_INFO.get();
		return info == null
			? null
			: info.instance;
	}


	static void setCallInfo( Intrepid instance, VMID source, InetAddress source_address,
		UserContextInfo user_context ) {

		CALL_INFO.set( new CallInfo( instance, source, source_address, user_context ) );
	}

	static void clearCallInfo() {
		CALL_INFO.remove();
	}


	private static class CallInfo {
		final Intrepid instance;
		final VMID source;
		final InetAddress source_address;
		final UserContextInfo user_context;

		CallInfo( Intrepid instance, VMID source, InetAddress source_address,
			UserContextInfo user_context ) {

			this.instance = instance;
			this.source = source;
			this.source_address = source_address;
			this.user_context = user_context;
		}
	}
}
