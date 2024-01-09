package com.starlight.intrepid;

import com.starlight.intrepid.driver.DataSink;
import com.starlight.intrepid.driver.DataSource;
import okio.*;

import javax.annotation.Nonnull;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.function.IntConsumer;


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



	@Override public void putString( @Nonnull String value,
		@Nonnull CharsetEncoder encoder, @Nonnull IntConsumer byte_count_consumer )
		throws CharacterCodingException {

		BufferedSink tracking_sink = Okio.buffer( new ForwardingSink( buffer ) {
			@Override
			public void write( Buffer source, long byteCount )
				throws IOException {

				super.write( source, byteCount );
				while ( byteCount > 0 ) {
					if ( byteCount > Integer.MAX_VALUE ) {
						byte_count_consumer.accept( Integer.MAX_VALUE );
						byteCount -= Integer.MAX_VALUE;
					}
					else {
						byte_count_consumer.accept( ( int ) byteCount );
						byteCount = 0;
					}
				}
			}
		} );


		try {
			tracking_sink.writeString( value, encoder.charset() );

            boolean utf16 = encoder.charset().name().startsWith( "UTF-16" );
            if ( utf16 ) {
	            tracking_sink.writeShort( ( short ) 0x00 );
            }
            else {
            	tracking_sink.writeByte( 0 );
            }

			tracking_sink.flush();
		}
		catch ( IOException e ) {
			// Shouldn't be possible
			CharacterCodingException ex = new CharacterCodingException();
			ex.initCause( e );
			throw ex;
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

	@Override public @Nonnull String getString(@Nonnull Charset charset, @Nonnull CharsetDecoder decoder,
											   @Nonnull IntConsumer byte_count_consumer ) throws CharacterCodingException {

		// Logic (mostly) from MINA
        boolean utf16 = decoder.charset().name().startsWith( "UTF-16" );
        long index;
        try {
	        if ( !utf16 ) {
		        index = buffer.indexOf( ( byte ) 0x0 );
	        }
	        else {
		        index = buffer.indexOf( ByteString.of( ( byte ) 0x0, ( byte ) 0x0 ) );
	        }

	        if ( index > Integer.MAX_VALUE ) {
	        	throw new CharacterCodingException();
	        }

			String string = buffer.readString( index, decoder.charset() );
	        byte_count_consumer.accept( ( int ) index );

	        if ( !utf16 ) {
	        	buffer.skip( 1 );
	        	byte_count_consumer.accept( 1 );
	        }
	        else {
	        	buffer.skip( 2 );
	        	byte_count_consumer.accept( 2 );
	        }

	        return string;
		}
		catch ( IOException e ) {
			// Shouldn't be possible
			CharacterCodingException ex = new CharacterCodingException();
			ex.initCause( e );
			throw ex;
		}
	}

	@Override
	public @Nonnull String getString( @Nonnull Charset charset, @Nonnull CharsetDecoder decoder,
									  int length )
		throws CharacterCodingException, EOFException {

		return buffer.readString( length, decoder.charset() );
	}

	@Nonnull @Override public InputStream inputStream() {
		return buffer.inputStream();
	}

	@Override public boolean request( long byte_count ) {
		return buffer.request( byte_count );
	}
}
