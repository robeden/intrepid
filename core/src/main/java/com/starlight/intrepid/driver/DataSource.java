package com.starlight.intrepid.driver;

import javax.annotation.Nonnull;
import java.io.EOFException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
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
	@Nonnull String getString( @Nonnull CharsetDecoder decoder,
		@Nonnull IntConsumer byte_count_consumer ) throws CharacterCodingException;

	/**
	 * Read a string of a specified length. In this case the string is not null-terminated.
	 */
	@Nonnull String getString( @Nonnull CharsetDecoder decoder, int length )
		throws CharacterCodingException, EOFException;

	@Nonnull InputStream inputStream();

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
