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

package com.starlight.intrepid.message;

import com.starlight.intrepid.auth.UserContextInfo;

import java.util.Arrays;


/**
 *
 */
public class InvokeIMessage implements IMessage {
	private final int call_id;
	private final int method_id;
	private final Object[] args;

	private final int object_id;
	private final String persistent_name;

	private final UserContextInfo user_context;

	private final boolean server_perf_stats_requested;


	/**
	 *
	 * @param call_id			ID of this call.
	 * @param object_id			ID of the object being referenced.
	 * @param persistent_name	Persistent name of the object being referenced, if
	 * 							available/applicable.
	 * @param method_id			ID of the method being called.
	 * @param args				Call arguments.
	 * @param user_context		If non-null, this will contain information about the
	 * 							user under which the method is being called. This will be
	 * 							non-null when nested calls are made and this information
	 * 							should override the information from the session.
	 * @param server_perf_stats_requested	If true, server-side performance information
	 * 							is requested for this method.
	 */
	public InvokeIMessage( int call_id, int object_id, String persistent_name,
		int method_id, Object[] args, UserContextInfo user_context,
		boolean server_perf_stats_requested ) {

		this.call_id = call_id;
		this.object_id = object_id;
		this.persistent_name = persistent_name;
		this.method_id = method_id;
		this.args = args;
		this.user_context = user_context;
		this.server_perf_stats_requested = server_perf_stats_requested;
	}


	@Override
	public IMessageType getType() {
		return IMessageType.INVOKE;
	}

	public int getCallID() {
		return call_id;
	}

	public Object[] getArgs() {
		return args;
	}

	public int getMethodID() {
		return method_id;
	}

	public int getObjectID() {
		return object_id;
	}

	public String getPersistentName() {
		return persistent_name;
	}

	public UserContextInfo getUserContext() {
		return user_context;
	}

	public boolean isServerPerfStatsRequested() {
		return server_perf_stats_requested;
	}

	@Override
	public String toString() {
		return "InvokeIMessage{" +
			"args=" + ( args == null ? null : Arrays.asList( args ) ) +
			", call_id=" + call_id +
			", object_id=" + object_id +
			", method_id=" + method_id +
			", persistent_name=" + persistent_name +
			", user_context=" + user_context +
			", server_perf_stats_requested=" + server_perf_stats_requested +
			'}';
	}


	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		InvokeIMessage that = ( InvokeIMessage ) o;

		if ( call_id != that.call_id ) return false;
		if ( method_id != that.method_id ) return false;
		if ( object_id != that.object_id ) return false;
		if ( server_perf_stats_requested != that.server_perf_stats_requested )
			return false;
		// Probably incorrect - comparing Object[] arrays with Arrays.equals
		if ( !Arrays.equals( args, that.args ) ) return false;
		if ( persistent_name != null ? !persistent_name.equals( that.persistent_name ) :
			that.persistent_name != null ) return false;
		if ( user_context != null ? !user_context.equals( that.user_context ) :
			that.user_context != null ) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = call_id;
		result = 31 * result + method_id;
		result = 31 * result + ( args != null ? Arrays.hashCode( args ) : 0 );
		result = 31 * result + object_id;
		result =
			31 * result + ( persistent_name != null ? persistent_name.hashCode() : 0 );
		result = 31 * result + ( user_context != null ? user_context.hashCode() : 0 );
		result = 31 * result + ( server_perf_stats_requested ? 1 : 0 );
		return result;
	}
}
