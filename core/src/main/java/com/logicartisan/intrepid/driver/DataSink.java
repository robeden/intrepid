package com.logicartisan.intrepid.driver;

import javax.annotation.Nonnull;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.util.function.IntConsumer;


/**
 *
 */
public interface DataSink {
	void put( int value );
	void putShort( short value );
	void putInt( int value );
	void putLong( long value );

	void put( byte[] b, int offset, int length );


	/**
	 * Write a null-terminated string.
	 */
	void putString( @Nonnull String value, @Nonnull CharsetEncoder encoder,
		@Nonnull IntConsumer byte_count_consumer ) throws CharacterCodingException;


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
