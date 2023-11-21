package com.starlight.intrepid.driver;

import javax.annotation.Nonnull;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.util.function.IntConsumer;


/**
 *
 */
class DefaultTrackingDataSource implements DataSource.Tracking {
	private final DataSource delegate;


	private long read = 0;


	DefaultTrackingDataSource( DataSource delegate ) {
		this.delegate = delegate;
	}



	@Override
	public long bytesRead() {
		return read;
	}



	@Override
	public @Nonnull String hex() {
		return delegate.hex();
	}

	@Override
	public byte get() throws EOFException {
		byte value = delegate.get();
		read++;
		return value;
	}

	@Override
	public short getShort() throws EOFException {
		short value = delegate.getShort();
		read += 2;
		return value;
	}

	@Override
	public int getInt() throws EOFException {
		int value = delegate.getInt();
		read += 4;
		return value;
	}

	@Override
	public long getLong() throws EOFException {
		long value = delegate.getLong();
		read += 8;
		return value;
	}

	@Override
	public void getFully( @Nonnull byte[] destination ) throws EOFException {
		delegate.getFully( destination );
		read += destination.length;
	}

	@Override
	public @Nonnull String getString( @Nonnull CharsetDecoder decoder,
		@Nonnull IntConsumer byte_count_consumer ) throws CharacterCodingException {

		return delegate.getString( decoder, count -> {
			read += count;
			byte_count_consumer.accept( count );
		} );
	}

	@Override
	public @Nonnull String getString( @Nonnull CharsetDecoder decoder, int length )
		throws CharacterCodingException, EOFException {

		String value = delegate.getString( decoder, length );
		read += length;
		return value;
	}

	@Override
	public @Nonnull InputStream inputStream() {
		return new FilterInputStream( delegate.inputStream() ) {
			@Override
			public int read() throws IOException {
				int value = super.read();
				read++;
				return value;
			}

			@Override
			public int read( byte[] b ) throws IOException {
				int count = super.read( b );
				read += count;
				return count;
			}

			@Override
			public int read( byte[] b, int off, int len ) throws IOException {
				int count = super.read( b, off, len );
				read += count;
				return count;
			}

			@Override
			public long skip( long n ) throws IOException {
				long skipped = super.skip( n );
				read += skipped;
				return skipped;
			}

			@Override public boolean markSupported() {
				return false;
			}
		};
	}



	@Override public boolean request( long byte_count ) {
		return delegate.request( byte_count );
	}



	@Override public Tracking trackRead() {
		return delegate.trackRead();
	}
}
