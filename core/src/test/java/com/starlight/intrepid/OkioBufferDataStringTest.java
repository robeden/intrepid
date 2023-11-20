package com.starlight.intrepid;

import okio.Buffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;


/**
 *
 */
public class OkioBufferDataStringTest {
	public static Stream<Arguments> args() {
		return Stream.of(
			arguments(
				List.of("It was the best of times, it was the worst of times."),
                StandardCharsets.UTF_16),
			arguments(
                List.of("It was the best of times, it was the worst of times."),
                StandardCharsets.UTF_8),

			arguments(
				Arrays.asList( "A", "B", "C" ),
                StandardCharsets.UTF_16),
			arguments(
				Arrays.asList( "A", "B", "C" ),
                StandardCharsets.UTF_8)
		);
	}


	private final Buffer buffer = new Buffer();
	private final OkioBufferData data = new OkioBufferData( buffer );



	@ParameterizedTest
	@MethodSource("args")
	public void readWrite(List<String> strings, Charset charset) throws CharacterCodingException {
		AtomicInteger total_written = new AtomicInteger( 0 );
		for( String s : strings ) {
			data.putString( s, charset.newEncoder(), total_written::addAndGet );
		}

		System.out.println( "Data: " + data.hex() );

		AtomicInteger total_read = new AtomicInteger( 0 );
		for( String s : strings ) {
			assertEquals( s, data.getString( charset.newDecoder(),
				total_read::addAndGet ) );
		}

		assertEquals( total_written.get(), total_read.get() );
	}
}
