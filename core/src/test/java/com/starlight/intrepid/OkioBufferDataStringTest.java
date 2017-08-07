package com.starlight.intrepid;

import okio.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;


/**
 *
 */
@RunWith( Parameterized.class )
public class OkioBufferDataStringTest {
	@Parameterized.Parameters( name = "{0}" )
	public static List<TestArgs> args() {
		return Arrays.asList(
			new TestArgs(
				Arrays.asList( "It was the best of times, it was the worst of times." ),
				Charset.forName( "UTF-16" ) ),
			new TestArgs(
				Arrays.asList( "It was the best of times, it was the worst of times." ),
				Charset.forName( "UTF-8" ) ),

			new TestArgs(
				Arrays.asList( "A", "B", "C" ),
				Charset.forName( "UTF-16" ) ),
			new TestArgs(
				Arrays.asList( "A", "B", "C" ),
				Charset.forName( "UTF-8" ) )
		);
	}


	private final TestArgs args;

	private final Buffer buffer = new Buffer();
	private final OkioBufferData data = new OkioBufferData( buffer );


	public OkioBufferDataStringTest( TestArgs args ) {
		this.args = args;
	}



	@Test
	public void readWrite() throws CharacterCodingException {
		AtomicInteger total_written = new AtomicInteger( 0 );
		for( String s : args.strings ) {
			data.putString( s, args.charset.newEncoder(), total_written::addAndGet );
		}

		System.out.println( "Data: " + data.hex() );

		AtomicInteger total_read = new AtomicInteger( 0 );
		for( String s : args.strings ) {
			assertEquals( s, data.getString( args.charset.newDecoder(),
				total_read::addAndGet ) );
		}

		assertEquals( total_written.get(), total_read.get() );
	}


	private static class TestArgs {
		private final List<String> strings;
		private final Charset charset;

		TestArgs( List<String> strings, Charset charset ) {
			this.strings = strings;
			this.charset = charset;
		}



		@Override public String toString() {
			return "TestArgs{" +
				"strings=" + strings +
				", charset=" + charset +
				'}';
		}
	}
}
