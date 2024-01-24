package com.starlight.intrepid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Manage the receive window (the amount of data that can be sent without being
 * acknowledged) for a SENDING data to/with a {@link VirtualByteChannel}.
 * This works very similarly to a Semaphore in that it has a certain number of permits
 * that can be acquired and released. There are two significant difference:
 * <ol>
 *     <ul>The same thread does not need to release permits that acquired them</ul>
 *     <ul>The number of permits may be changed dynamically based on the desires
 *     of the peer</ul>
 * </ol>
 * Also of note is the fact that a data size is specified during
 * {@link #tryAcquire(int, int, short) tryAcquire} but does not need to be tracked
 * (externally) from that point on as it will be known from the message ID.
 */
interface VBCRxWindowSendControl {
	/**
	 * Attempt to acquire permits for a certain number of bytes.
	 *
	 * @param desired_count     The number of permits that the caller would like to be
	 *                          granted.
	 * @param min_count         The minimum number of permits that the caller will accept.
	 *                          The call will block until this many permits can be
	 *                          obtained, UNLESS the window size is smaller than this
	 *                          number in which case the call will block until the entire
	 *                          window is available and that value will be returned.
	 * @param message_id        ID of the message that will be associated with this data.
	 *                          It is not required that the ID in any special order, but
	 *                          it is required that the ID be unique among the IDs that
	 *                          are outstanding (see {@link #releaseAndResize}).
	 *
	 * @return                  The actual number of permits granted. Note that this
	 *                          may be less than the {@code min_count} (see docs for
	 *                          that parameter).
	 */
	int tryAcquire( int desired_count, int min_count, short message_id )
		throws InterruptedException;


	/**
	 * Release all the data acquired in the sending a given message and all preceding
	 * messages, also update the size of the window.
	 *
	 * Note that the new window can be smaller than the old window which could
	 * mean that we're currently over the limit. If the number is larger than the current
	 * window, new permits will be available immediately.
	 *
	 * @param up_to_message     The specified message and all preceding outstanding
	 *                          messages will be released.
	 * @param new_size          The (possibly) new window size. A value less than zero
	 *                          indicates the value is unchanged from the previously
	 *                          specified value.
	 *
	 * @return          True if the message ID was known and all is well. If the message
	 *                  ID was somehow unknown, false will be returned. This indicates
	 *                  a fundamental breakdown in communication.
	 */
	@CheckReturnValue
	boolean releaseAndResize( short up_to_message, int new_size );

	/**
	 * Specify a new window size. Note that this can be smaller number which could
	 * mean that we're currently over the limit. If the number is larger than the current
	 * window, new permits will be available immediately.
	 */
	void resize( int new_size );



	/**
	 * Non-tracking implementation for support of older peers.
	 */
	VBCRxWindowSendControl UNBOUNDED = new VBCRxWindowSendControl() {
		@Override public int tryAcquire(
			int desired_count, int min_count, short message_id ) {

			return desired_count;
		}

		@Override public boolean releaseAndResize( short up_to_message, int new_size ) {
			return true;
		}

		@Override public void resize( int new_size ) {}
	};


	class RingBuffer implements VBCRxWindowSendControl {
		private static final Logger LOG = LoggerFactory.getLogger( RingBuffer.class );

		// Max size the ring will be allowed to grow
		private static final int DEFAULT_MAX_RING_SIZE = 10240;
		private static final int DEFAULT_INIT_RING_SIZE = 100;


		// Size of the active window
		private volatile int configured_window;

		// Amount of data currently in use (reserved via acquire)
		private volatile int window_in_use;

		private final ReentrantLock lock = new ReentrantLock();
		private final Condition space_freed = lock.newCondition();

		private final int max_ring_size;

		private short[] message_id_ring;
		private int[] message_size_ring;
		private int data_index = 0;             // Index of the next data element
		private int free_space_index = 0;       // Index of the next free slot
		private int size = 0;					// Number of slots in use

		RingBuffer( int initial_window ) {
			this( initial_window, DEFAULT_INIT_RING_SIZE, DEFAULT_MAX_RING_SIZE );
		}

		// This constructor exists for testing
		RingBuffer( int initial_window, int ring_size, int max_ring_size ) {
			configured_window = initial_window;
			this.max_ring_size = max_ring_size;

			message_id_ring = new short[ ring_size ];
			message_size_ring = new int[ ring_size ];
		}


		@Override
		public void resize( int new_window ) {
			if ( new_window < 0 ) return;

			lock.lock();
			try {
				boolean window_size_increasing = new_window > configured_window;

				LOG.debug( "Window is now {}", new_window );
				configured_window = new_window;
				if ( window_size_increasing ) {
					space_freed.signalAll();
				}
			}
			finally {
				lock.unlock();
			}
		}


		@Override
		public int tryAcquire( int desired_count, int min_count, short message_id )
			throws InterruptedException {

			lock.lock();
			try {
				final boolean debug = LOG.isDebugEnabled();

				long start = debug ? System.nanoTime() : 0;
				boolean had_to_wait = false;

				// Wait for enough free space
				int available;
				while( ( available = configured_window - window_in_use ) < min_count ) {
					LOG.trace( "Waiting to acquire {}-{} bytes for message {}",
						min_count, desired_count, message_id );

					had_to_wait = true;
					// GENTLE REMINDER: It isn't guaranteed that any more free space is
					//                  actually available when we pop out of this.
					space_freed.await();
				}

				if ( debug && had_to_wait ) {
					long time_ns = System.nanoTime() - start;
					LOG.debug( "Had to wait {} ms to acquire right to send {}-{} " +
						"bytes for message {}", TimeUnit.NANOSECONDS.toMillis( time_ns ),
						min_count, desired_count, message_id );
				}

				int reservable_amount = Math.min( available, desired_count );

				start = debug ? System.nanoTime() : 0;
				had_to_wait = false;
				while ( ! insertRingData( message_id, reservable_amount ) ) {
					had_to_wait = true;
					// GENTLE REMINDER: It isn't guaranteed that any more free space is
					//                  actually available when we pop out of this.
					space_freed.await();
				}

				if ( debug && had_to_wait ) {
					long time_ns = System.nanoTime() - start;
//					System.out.println( "Ring insert wait: " +
//						TimeUnit.NANOSECONDS.toMillis( time_ns ));
					LOG.debug( "Had to wait {} ms for space in send message ring " +
						"buffer for message {}", TimeUnit.NANOSECONDS.toMillis( time_ns ),
						min_count, desired_count, message_id );
				}


				window_in_use += reservable_amount;

				return reservable_amount;
			}
			finally {
				lock.unlock();
			}
		}

		@Override
		public boolean releaseAndResize( short up_to_message, int new_size ) {
			boolean found_message = false;
			lock.lock();
			try {
				if ( new_size >= 0 ) {
					LOG.debug( "Window is now {}", new_size );
					configured_window = new_size;
				}

				int released = 0;

				while( true ) {
					// Check for no nodes left
					if ( size == 0 ) {
						break;
					}

					// Space being freed
					released += message_size_ring[ data_index ];

					size--;

					short node_message_id = message_id_ring[ data_index ];

					// Check for data index wrapping
					data_index++;
					if ( data_index == message_id_ring.length ) data_index = 0;

					if ( node_message_id == up_to_message ) {
						found_message = true;
						break;
					}
				}

				LOG.trace( "Release window {}", released );

				window_in_use -= released;

				// Not guaranteed, but we probably freed space
				space_freed.signalAll();
			}
			finally {
				lock.unlock();
			}

			return found_message;
		}



		// For testing
		int windowInUse() {
			return window_in_use;
		}



		/**
		 * For testing, iterate over elements in the ring.
		 *
		 * @return  Number of elements touched
		 */
		int forEachElement( MessageSizeConsumer consumer ) {
			lock.lock();
			try {
				int ring_index = data_index;
				for( int i = 0; i < size; i++ ) {
					consumer.accept( message_id_ring[ ring_index ],
						message_size_ring[ ring_index ] );

					ring_index++;
					if ( ring_index == message_id_ring.length ) ring_index = 0;
				}

				return size;
			}
			finally {
				lock.unlock();
			}
		}



		/**
		 * @return      False if the data was not inserted into the ring because no space
		 *              is available and the ring is not allowed to grow anymore.
		 */
		private boolean insertRingData( short message_id, int reservation ) {
			if ( !growRingIfNecessary() ) return false;

			// NOTE: At this point the free_space_index may be past the end of the array,
			//       indicating that it needs to wrap to 0. (We don't wrap until insert)
			if ( free_space_index == message_id_ring.length ) free_space_index = 0;

			message_id_ring[ free_space_index ] = message_id;
			message_size_ring[ free_space_index ] = reservation;

			free_space_index++;
			size++;
			return true;
		}



		/**
		 * @return      An indication as to whether or not all is well with the ring. This
		 *              will return true if either there was already space in the ring or
		 *              if the ring needed to grow and successfully did so. Effectively,
		 *              this returns false in there is no more space in the ring.
		 */
		private boolean growRingIfNecessary() {
			assert lock.isHeldByCurrentThread();

			final int ring_size = ringSize();
			if ( ring_size < message_id_ring.length ) return true;

			int new_ring_capacity = message_size_ring.length * 2;
			if ( new_ring_capacity > max_ring_size ) {
				LOG.trace( "Channel send ring full: {}", max_ring_size );
				return false;
			}

//			System.out.println( "new ring size: " + new_ring_capacity );

			short[] new_message_id_ring = new short[ new_ring_capacity ];
			int[] new_message_size_ring = new int[ new_ring_capacity ];

			// Simple case, no wrapping
			if ( free_space_index > data_index ) {
				System.arraycopy( message_id_ring, data_index, new_message_id_ring, 0,
					ring_size );
				System.arraycopy( message_size_ring, data_index, new_message_size_ring, 0,
					ring_size );
			}
			// Wrapped case
			else {
				int high_block_length = message_size_ring.length - data_index;
				int low_block_length = free_space_index;

				System.arraycopy( message_id_ring, data_index, new_message_id_ring, 0,
					high_block_length );
				System.arraycopy( message_id_ring, 0, new_message_id_ring,
					high_block_length, low_block_length );

				System.arraycopy( message_size_ring, data_index, new_message_size_ring, 0,
					high_block_length );
				System.arraycopy( message_size_ring, 0, new_message_size_ring,
					high_block_length, low_block_length );
			}
			message_id_ring = new_message_id_ring;
			message_size_ring = new_message_size_ring;
			data_index = 0;
			free_space_index = ring_size;
			return true;
		}

		// Visible for testing
		int ringSize() {
			return size;
		}
	}


	@FunctionalInterface
	interface MessageSizeConsumer {
		/**
		 * @return      True if processing should continue
		 */
		boolean accept( short message_id, int size );
	}
}
