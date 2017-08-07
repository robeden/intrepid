package com.starlight.intrepid.driver.mina;

import com.logicartisan.common.core.thread.NamingThreadFactory;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.session.IoSession;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * It's been found (at least on Linux, unsure on other platforms) that closing a session
 * too quickly after it is opened can cause the connection to get "stuck" and require
 * a timeout to pass. If we ensure that a session is open for at least a second or so
 * (on our test linux system, Fedora 15) there are no issues. So, this class is used
 * to ensure a session has been open a reasonable amount of time before it is closed.
 */
class CloseHandler {
	private static final long SESSION_MIN_CLOSE_TIME =
		Long.getLong( "intrepid.driver.mina.close_min_time", 5000 ).longValue();

	private static final int SESSION_CLOSE_THREADS =
		Integer.getInteger( "intrepid.driver.min.close_threads", 2 ).intValue();

	private static final ScheduledExecutorService schedule_executor;
	static {
		if ( SESSION_MIN_CLOSE_TIME <= 0 ) schedule_executor = null;
		else {
			schedule_executor = new ScheduledThreadPoolExecutor( SESSION_CLOSE_THREADS,
				new NamingThreadFactory( "Intrepid CloseHandler-", true ) );
		}
	}


	/**
	 * Close the session using a default amount of "nice" time before a forceful close.
	 */
	static void close( IoSession session ) {
		close( session, 2000 );
	}

	/**
	 * Close the session, first trying to close it "nicely" (allowing messages in the
	 * queue to be sent) and then closing it forcefully if a nice close doesn't finish
	 * in the allotted time.
	 *
	 * @param nice_close_time_ms    The amount of time (in milliseconds) to allow the
	 *                              "nice" close to finish before forcefully closing the
	 *                              session, if the nice close hasn't completed.
	 */
	static void close( IoSession session, long nice_close_time_ms ) {
		CloseRunnable runnable = new CloseRunnable( session, nice_close_time_ms );
		if ( SESSION_MIN_CLOSE_TIME <= 0 ) schedule_executor.execute( runnable );
		else {
			Long create_time =
				( Long ) session.getAttribute( MINAIntrepidDriver.CREATED_TIME_KEY );
			if ( create_time == null ) {
				assert false : "Null create time: " + session;
				create_time = Long.valueOf( System.nanoTime() );    // assume worst case
			}

			long elapse_ms = TimeUnit.NANOSECONDS.toMillis(
				System.nanoTime() - create_time.longValue() );
			if ( elapse_ms < 0 ) elapse_ms = 0; // sanity check

			long delay = nice_close_time_ms - elapse_ms;
			if ( delay > 0 ) {
				schedule_executor.schedule( runnable, delay, TimeUnit.MILLISECONDS );
			}
			else schedule_executor.execute( runnable );
		}
	}


	static class CloseRunnable implements Runnable {
		private final IoSession session;
		private final long nice_close_time_ms;

		CloseRunnable( IoSession session, long nice_close_time_ms ) {
			this.session = session;
			this.nice_close_time_ms = nice_close_time_ms;
		}


		@Override
		public void run() {
			if ( nice_close_time_ms >= 0 ) {
				CloseFuture future = session.close( false );
				future.awaitUninterruptibly( nice_close_time_ms );
				if ( future.isDone() ) return;
			}

			session.close( true );
		}
	}
}
