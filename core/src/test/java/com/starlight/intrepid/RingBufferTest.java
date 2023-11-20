package com.starlight.intrepid;

import com.logicartisan.common.core.thread.SharedThreadPool;
import com.logicartisan.common.core.thread.ThreadKit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class RingBufferTest {
    private static final int IRRELEVANT = Integer.MAX_VALUE;

    // This uses a dump of data found in a real world test in which (we think) the ring buffer
    // didn't function correctly.
    // The file is in the format:
    //  >> #  = write entry to buffer
    //  << #  = remove entry from buffer
    //  Lines starting with '#' are comments
    @Test
    public void bigTestFile() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 10, IRRELEVANT );


        int ops = 0;
        try( BufferedReader in = new BufferedReader( new InputStreamReader(
            RingBufferTest.class.getResourceAsStream( "RingBufferSampleDump.txt" ) ) ) ) {

            String line;
            while( ( line = in.readLine() ) != null ) {
                if ( line.isEmpty() ) continue;
                if ( line.startsWith( "#" ) ) continue;

                short message_id = Short.parseShort( line.substring( 3 ) );   // Example: ">> 123"
                boolean add_to_buffer = line.startsWith( ">>" );

                if ( add_to_buffer ) {
                    buffer.tryAcquire( 1, 1, message_id );
                }
                else {
                    // HERE IS THE TEST
                    // This should always return true because all our data is valid
                    assertTrue(buffer.releaseAndResize( message_id, -1 ));
                }
                ops++;
            }
        }

        System.out.println( "Completed " + ops + " ops");
    }


    @Test
    public void testSimple() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 5, IRRELEVANT );

        assertEquals(0, buffer.ringSize());
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));
        assertEquals(1, buffer.ringSize());
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 2 ));
        assertEquals(2, buffer.ringSize());
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 3 ));
        assertEquals(3, buffer.ringSize());
        expectRingMessages( buffer, 1, 2, 3 );

        assertTrue(buffer.releaseAndResize( (short) 1, -1 ));
        assertEquals(2, buffer.ringSize());
        assertTrue(buffer.releaseAndResize( (short) 2, -1 ));
        assertEquals(1, buffer.ringSize());
        assertTrue(buffer.releaseAndResize( (short) 3, -1 ));
        assertEquals(0, buffer.ringSize());
        expectRingMessages( buffer );
    }

    @Test
    public void testWrapping() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 5, IRRELEVANT );

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 2 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 3 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 4 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 5 ));
        assertEquals(5, buffer.ringSize());   // full
        assertEquals(500, buffer.windowInUse());
        expectRingMessages( buffer, 1, 2, 3, 4, 5 );

        assertTrue(buffer.releaseAndResize( ( short ) 1, -1 ));
        assertEquals(4, buffer.ringSize());
        assertEquals(400, buffer.windowInUse());

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 6 ));
        assertEquals(5, buffer.ringSize());   // full
        assertEquals(500, buffer.windowInUse());
        expectRingMessages( buffer, 2, 3, 4, 5, 6 );


        assertTrue(buffer.releaseAndResize( ( short ) 2, -1 ));
        assertEquals(4, buffer.ringSize());
        assertTrue(buffer.releaseAndResize( ( short ) 3, -1 ));
        assertEquals(3, buffer.ringSize());
        assertTrue(buffer.releaseAndResize( ( short ) 4, -1 ));
        assertEquals(2, buffer.ringSize());
        assertTrue(buffer.releaseAndResize( ( short ) 5, -1 ));
        assertEquals(1, buffer.ringSize());
        assertTrue(buffer.releaseAndResize( ( short ) 6, -1 ));
        assertEquals(0, buffer.ringSize());
        expectRingMessages( buffer );
    }

    @Test
    public void testSingleRelease() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 5, IRRELEVANT );

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));
        assertEquals(1, buffer.ringSize());
        assertEquals(100, buffer.windowInUse());

        assertTrue(buffer.releaseAndResize( ( short ) 1, -1 ));
        assertEquals(0, buffer.ringSize());
        assertEquals(0, buffer.windowInUse());
    }

    @Test
    public void testDoubleRelease() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 5, IRRELEVANT );

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 2 ));
        assertEquals(200, buffer.windowInUse());
        assertEquals(2, buffer.ringSize());

        assertTrue(buffer.releaseAndResize( ( short ) 2, -1 ));
        assertEquals(0, buffer.ringSize());
        assertEquals(0, buffer.windowInUse());
    }


    @Test
	public void testGrow_noGaps() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 5, IRRELEVANT );

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 2 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 3 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 4 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 5 ));
        assertEquals(5, buffer.ringSize());   // full


        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 6 ));
        expectRingMessages( buffer, 1, 2, 3, 4, 5, 6 );
    }

    @Test
	public void testGrow_initialGap() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 5, IRRELEVANT );

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 2 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 3 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 4 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 5 ));
        assertEquals(5, buffer.ringSize());   // full
        assertTrue(buffer.releaseAndResize( ( short ) 2, -1 ));
	    expectRingMessages( buffer, 3, 4, 5 );
        assertEquals(300, buffer.windowInUse());


        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 6 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 7 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 8 ));
        expectRingMessages( buffer, 3, 4, 5, 6, 7, 8 );
        assertEquals(600, buffer.windowInUse());
    }



    @Test
    public void testUnknownMessageRelease_empty() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 5, IRRELEVANT );

        assertFalse(buffer.releaseAndResize( ( short ) 999, -1 ));
        assertEquals(0, buffer.ringSize());
        assertEquals(0, buffer.windowInUse());
    }

    @Test
    public void testUnknownMessageRelease_nonEmpty() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 5, IRRELEVANT );

        buffer.tryAcquire( 100, 1, ( short ) 1 );
        buffer.tryAcquire( 100, 1, ( short ) 2 );
        buffer.tryAcquire( 100, 1, ( short ) 3 );

        assertFalse(buffer.releaseAndResize( ( short ) 999, -1 ));
        assertEquals(0, buffer.ringSize());
        assertEquals(0, buffer.windowInUse());
    }



    @Test
    public void testPartialAcquire() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( 150, 5, IRRELEVANT );    // 150 space

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));      // -100
        assertEquals(50, buffer.tryAcquire( 100, 1, ( short ) 1 ));      // -50
    }



    @Timeout(5)
    @Test
    public void testWaitForWindowSpace() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( 100, 5, IRRELEVANT );

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));

	    SharedThreadPool.INSTANCE.schedule(
	    	() -> buffer.releaseAndResize( ( short ) 1, -1 ), 2, TimeUnit.SECONDS );

        // This will block
	    long start = System.nanoTime();
	    assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 2 ));
	    long end = System.nanoTime();

	    assertTrue(( end - start ) > TimeUnit.SECONDS.toNanos( 1 ));
    }

    @Timeout(5)
    @Test
    public void testWaitForWindowSpace_multiRelease() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( 200, 5, IRRELEVANT );

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));
        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 2 ));

	    SharedThreadPool.INSTANCE.schedule(
	    	() -> {
			    //noinspection ResultOfMethodCallIgnored
			    buffer.releaseAndResize( ( short ) 1, -1 );
	    		ThreadKit.sleep( 1000 );
			    //noinspection ResultOfMethodCallIgnored
	    		buffer.releaseAndResize( ( short ) 2, -1 );
		    }, 2, TimeUnit.SECONDS );

        // This will block
	    long start = System.nanoTime();
	    assertEquals(200, buffer.tryAcquire( 200, 200, ( short ) 2 ));
	    long end = System.nanoTime();

	    assertTrue(( end - start ) > TimeUnit.SECONDS.toNanos( 2 ));
    }

    @Timeout(5)
    @Test
    public void testWaitForWindowSpace_resize() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( 100, 5, IRRELEVANT );

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));

	    SharedThreadPool.INSTANCE.schedule(
	    	() -> buffer.resize( 200 ), 2, TimeUnit.SECONDS );

        // This will block
	    long start = System.nanoTime();
	    assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 2 ));
	    long end = System.nanoTime();

	    assertTrue(( end - start ) > TimeUnit.SECONDS.toNanos( 1 ));
    }

    @Timeout(2)
    @Test
    public void testWindowChangeOnRelease() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( 100, 5, IRRELEVANT );

        assertEquals(100, buffer.tryAcquire( 100, 1, ( short ) 1 ));
        assertEquals(100, buffer.windowInUse());

        assertTrue(buffer.releaseAndResize( ( short ) 1, 1000 ));
        assertEquals(0, buffer.windowInUse());

        assertEquals(1000, buffer.tryAcquire( 1000, 1, ( short ) 1 ));
        assertEquals(1000, buffer.windowInUse());
    }



    @Timeout(5)
    @Test
    public void testWaitForRingSpace() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT, 3, 3 ); // no growth

        assertEquals(1, buffer.tryAcquire( 1, 1, ( short ) 1 ));
        assertEquals(1, buffer.tryAcquire( 1, 1, ( short ) 2 ));
        assertEquals(1, buffer.tryAcquire( 1, 1, ( short ) 3 ));

	    SharedThreadPool.INSTANCE.schedule(
	    	() -> buffer.releaseAndResize( ( short ) 1, -1 ), 2, TimeUnit.SECONDS );

        // This will block
	    long start = System.nanoTime();
	    assertEquals(1, buffer.tryAcquire( 1, 1, ( short ) 4 ));
	    long end = System.nanoTime();

	    assertTrue(( end - start ) > TimeUnit.SECONDS.toNanos( 1 ));
    }






    private void expectRingMessages( VBCRxWindowSendControl.RingBuffer ring,
	    int... message_ids ) {

	    AtomicInteger touch_count = new AtomicInteger( 0 );
	    int total = ring.forEachElement( ( id, size ) -> {
	    	assertEquals(message_ids[ touch_count.get() ], id, "Message " + touch_count.get() + " didn't match");
	    	touch_count.incrementAndGet();
	    	return true;
	    } );
	    assertEquals(message_ids.length, total);
	    assertEquals(message_ids.length, touch_count.get());
    }
}