// Copyright (c) 2011 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
// * Neither the name of Intrepid nor the
// names of its contributors may be used to endorse or promote products
// derived from this software without specific prior written permission.
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


import com.starlight.intrepid.exception.NotConnectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 *
 */
class VirtualByteChannel implements ByteChannel {
	private static final Logger LOG =
		LoggerFactory.getLogger( VirtualByteChannel.class );



	// Put in the buffer to signal a "normal" close
	private static final ByteBuffer CLOSED_FLAG = ByteBuffer.allocate( 1 );
	// Put in the buffer to signal a forceful close
	private static final ByteBuffer FORCE_CLOSED_FLAG = ByteBuffer.allocate( 1 );

	private final VMID remote_vmid;
	private final short id;
	private final RemoteCallHandler handler;

	private final Lock lock = new ReentrantLock();
	private final Condition data_available = lock.newCondition();

	private final Queue<ByteBuffer> buffer_queue = new LinkedList<ByteBuffer>();
	private volatile ByteBuffer active_buffer = null;

	private volatile boolean closed = false;

	VirtualByteChannel( VMID remote_vmid, short id, RemoteCallHandler handler ) {
		this.remote_vmid = remote_vmid;
		this.id = id;
		this.handler = handler;
	}


	@Override
	public int read( ByteBuffer buffer ) throws IOException {
		if ( !isOpen() ) throw new ClosedChannelException();

		assert buffer.hasRemaining() : buffer;

		return lockWrapper( true, buffer );
	}

	@Override
	public int write( ByteBuffer buffer ) throws IOException {
		if ( !isOpen() ) throw new ClosedChannelException();

		assert buffer.hasRemaining() : buffer;

		return lockWrapper( false, buffer );
	}

	@Override
	public boolean isOpen() {
		return !closed;
	}

	@Override
	public void close() throws IOException {
		handler.channelClose( remote_vmid, id, true );
		lock.lock();
		try {
			handleClose();
		}
		finally {
			lock.unlock();
		}
	}


	void putData( ByteBuffer buffer ) {
		lock.lock();
		try {
			if ( LOG.isDebugEnabled() ) {
				LOG.debug( "Put data buffer (size={}) for virtual channel {}",
					Integer.valueOf( buffer.remaining() ), Short.valueOf( id ) );
			}
			buffer_queue.add( buffer );
			data_available.signalAll();
		}
		finally {
			lock.unlock();
		}
	}

	void closedByPeer( boolean forceful ) {
		putData( forceful ? FORCE_CLOSED_FLAG : CLOSED_FLAG );
	}


	private int lockWrapper( boolean read, ByteBuffer buffer ) throws IOException {
		try {
			lock.lockInterruptibly();
			try {
				if ( read ) return readNoLock( buffer );
				else return writeNoLock( buffer );
			}
			finally {
				lock.unlock();
			}
		}
		catch( InterruptedException ex ) {
			close();
			throw new ClosedByInterruptException();
		}
	}


	private int readNoLock( ByteBuffer dest_buffer )
		throws IOException, InterruptedException {

		int total_read = 0;
		while( dest_buffer.hasRemaining() ) {
			int read = readPiece( dest_buffer, total_read == 0 );
			if ( read == -1 ) {
				if ( total_read > 0 ) break;
				else return -1;
			}

			// If we previously got data and this time we didn't, return
			if( read == 0 && total_read != 0 ) return total_read;

			total_read += read;
		}
		return total_read;
	}

	private int readPiece( ByteBuffer dest_buffer, boolean need_data )
		throws IOException, InterruptedException {
		
		// See if there's a buffer active already
		if ( active_buffer != null ) {
			if ( active_buffer == CLOSED_FLAG ) return -1;
			if ( active_buffer == FORCE_CLOSED_FLAG ) throw new ClosedChannelException();

			int pos_before_read = active_buffer.position();

			int original_limit = 0;
			if ( dest_buffer.remaining() < active_buffer.remaining() ) {
				original_limit = active_buffer.limit();
				active_buffer.limit( active_buffer.position() + dest_buffer.remaining() );
			}
			dest_buffer.put( active_buffer );
			int read = active_buffer.position() - pos_before_read;
			if ( original_limit != 0 ) {
				active_buffer.limit( original_limit );
				return read;
			}

			if ( !active_buffer.hasRemaining() ) active_buffer = null;

			return read;
		}

		// If not, try to pull one from the queue
		ByteBuffer buffer;
		while( ( buffer = buffer_queue.poll() ) == null ) {
			if ( !need_data ) return 0;

			data_available.await();
			if ( closed ) throw new ClosedChannelException();
		}
		active_buffer = buffer;
		if ( active_buffer == CLOSED_FLAG ) return -1;
		if ( active_buffer == FORCE_CLOSED_FLAG ) throw new ClosedChannelException();

		return 0;   // keep trying
	}

	private int writeNoLock( ByteBuffer buffer ) throws IOException {
		if ( closed ) throw new ClosedChannelException();

		int remaining = buffer.remaining();
		try {
			handler.channelSendData( remote_vmid, id, buffer );
		}
		catch( NotConnectedException ex ) {
			handleClose();
			ClosedChannelException to_throw = new ClosedChannelException();
			to_throw.initCause( ex );
			throw to_throw;
		}
		catch( ClosedChannelException ex ) {
			handleClose();
			throw ex;
		}
		return remaining;
	}

	private void handleClose() {
		closed = true;
		active_buffer = null;
		buffer_queue.clear();
	}
}
