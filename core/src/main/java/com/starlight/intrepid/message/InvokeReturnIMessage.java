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

/**
 *
 */
public class InvokeReturnIMessage implements IMessage {
	private final int call_id;
	private final Object value;
	private final boolean is_thrown;
	private final Integer new_object_id;
	private final Long server_time_ns;


	public InvokeReturnIMessage( int call_id, Object value, boolean is_thrown,
		Integer new_object_id, Long server_time_ns ) {

		this.call_id = call_id;
		this.value = value;
		this.is_thrown = is_thrown;
		this.new_object_id = new_object_id;
		this.server_time_ns = server_time_ns;
	}

	@Override
	public IMessageType getType() {
		return IMessageType.INVOKE_RETURN;
	}

	public int getCallID() {
		return call_id;
	}

	public boolean isThrown() {
		return is_thrown;
	}

	public Object getValue() {
		return value;
	}

	public Integer getNewObjectID() {
		return new_object_id;
	}

	public Long getServerTimeNano() {
		return server_time_ns;
	}

	@Override
	public String toString() {
		return "InvokeReturnIMessage{" +
			"call_id=" + call_id +
			", value=" + value +
			", is_thrown=" + is_thrown +
			", new_object_id=" + new_object_id +
			", server_time=" + server_time_ns +
			'}';
	}



	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		InvokeReturnIMessage that = ( InvokeReturnIMessage ) o;

		if ( call_id != that.call_id ) return false;
		if ( is_thrown != that.is_thrown ) return false;
		if ( value != null ? !value.equals( that.value ) : that.value != null )
			return false;
		if ( new_object_id != null ? !new_object_id.equals( that.new_object_id ) :
			that.new_object_id != null ) {
			return false;
		}
		return server_time_ns != null ? server_time_ns.equals( that.server_time_ns ) :
			that.server_time_ns == null;
	}

	@Override
	public int hashCode() {
		int result = call_id;
		result = 31 * result + ( value != null ? value.hashCode() : 0 );
		result = 31 * result + ( is_thrown ? 1 : 0 );
		result = 31 * result + ( new_object_id != null ? new_object_id.hashCode() : 0 );
		result = 31 * result + ( server_time_ns != null ? server_time_ns.hashCode() : 0 );
		return result;
	}
}
