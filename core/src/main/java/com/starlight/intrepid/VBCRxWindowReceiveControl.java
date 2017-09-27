package com.starlight.intrepid;

import javax.annotation.Nonnegative;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 */
interface VBCRxWindowReceiveControl {
	void reserveInBuffer( @Nonnegative int bytes );

	/**
	 * This should be called when a chunk of data has been processed and can be "released"
	 * from rx window considerations.
	 *
	 * @param release_bytes         The amount of data (bytes) that has been processed.
	 *                              Note that "0" is an acceptable value.
	 *
	 * @return                      The new (current after processing) rx window to
	 *                              advertise to the peer.
	 */
	@Nonnegative
	int releaseFromBufferAndGetWindowSize( @Nonnegative int release_bytes );


	/**
	 * Get the current rx windows size (equivalent to
	 * {@link #releaseFromBufferAndGetWindowSize(int) releaseProcessedDataAndGetWindowSize(0)}).
	 */
	default int currentWindowSize() {
		return releaseFromBufferAndGetWindowSize( 0 );
	}




	/**
	 * A non-throttling implementation for backwards compatibility with connections that
	 * don't support an rx window.
	 */
	VBCRxWindowReceiveControl UNBOUNDED = new VBCRxWindowReceiveControl() {
		@Override public void reserveInBuffer( int bytes ) {}

		@Override public int releaseFromBufferAndGetWindowSize( int release_bytes ) {
			return Integer.MAX_VALUE;
		}
	};





	// This is a pretty naive implementation. May want something better in the future
	// to avoid Silly Window Syndrome (see https://tools.ietf.org/html/rfc813). However,
	// I haven't really seen SWS in testing and performance has been adequate for now,
	// so I'm going to start with this implementation to keep it simple.
	class Simple implements VBCRxWindowReceiveControl {
		private final AtomicInteger available;


		Simple( int size ) {
			this.available = new AtomicInteger( size );
		}



		@Override
		public void reserveInBuffer( int bytes ) {
			available.addAndGet( -bytes );
		}

		@Override
		public int releaseFromBufferAndGetWindowSize( int release_bytes ) {
			return available.addAndGet( release_bytes );
		}

		@Override
		public int currentWindowSize() {
			return available.get();
		}
	}
}
