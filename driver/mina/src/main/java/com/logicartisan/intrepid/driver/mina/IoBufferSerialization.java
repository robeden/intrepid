package com.logicartisan.intrepid.driver.mina;

import org.apache.mina.core.buffer.IoBuffer;

import java.io.*;


/**
 * This class works around issues with IoBuffer get/putObject
 */
class IoBufferSerialization {
	private static final ThreadLocal<IoBufferSerializationHelper> serializers =
		new ThreadLocal<IoBufferSerializationHelper>();


	static void putObject( Object object, IoBuffer buffer ) throws IOException {
		ObjectOutputStream out = new ObjectOutputStream( buffer.asOutputStream() );
		out.writeUnshared( object );
		out.close();

//		buffer.putObject( object );
	}


	static Object getObject( IoBuffer buffer ) throws IOException, ClassNotFoundException {
//		return buffer.getObject();
		try ( ObjectInputStream in = new ObjectInputStream( buffer.asInputStream() ) ) {
			return in.readObject();
		}
	}


	private static class IoBufferSerializationHelper {
		private final BufferOutputStreamWrapper output_stream_wrapper =
			new BufferOutputStreamWrapper();
		private final ObjectOutputStream output_stream;

		private final BufferInputStreamWrapper input_stream_wrapper =
			new BufferInputStreamWrapper();
		private final ObjectInputStream input_stream;

		IoBufferSerializationHelper() throws IOException {
			output_stream = new ObjectOutputStream( output_stream_wrapper );
			input_stream = new ObjectInputStream( input_stream_wrapper );
		}
	}


	private static class BufferOutputStreamWrapper extends OutputStream {
		private IoBuffer buffer;

		void setBuffer( IoBuffer buffer ) {
			this.buffer = buffer;
		}


		@Override
		public void write( byte[] b, int off, int len ) {
			if ( buffer == null ) return;
			buffer.put( b, off, len );
		}

		@Override
		public void write( int b ) {
			if ( buffer == null ) return;
			buffer.put( ( byte ) b );
		}
	}


	private static class BufferInputStreamWrapper extends InputStream {
		private IoBuffer buffer;

		void setBuffer( IoBuffer buffer ) {
			this.buffer = buffer;
		}

		@Override
		public int read() throws IOException {
			if ( !buffer.hasRemaining() ) return -1;

			return buffer.get();
		}

		@Override
		public int read( byte[] bytes ) throws IOException {
			return read( bytes, 0, bytes.length );
		}

		@Override
		public int read( byte[] bytes, int offset, int length ) throws IOException {
			int to_read = Math.min( length, buffer.remaining() );

			buffer.get( bytes, offset, to_read );
			return to_read;
		}
	}
}
