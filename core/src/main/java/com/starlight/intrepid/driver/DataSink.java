package com.starlight.intrepid.driver;

import javax.annotation.Nonnull;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;


/**
 *
 */
public interface DataSink {
	void put( int value );
	void putShort( short value );
	void putInt( int value );
	void putLong( long value );

	void put( byte[] b, int offset, int length );
	void put( ByteBuffer src );


	/**
	 * Write a string (just the string value without null-termination).
	 *
	 * @return the number of bytes encoded
	 */
	default int putUtf8String(@Nonnull String value) throws CharacterCodingException {
		byte[] str_data = value.getBytes(StandardCharsets.UTF_8);
		put(str_data, 0, str_data.length);
		return str_data.length;
	}


	/**
	 * Announces to the sink that the given amount of data is about to be provided to it.
	 * This can optionally do things like size internal buffers.
	 */
	default void prepareForData( int length ) {}

	@Nonnull OutputStream outputStream();


	default Tracking trackWritten() {
		return new DefaultTrackingDataSink( this );
	}


	interface Tracking extends DataSink {
		/**
		 * The number of written read since the creation of this Tracking object.
		 */
		int bytesWritten();
	}
}
