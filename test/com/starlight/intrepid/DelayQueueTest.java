package com.starlight.intrepid;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class DelayQueueTest {
	public static void main( String[] args ) throws Exception {
		DelayQueue<MyDelayedObject> queue = new DelayQueue<MyDelayedObject>();

		queue.add( new MyDelayedObject( 1000 ) );
		queue.add( new MyDelayedObject( 10000 ) );
		queue.add( new MyDelayedObject( -1000 ) );
		queue.add( new MyDelayedObject( -10000 ) );
		queue.add( new MyDelayedObject( 5000 ) );
		queue.add( new MyDelayedObject( 2000 ) );
		queue.add( new MyDelayedObject( 7500 ) );

		long start = System.currentTimeMillis();

		System.out.println( "Start poll..." );
		while( !queue.isEmpty() ) {
			MyDelayedObject mdo = queue.poll( 10, TimeUnit.SECONDS );
			System.out.println( "   Got: " + mdo.delay_nano +
				" (" + ( System.currentTimeMillis() - start ) + ")" );
		}
	}


	private static class MyDelayedObject implements Delayed {
		long delay_nano;
		long start = System.nanoTime();


		MyDelayedObject( long delay_ms ) {
			this.delay_nano = TimeUnit.MILLISECONDS.toNanos( delay_ms );
		}


		@Override
		public long getDelay( TimeUnit unit ) {
			long passed = System.nanoTime() - start;
			return unit.convert( delay_nano - passed, TimeUnit.NANOSECONDS );
		}

		@Override
		public int compareTo( Delayed o ) {
			long d = getDelay( TimeUnit.NANOSECONDS );
			long other_d = o.getDelay( TimeUnit.NANOSECONDS );
			if ( other_d < d ) return 1;
			else if ( other_d == d ) return 0;
			else return -1;
		}
	}
}
