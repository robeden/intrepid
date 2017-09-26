package com.starlight.intrepid;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class RingBufferTest {
    private static final int IRRELEVANT_WIN_SIZE = Integer.MAX_VALUE;

    // This uses a dump of data found in a real world test in which (we think) the ring buffer
    // didn't function correctly.
    // The file is in the format:
    //  >> #  = write entry to buffer
    //  << #  = remove entry from buffer
    //  Lines starting with '#' are comments
    @Test
    public void bigTestFile() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT_WIN_SIZE, 10 );


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
                    assertTrue( buffer.releaseAndResize( message_id, -1 ) );
                }
                ops++;
            }
        }

        System.out.println( "Completed " + ops + " ops");
    }


    @Test
    public void testSimple() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT_WIN_SIZE, 5 );

        assertEquals( 0, buffer.ringSize() );
        assertEquals( 100, buffer.tryAcquire( 100, 1, ( short ) 1 ) );
        assertEquals( 1, buffer.ringSize() );
        assertEquals( 100, buffer.tryAcquire( 100, 1, ( short ) 2 ) );
        assertEquals( 2, buffer.ringSize() );
        assertEquals( 100, buffer.tryAcquire( 100, 1, ( short ) 3 ) );
        assertEquals( 3, buffer.ringSize() );

        assertTrue( buffer.releaseAndResize( (short) 1, -1 ) );
        assertEquals( 2, buffer.ringSize() );
        assertTrue( buffer.releaseAndResize( (short) 2, -1 ) );
        assertEquals( 1, buffer.ringSize() );
        assertTrue( buffer.releaseAndResize( (short) 3, -1 ) );
        assertEquals( 0, buffer.ringSize() );
    }

    @Test
    public void testWrapping() throws Exception {
        VBCRxWindowSendControl.RingBuffer buffer =
            new VBCRxWindowSendControl.RingBuffer( IRRELEVANT_WIN_SIZE, 5 );

        assertEquals( 100, buffer.tryAcquire( 100, 1, ( short ) 1 ) );
        assertEquals( 100, buffer.tryAcquire( 100, 1, ( short ) 2 ) );
        assertEquals( 100, buffer.tryAcquire( 100, 1, ( short ) 3 ) );
        assertEquals( 100, buffer.tryAcquire( 100, 1, ( short ) 4 ) );
        assertEquals( 100, buffer.tryAcquire( 100, 1, ( short ) 5 ) );
        assertEquals( 5, buffer.ringSize() );   // full

        assertTrue( buffer.releaseAndResize( (short) 1, -1 ) );
        assertEquals( 4, buffer.ringSize() );

        assertEquals( 100, buffer.tryAcquire( 100, 1, ( short ) 6 ) );
        assertEquals( 5, buffer.ringSize() );   // full


        assertTrue( buffer.releaseAndResize( ( short ) 2, -1 ) );
        assertEquals( 4, buffer.ringSize() );
        assertTrue( buffer.releaseAndResize( ( short ) 3, -1 ) );
        assertEquals( 3, buffer.ringSize() );
        assertTrue( buffer.releaseAndResize( ( short ) 4, -1 ) );
        assertEquals( 2, buffer.ringSize() );
        assertTrue( buffer.releaseAndResize( ( short ) 5, -1 ) );
        assertEquals( 1, buffer.ringSize() );
        assertTrue( buffer.releaseAndResize( ( short ) 6, -1 ) );
        assertEquals( 0, buffer.ringSize() );
    }

}