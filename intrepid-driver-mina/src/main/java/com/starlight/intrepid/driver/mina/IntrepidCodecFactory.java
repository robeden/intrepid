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

import com.starlight.intrepid.ObjectCodec;
import com.starlight.intrepid.VMID;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.function.BiFunction;


/**
 *
 */
class IntrepidCodecFactory implements ProtocolCodecFactory {
	private final ProtocolDecoder decoder;
	private final ProtocolEncoder encoder;


	IntrepidCodecFactory(@Nonnull VMID vmid,
						 @Nonnull ThreadLocal<VMID> deserialization_context_vmid,
						 @Nonnull BiFunction<UUID,String,VMID> vmid_creator,
						 @Nonnull ObjectCodec object_codec) {

		decoder = new MINAIMessageDecoder( vmid, deserialization_context_vmid,
			vmid_creator, object_codec );
		encoder = new MINAIMessageEncoder(object_codec);
	}


	@Override
	public ProtocolDecoder getDecoder( IoSession session ) {
		return decoder;
	}

	@Override
	public ProtocolEncoder getEncoder( IoSession session ) {
		return encoder;
	}
}
