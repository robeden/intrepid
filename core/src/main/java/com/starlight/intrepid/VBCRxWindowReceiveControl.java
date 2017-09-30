package com.starlight.intrepid;

import com.logicartisan.common.core.thread.ScheduledExecutor;
import com.logicartisan.common.core.thread.SharedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


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
	 * @param message_id            If the data being released corresponds to the
	 *                              completion of a message, this will provide the message
	 *                              to which the data belongs (a short). Otherwise, this
	 *                              will be {@code Integer.MIN_VALUE}.
	 * @param ack_sender            A callback that should be user to trigger the sending
	 *                              of an Ack message. This callback will be thread safe
	 *                              and may be kept for later, asynchronous usage.
	 */
	void releaseFromBuffer( @Nonnegative int release_bytes, int message_id,
		AckSender ack_sender );


	/**
	 * Get the current rx windows size.
	 */
	int currentWindowSize();




	/**
	 * A non-throttling implementation for backwards compatibility with connections that
	 * don't support an rx window.
	 */
	VBCRxWindowReceiveControl UNBOUNDED = new VBCRxWindowReceiveControl() {
		@Override public void reserveInBuffer( int bytes ) {}

		@Override public void releaseFromBuffer( @Nonnegative int release_bytes,
			int message_id, AckSender ack_sender ) {}

		@Override public int currentWindowSize() {
			return Integer.MAX_VALUE;
		}
	};




	static VBCRxWindowReceiveControl create( int initial_window_size ) {
		String impl = System.getProperty( "intrepid.channel.window_control",
			"ProportionalTimer" ).toLowerCase();
		switch( impl ) {
			case "proportionaltimer":
				return new ProportionalTimer( initial_window_size );

			case "quidproquo":
				return new QuidProQuo( initial_window_size );

			default:
				throw new IllegalArgumentException(
					"Unknown VBCRxWindowReceiveControl implementation specified in " +
					"'intrepid.channel.window_control': " + impl );
		}
	}



	// Naïve implementation in which every message is ack'ed. This is likely to lead to
	// Silly Window Syndrome.
	class QuidProQuo implements VBCRxWindowReceiveControl {
		private final AtomicInteger available;


		QuidProQuo( int size ) {
			this.available = new AtomicInteger( size );
		}



		@Override
		public void reserveInBuffer( int bytes ) {
			available.addAndGet( -bytes );
		}

		@Override
		public void releaseFromBuffer( @Nonnegative int release_bytes, int message_id,
			AckSender ack_sender ) {

			if ( message_id == Integer.MIN_VALUE ) return;

			//noinspection ResultOfMethodCallIgnored
			ack_sender.send( ( short ) message_id, available.addAndGet( release_bytes ) );
		}

		@Override
		public int currentWindowSize() {
			return available.get();
		}
	}


	// This implementation only sends acks under one of these conditions:
	//   - A "significant" amount of data needs to be acknowledged
	//   - A "significant" amount of time has gone by
	// What is "significant" is obviously up to interpretation. For data, it is determined
	// by a particular proportion of the overall window size, which is specified during
	// construction. The default will ack at 30% of the window.
	// For time
	class ProportionalTimer implements VBCRxWindowReceiveControl {
		private static final Logger LOG =
			LoggerFactory.getLogger( ProportionalTimer.class );


		private final int window_size;

		private final int ack_at_data_amount;
		private final long ack_at_time_ms;
		private final ScheduledExecutor timer_executor;

		private final AtomicReference<ScheduledFuture<?>> outstanding_timer =
			new AtomicReference<>();

		private final Lock data_lock = new ReentrantLock();
		private volatile int latest_message = Integer.MIN_VALUE;
		private volatile int outstanding_data = 0;
		private volatile int messageless_outstanding_data = 0;

		private final AtomicInteger ack_send_fail_count = new AtomicInteger( 0 );


		ProportionalTimer( int window_size ) {
			this( window_size, 0.3f, 300, SharedThreadPool.INSTANCE );
		}

		// For testing
		ProportionalTimer( int window_size, float ack_at_data_percentage,
			long ack_at_receive_delay_ms, ScheduledExecutor executor ) {

			if ( ack_at_data_percentage <= 0 || ack_at_data_percentage > 1.0f ) {
				throw new IllegalArgumentException( "Invalid ack_at_data_percentage: " +
					ack_at_data_percentage );
			}

			this.window_size = window_size;

			ack_at_time_ms = ack_at_receive_delay_ms;
			ack_at_data_amount = Math.round( window_size * ack_at_data_percentage );
			this.timer_executor = executor;
		}



		@Override
		public void reserveInBuffer( int bytes ) {}



		@Override
		public void releaseFromBuffer( int release_bytes, int message_id,
			AckSender ack_sender ) {

			ScheduledFuture<?> old_timer = outstanding_timer.getAndSet( null );
			if ( old_timer != null ) old_timer.cancel( false );

			boolean schedule_timer = true;
			data_lock.lock();
			try {
				if ( message_id == Integer.MIN_VALUE ) {
					// Data associated outside a message context is tracked separately so
					// it's known exactly what we're acking when we ack a message.
					messageless_outstanding_data += release_bytes;
				}
				else {
					int outstanding =
						outstanding_data + release_bytes + messageless_outstanding_data;
					messageless_outstanding_data = 0;
					latest_message = ( short ) message_id;

					if ( outstanding >= ack_at_data_amount ) {
						sendAck( ack_sender );                      // immediate send
						schedule_timer = false;
					}
					else outstanding_data = outstanding;
				}
			}
			finally {
				data_lock.unlock();
			}

			if ( schedule_timer ) {
				ScheduledFuture<?> future =
					timer_executor.schedule( () -> sendAck( ack_sender ),
						ack_at_time_ms, TimeUnit.MILLISECONDS );
				outstanding_timer.set( future );
			}
		}



		@Override
		public int currentWindowSize() {
			return window_size;
		}


		private void sendAck( AckSender ack_sender ) {
			short message_to_ack;
			data_lock.lock();
			try {
				int latest_message = this.latest_message;
				if ( latest_message == Integer.MIN_VALUE ) {
					LOG.debug( "Tried to send ack with no latest message. " +
						"Aborting send." );
					return;
				}
				message_to_ack = ( short ) latest_message;

				int outstanding = this.outstanding_data;
				if ( outstanding == 0 ) {
					LOG.debug( "Tried to send ack with no outstanding data. " +
						"Aborting send." );
					return;
				}
				this.outstanding_data = 0;
			}
			finally {
				data_lock.unlock();
			}

			if ( !ack_sender.send( message_to_ack, -1 ) ) {
				int count = ack_send_fail_count.incrementAndGet();
				if ( count < 5 ) {
					timer_executor.schedule( () -> sendAck( ack_sender ),
						250, TimeUnit.MILLISECONDS );
				}
			}
			else ack_send_fail_count.set( 0 );
		}
	}



	@FunctionalInterface
	interface AckSender {
		/**
		 * @return      True if the message was successfully sent.
		 */
		@CheckReturnValue
		boolean send( short message_id, int new_window_size );
	}
}
