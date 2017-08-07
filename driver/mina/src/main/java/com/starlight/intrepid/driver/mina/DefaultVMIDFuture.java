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

package com.starlight.intrepid.driver.mina;

import com.starlight.intrepid.VMID;
import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.future.DefaultIoFuture;


/**
 *
 */
class DefaultVMIDFuture extends DefaultIoFuture implements VMIDFuture {
	private static final Object CANCELED = new Object();


	/**
	 * Creates a new instance.
	 */
	public DefaultVMIDFuture() {
		super( null );
	}

	@Override
	public VMID getVMID() {
		Object v = getValue();
		if ( v instanceof RuntimeException ) {
			throw ( RuntimeException ) v;
		}
		else if ( v instanceof Error ) {
			throw ( Error ) v;
		}
		else if ( v instanceof Throwable ) {
			throw ( RuntimeIoException ) new RuntimeIoException(
				"Failed to get the VMID." ).initCause( ( Throwable ) v );
		}
		else if ( v instanceof VMID ) {
			return ( VMID ) v;
		}
		else {
			return null;
		}
	}

	public Throwable getException() {
		Object v = getValue();
		if ( v instanceof Throwable ) {
			return ( Throwable ) v;
		}
		else {
			return null;
		}
	}

	public boolean isCanceled() {
		return getValue() == CANCELED;
	}

	public void setVMID( VMID vmid ) {
		if ( vmid == null ) {
			throw new IllegalArgumentException( "session" );
		}
		setValue( vmid );
	}

	public void setException( Throwable exception ) {
		if ( exception == null ) {
			throw new IllegalArgumentException( "exception" );
		}
		setValue( exception );
	}

	public void cancel() {
		setValue( CANCELED );
	}

	@Override
	public VMIDFuture await() throws InterruptedException {
		return ( VMIDFuture ) super.await();
	}

	@Override
	public VMIDFuture awaitUninterruptibly() {
		return ( VMIDFuture ) super.awaitUninterruptibly();
	}
}