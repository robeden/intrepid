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


import com.logicartisan.common.core.thread.SharedThreadPool;
import com.starlight.intrepid.exception.NotConnectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.requireNonNull;


/**
 *
 */
class VirtualByteChannel implements ByteChannel {
	private static final Logger LOG =
		LoggerFactory.getLogger( VirtualByteChannel.class );



	// Put in the buffer to signal a "normal" close
	private static final BufferToMessageWrapper CLOSED_FLAG = 
		new BufferToMessageWrapper( ByteBuffer.allocate( 1 ) );

	private final VMID remote_vmid;
	private final short id;
	private final RemoteCallHandler handler;

	private final VBCRxWindowReceiveControl receive_window_control;
	private final VBCRxWindowSendControl send_window_control;
	private final AtomicInteger message_id_source = new AtomicInteger(
		// Setting a random range so we hit rollover frequently
		new Random().nextInt( Short.MAX_VALUE ) );

	private final ReentrantLock read_operation_lock = new ReentrantLock();
	private final BlockingQueue<BufferToMessageWrapper> read_buffer_queue =
		new LinkedBlockingQueue<>();
	private volatile BufferToMessageWrapper read_active_wrapper = null;

	private volatile boolean closed_locally = false;
	private volatile boolean closed_remotely = false;



	VirtualByteChannel( VMID remote_vmid, short id,
		VBCRxWindowReceiveControl receive_window_control,
		VBCRxWindowSendControl send_window_control, RemoteCallHandler handler ) {

		this.remote_vmid = requireNonNull( remote_vmid );
		this.id = id;
		this.receive_window_control = requireNonNull( receive_window_control );
		this.send_window_control = requireNonNull( send_window_control );
		this.handler = requireNonNull( handler );
	}


	// CONCURRENCY NOTES:
	// There are two important synchronization points while reading:
	//  1) Per ReadableByteChannel docs...
	//       "Only one read operation upon a readable channel may be in progress at
	//        any given time. If one thread initiates a read operation upon a channel
	//        then any other thread that attempts to initiate another read operation
	//        will block until the first operation is complete. Whether or not other
	//        kinds of I/O operations may proceed concurrently with a read operation
	//        depends upon the type of the channel."
	//     ... the entire read(...) method is synchronized.
	//
	//  2) Within the read operation, the call must be able to block while waiting for
	//     data (while -of course- allowing the data queue to be filled).
	//
	// Writing is easier as send blocking (for the RX window) is managed externally.


	@Override
	public int read( ByteBuffer buffer ) throws IOException {
		if ( closed_locally ) throw new ClosedChannelException();

		if ( !buffer.hasRemaining() ) return 0;

		AtomicInteger ack_message = new AtomicInteger( Integer.MIN_VALUE );
		int read;
		read_operation_lock.lock();
		try {
			read = readIntoBuffer( buffer, ack_message );
		}
		catch( InterruptedException ex ) {
			throw new InterruptedIOException();
		}
		finally {
			read_operation_lock.unlock();
		}

		// RX Window reservation released
		int new_window_size =
			receive_window_control.releaseFromBufferAndGetWindowSize( read );

		int message_id = ack_message.get();
		if ( message_id != Integer.MIN_VALUE ) {
			handler.channelSendDataAck( remote_vmid, id, ( short ) message_id,
				new_window_size );
		}

		return read;
	}



	/**
	 * Returns the current size of the Rx window to be advertised to peers.
	 */
	int getCurrentRxWindow() {
		return receive_window_control.currentWindowSize();
	}



	private volatile int last_message_id;
	/**
	 * Queues a buffer to be read by the channel.
	 *
	 * @param message_id        The message ID associated with the buffer. If no message
	 *                          ID is desired, {@code Integer.MIN_VALUE} should be
	 *                          specified. Otherwise, the value should be a short.
	 */
	void putData( ByteBuffer buffer, int message_id ) {
		if ( closed_locally || closed_remotely ) return;

		// RX Window starts reservation here
		receive_window_control.reserveInBuffer( buffer.remaining() );

		if ( LOG.isTraceEnabled() ) {
			LOG.trace( "Put data buffer (size={}) for virtual channel {}",
				Integer.valueOf( buffer.remaining() ), Short.valueOf( id ) );
		}

		// Should never have a message ID which exists in the queue
		assert read_buffer_queue.stream()
			.noneMatch( wrapper -> wrapper.message_id == message_id ) :
			"Attempting to put buffer with message ID " + message_id +
			" into queue when it's already in the queue: " + read_buffer_queue +
			". Last message ID was " + last_message_id;
		last_message_id = message_id;

		read_buffer_queue.add(
			new BufferToMessageWrapper( buffer, message_id & 0xFFFF ) );
	}

	void closedByPeer() {
		closed_remotely = true;
		read_buffer_queue.add( CLOSED_FLAG );
	}


	void processDataAck( short ack_through_message_id, int new_window_size ) {
		if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Channel {} received ack for message {} (new window={})",
				id, Short.valueOf( ack_through_message_id ), new_window_size );
		}

		// If the ack could not be processed, shut the channel down
		if ( !send_window_control.releaseAndResize(
			ack_through_message_id, new_window_size ) ) {

			LOG.warn( "Channel {} will be closed because a data ack from the peer " +
				"referenced message {}, which is unknown.", id, ack_through_message_id );
			handleClose();

			// Do this outside the processor thread
			SharedThreadPool.INSTANCE.execute(
				() -> handler.channelClose( remote_vmid, id, true ) );
		}
	}



	/**
	 * @param ack_message   If set to a value other than Integer.MIN_VALUE, then this is
	 *                      the ID of the message to be ack'ed.
	 */
	private int readIntoBuffer( ByteBuffer dest_buffer, AtomicInteger ack_message )
		throws IOException, InterruptedException {

		int total_read = 0;
		while( dest_buffer.hasRemaining() ) {
			int read = readPiece( dest_buffer, total_read == 0, ack_message );
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



	/**
	 * Read some data... doesn't have to be the full buffer.
	 *
	 * @param need_data     If false, it's okay to come up empty. In that case, a lack of
	 *                      data will immediately cause a return of '0'. If true, calling
	 *                      will block until some data is available, in which case '0'
	 *                      may still be returned, but subsequent calls will return data.
	 *
	 * @return      The amount of data read, -1 for a soft close.
	 */
	private int readPiece( ByteBuffer dest_buffer, boolean need_data,
		AtomicInteger ack_message ) throws IOException, InterruptedException {
		
		assert read_operation_lock.isHeldByCurrentThread();

		if ( closed_locally ) throw new ClosedChannelException();

		// See if there's a buffer active already
		BufferToMessageWrapper active_wrapper = read_active_wrapper;
		if ( active_wrapper != null ) {
			if ( active_wrapper == CLOSED_FLAG ) return -1;     // NOTE: Don't clear

			int pos_before_read = active_wrapper.buffer.position();

			int original_limit = 0;
			if ( dest_buffer.remaining() < active_wrapper.buffer.remaining() ) {
				original_limit = active_wrapper.buffer.limit();
				active_wrapper.buffer.limit( active_wrapper.buffer.position() + dest_buffer.remaining() );
			}
			dest_buffer.put( active_wrapper.buffer );
			int read = active_wrapper.buffer.position() - pos_before_read;
			if ( original_limit != 0 ) {
				active_wrapper.buffer.limit( original_limit );
				return read;
			}

			if ( !active_wrapper.buffer.hasRemaining() ) {
				// Indicate the message that should be ack'ed. As more pieces are read,
				// this may be overwritten by newer messages (which is a good thing).
				if ( active_wrapper.message_id != Integer.MIN_VALUE ) {
					ack_message.set( active_wrapper.message_id );
				}

				read_active_wrapper = null;
			}

			return read;
		}

		// If not, try to pull one from the queue
		BufferToMessageWrapper wrapper;
		while( ( wrapper =
			// If we need data, wait for it. Otherwise, don't.
			( need_data ? read_buffer_queue.take() : read_buffer_queue.poll() ) ) == null ) {

			if ( !need_data ) return 0;
			if ( closed_locally ) throw new ClosedChannelException();
		}
//		System.out.println( "VBC read wrapper: " + wrapper );
		read_active_wrapper = wrapper;
		if ( wrapper == CLOSED_FLAG ) return -1;

		return 0;   // keep trying
	}



	@Override
	public int write( ByteBuffer buffer ) throws IOException {
		if ( closed_locally || closed_remotely ) throw new ClosedChannelException();

		int remaining = buffer.remaining();
		try {
			handler.channelSendData( remote_vmid, id, buffer, send_window_control,
				() -> ( short ) message_id_source.incrementAndGet() );
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

	@Override
	public boolean isOpen() {
		return !closed_locally;
	}

	@Override
	public void close() {
		handleClose();
		handler.channelClose( remote_vmid, id, true );
	}

	private void handleClose() {
		closed_locally = true;

		if ( read_active_wrapper == CLOSED_FLAG ) return;

		read_operation_lock.lock();
		try {
			read_active_wrapper = CLOSED_FLAG;
			read_buffer_queue.add( CLOSED_FLAG );   // ensure nothing is blocked on read
		}
		finally {
			read_operation_lock.unlock();
		}

	}



	/**
	 * Tracks the message ID associated with a buffer so we know which one(s) to ack when
	 * a buffer is fully consumed.
	 */
	private static class BufferToMessageWrapper {
		private final ByteBuffer buffer;
		private final int message_id;       // min for none

		BufferToMessageWrapper( ByteBuffer buffer ) {
			this( buffer, Integer.MIN_VALUE );
		}
		
		BufferToMessageWrapper( ByteBuffer buffer, int message_id ) {
			this.buffer = buffer;
			this.message_id = message_id;
		}

		@Override public String toString() {
			if ( this == CLOSED_FLAG ) return "BufferToMessageWrapper - CLOSED";

			return "BufferToMessageWrapper{" +
				"message_id=" + message_id +
				", buffer=" + buffer +
				'}';
		}
	}
}
