package com.starlight.intrepid.driver;

import javax.annotation.Nonnull;
import java.io.EOFException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.function.IntConsumer;


/**
 *
 */
public interface DataSource {
	/**
	 * Return a hex dump of the available data. Doing so does not consume the data so it
	 * will still be available for reading.
	 */
	@Nonnull String hex();

	/**
	 * Some data source implementations rely on mark/reset semantics for identifying that
	 * more data is needed to read a message and some don't. For ones that do which need
	 * to keep their position at the beginning of the message (e.g., Netty), this should
	 * be implemented to mark the reader position so that it can be later reset if there
	 * isn't enough data to read the full message. For implementations that don't work
	 * that way (e.g., MINA), this can be ignored.
	 *
	 * @see #maybeResetRead()
	 */
	default void maybeMarkRead() {}

	/**
	 * @see #maybeMarkRead()
	 */
	default void maybeResetRead() {}


	byte get() throws EOFException;
	short getShort() throws EOFException;
	int getInt() throws EOFException;
	long getLong() throws EOFException;

	void getFully( @Nonnull byte[] destination ) throws EOFException;


	/**
	 * Read a null-terminated string. This operation should also consume the null marker.
	 *
	 * @param byte_count_consumer       A consumer that will be called to indicate the
	 *                                  number of bytes consumed during the read. The
	 *                                  consumer may be called more than once during the
	 *                                  operation but will no longer be called once the
	 *                                  method returns.
	 */
	@Nonnull String getString( @Nonnull Charset charset, @Nonnull CharsetDecoder charset_decoder,
		@Nonnull IntConsumer byte_count_consumer ) throws CharacterCodingException;

	/**
	 * Read a string of a specified length. In this case the string is not null-terminated.
	 */
	@Nonnull String getString( @Nonnull Charset charset, @Nonnull CharsetDecoder charset_decoder,
							   int length )
		throws CharacterCodingException, EOFException;


	/**
	 * Consume the given number of bytes from the buffer.
	 */
	default void consume(int bytes) throws EOFException {
		for( int i = 0; i < bytes; i++ ) {
			get();
		}
	}

	@Nonnull
	InputStream inputStream();

	/**
	 * Returns true when the buffer contains at least byteCount bytes. Returns false if
	 * the source is exhausted before the requested bytes can be read.
	 */
	boolean request( long byte_count );


	default Tracking trackRead() {
		return new DefaultTrackingDataSource( this );
	}



	interface Tracking extends DataSource {
		/**
		 * The number of bytes read since the creation of this Tracking object.
		 */
		long bytesRead();
	}
}
