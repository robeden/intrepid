package com.starlight.intrepid;

import com.starlight.intrepid.driver.DataSink;
import com.starlight.intrepid.driver.DataSource;
import okio.Buffer;

import javax.annotation.Nonnull;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;


/**
 *
 */
public class OkioBufferData implements DataSink, DataSource {
	private final Buffer buffer;

	public OkioBufferData( Buffer buffer ) {
		this.buffer = buffer;
	}



	@Override public void put( int value ) {
		buffer.writeByte( value );
	}

	@Override public void putShort( short value ) {
		buffer.writeShort( value );
	}

	@Override public void putInt( int value ) {
		buffer.writeInt( value );
	}

	@Override public void putLong( long value ) {
		buffer.writeLong( value );
	}

	@Override public void put( byte[] b, int offset, int length ) {
		buffer.write( b, offset, length );
	}

	@Override public void put( ByteBuffer src ) {
		// See https://github.com/square/okio/issues/318
		// Hopefully this can be more efficient someday.
		byte[] data = new byte[ 10_000 ];
		for( int remaining = src.remaining(); remaining > 0; remaining = src.remaining() ) {
			int to_read = Math.min( remaining, data.length );
			src.get( data, 0, to_read );
			buffer.write( data, 0, to_read );
		}
	}

	@Nonnull @Override public OutputStream outputStream() {
		return buffer.outputStream();
	}



	@Nonnull @Override public String hex() {
		return buffer.snapshot().hex();
	}

	@Override public byte get() throws EOFException {
		return buffer.readByte();
	}

	@Override public short getShort() throws EOFException {
		return buffer.readShort();
	}

	@Override public int getInt() throws EOFException{
		return buffer.readInt();
	}

	@Override public long getLong() throws EOFException {
		return buffer.readLong();
	}

	@Override public void getFully( @Nonnull byte[] destination ) throws EOFException {
		buffer.readFully( destination );
	}

	@Nonnull @Override public InputStream inputStream() {
		return buffer.inputStream();
	}

	@Override public boolean request( long byte_count ) {
		return buffer.request( byte_count );
	}
}
