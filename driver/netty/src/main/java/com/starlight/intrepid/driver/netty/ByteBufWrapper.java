package com.starlight.intrepid.driver.netty;

import com.starlight.intrepid.driver.DataSink;
import com.starlight.intrepid.driver.DataSource;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import javax.annotation.Nonnull;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.function.IntConsumer;

class ByteBufWrapper implements DataSource, DataSink {
    protected final ByteBuf delegate;


    ByteBufWrapper(ByteBuf delegate) {
        this.delegate = delegate;
    }

    @Override
    public void maybeMarkRead() {
        delegate.markReaderIndex();
    }

    @Override
    public void maybeResetRead() {
        delegate.resetReaderIndex();
    }

    @Override
    public @Nonnull String hex() {
        return "<not available>";        // TODO?
    }

    @Override
    public byte get() {
        return delegate.readByte();
    }

    @Override
    public short getShort() {
        return delegate.readShort();
    }

    @Override
    public int getInt() {
        return delegate.readInt();
    }

    @Override
    public long getLong() {
        return delegate.readLong();
    }

    @Override
    public void getFully(@Nonnull byte[] destination) throws EOFException {
        try {
            delegate.readBytes(destination);
        } catch (IndexOutOfBoundsException | BufferUnderflowException ex) {
            EOFException eof = new EOFException();
            eof.initCause(ex);
            throw eof;
        }
    }

    @Override
    public @Nonnull String getString(@Nonnull Charset charset,
                                     @Nonnull CharsetDecoder charset_decoder,
                                     @Nonnull IntConsumer byte_count_consumer) throws CharacterCodingException {

        int position_before = delegate.readerIndex();
        CharSequence value = delegate.readCharSequence(delegate.readableBytes(), charset);
        byte_count_consumer.accept(delegate.readerIndex() - position_before);
        return value.toString();
    }

    @Override
    public @Nonnull String getString(@Nonnull Charset charset,
                                     @Nonnull CharsetDecoder decoder,
                                     int length)
        throws CharacterCodingException, EOFException {

        return delegate.readCharSequence(length, charset).toString();
    }

    @Override
    public @Nonnull InputStream inputStream() {
        return new ByteBufInputStream(delegate);
    }

    @Override
    public boolean request(long byte_count) {
        return delegate.readableBytes() >= byte_count;
    }

    @Override
    public void consume(int bytes) throws EOFException {
        try {
            delegate.readerIndex(delegate.readerIndex() + bytes);
        } catch (IndexOutOfBoundsException ex) {
            EOFException to_throw = new EOFException();
            to_throw.initCause(ex);
            throw to_throw;
        }
    }



		@Override
		public void put( int value ) {
            delegate.writeByte( value );
		}

		@Override
		public void putShort( short value ) {
			delegate.writeShort( value );
		}

		@Override
		public void putInt( int value ) {
			delegate.writeInt( value );
		}

		@Override
		public void putLong( long value ) {
			delegate.writeLong( value );
		}

		@Override
		public void put( byte[] b, int offset, int length ) {
			delegate.writeBytes( b, offset, length );
		}

		@Override
		public void put( ByteBuffer src ) {
			delegate.writeBytes( src );
		}



		@Override
		public void putString(@Nonnull String value,
                              @Nonnull CharsetEncoder encoder,
                              @Nonnull IntConsumer byte_count_consumer )
			throws CharacterCodingException {

			int position_before = delegate.writerIndex();
			delegate.writeCharSequence( value, encoder.charset() );

			// NULL terminate string!!
			// NOTE: Since UTF-16 is 2 bytes, need to insert 2 bytes properly terminate
            boolean utf16 = encoder.charset().name().startsWith( "UTF-16" );
            if ( utf16 ) {
	            delegate.writeShort( ( short ) 0x00 );
            }
            else {
            	delegate.writeByte( 0 );
            }

			byte_count_consumer.accept( delegate.writerIndex() - position_before );
		}

		@Override
		public void prepareForData( int length ) {
			delegate.ensureWritable( length );
		}

		@Override
		public @Nonnull OutputStream outputStream() {
            return new ByteBufOutputStream(delegate);
		}



    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public DataSource.Tracking trackRead() {
        return new TrackingByteBufWrapper(delegate);
    }

    @Override
    public DataSink.Tracking trackWritten() {
        return new TrackingByteBufWrapper(delegate);
    }

    private static class TrackingByteBufWrapper extends ByteBufWrapper
        implements DataSource.Tracking, DataSink.Tracking {

        private final int read_start;
        private final int write_start;

        TrackingByteBufWrapper(ByteBuf delegate) {
            super(delegate);

            read_start = delegate.readerIndex();
            write_start = delegate.writerIndex();
        }

        @Override
        public long bytesRead() {
            return delegate.readerIndex() - read_start;
        }

        @Override
        public int bytesWritten() {
            return delegate.writerIndex() - write_start;
        }
    }
}
