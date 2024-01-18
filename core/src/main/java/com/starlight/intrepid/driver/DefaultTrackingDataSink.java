package com.starlight.intrepid.driver;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;


/**
 *
 */
public class DefaultTrackingDataSink implements DataSink.Tracking {
	private final DataSink delegate;


	private int written = 0;


	DefaultTrackingDataSink( DataSink delegate ) {
		this.delegate = delegate;
	}


	@Override
	public int bytesWritten() {
		return written;
	}



	@Override
	public void put( int value ) {
		delegate.put( value );
		written++;
	}

	@Override
	public void putShort( short value ) {
		delegate.putShort( value );
		written += 2;
	}

	@Override
	public void putInt( int value ) {
		delegate.putInt( value );
		written += 4;
	}

	@Override
	public void putLong( long value ) {
		delegate.putLong( value );
		written += 8;
	}

	@Override
	public void put( byte[] b, int offset, int length ) {
		delegate.put( b, offset, length );
		written += length;
	}

	@Override public void put( ByteBuffer src ) {
		int remaining = src.remaining();
		delegate.put( src );
		written += remaining;
	}


	@Override
	public int putUtf8String(@Nonnull String value) throws CharacterCodingException {
		int count = delegate.putUtf8String(value);
		written += count;
		return count;
	}

	@Override
	public @Nonnull OutputStream outputStream() {
		return new OutputStream() {
			@Override
			public void write( int b ) throws IOException {
				delegate.put( b );
				written++;
			}

			@Override
			public void write( byte[] b ) throws IOException {
				delegate.put( b, 0, b.length );
				written += b.length;
			}

			@Override
			public void write( byte[] b, int off, int len ) throws IOException {
				delegate.put( b, off, len );
				written += len;
			}
		};
	}
}
