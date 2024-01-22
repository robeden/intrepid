package com.starlight.intrepid;

import okio.Buffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.EOFException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
                StandardCharsets.UTF_8),

			arguments(
				Arrays.asList( "A", "B", "C" ),
                StandardCharsets.UTF_8)
		);
	}


	private final Buffer buffer = new Buffer();
	private final OkioBufferData data = new OkioBufferData( buffer );



	@ParameterizedTest
	@MethodSource("args")
	public void readWrite(List<String> strings) throws CharacterCodingException, EOFException {
		AtomicInteger total_written = new AtomicInteger( 0 );
		List<Integer> lengths = new ArrayList<>(strings.size());
		for( String s : strings ) {
			int written = data.putUtf8String(s);
			lengths.add(written);
			total_written.addAndGet(written);
		}

		System.out.println( "Data: " + data.hex() );

		AtomicInteger total_read = new AtomicInteger( 0 );
		for( String s : strings ) {
			int length = lengths.remove(0);
			assertEquals( s, data.getUtf8String( length ));
			total_read.addAndGet( length );
		}

		assertEquals( total_written.get(), total_read.get() );
	}
}
