package com.starlight.intrepid;

import com.jakewharton.byteunits.BinaryByteUnit;
import com.starlight.intrepid.exception.ChannelRejectedException;
import com.starlight.intrepid.message.IMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.LongStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/**
 *
 */
@RunWith( Parameterized.class )
public class LargeTransfersTest {
	private static final NumberFormat FORMATTER = NumberFormat.getNumberInstance();


	@Parameterized.Parameters( name="{0}" )
	public static List<Args> args() throws NoSuchAlgorithmException {
		List<Args> to_return = new ArrayList<>();

		long[] sizes = {
			             1,
			            10,
			           100,
			         1_000,
			        10_000,
			       100_000,
			     1_000_000,
			    10_000_000,
			   100_000_000,
//			 1_000_000_000,
//	    	10_000_000_000L
		};
		if ( System.getProperty( "intrepid.test.skip_slow" ) != null ) {
			sizes = LongStream.of( sizes )
				.filter( s -> s <= 1_000_000 )
				.toArray();
		}

		int[] thread_counts = {
			1,
			Runtime.getRuntime().availableProcessors() - 1,
			( Runtime.getRuntime().availableProcessors() - 1 ) * 2
		};

		MessageDigest[] digests = {
			null,                                   // fastest
			MessageDigest.getInstance( "MD5" ),
			MessageDigest.getInstance( "SHA-256" ), // slowest
		};

		int[] buffer_sizes = { 100_000, 1000, 10 };

		for ( int thread_count : thread_counts ) {
			for( MessageDigest digest : digests ) {
				for ( long size : sizes ) {
					for( int buffer_size : buffer_sizes ) {
						to_return.add(
							new Args( size, thread_count, digest, buffer_size ) );
					}
				}
			}
		}

		return to_return;
	}

	private final Args args;

	private byte[] data_block;
	private @Nullable byte[] checksum;

	private Intrepid client_instance = null;
	private Intrepid server_instance = null;


	public LargeTransfersTest( Args args ) {
		this.args = args;
	}


	@Before
	public void setUp() throws Exception {
		IntrepidTesting.setInterInstanceBridgeDisabled( true );

		// Data that will be sent (repeated as necessary)
		data_block = new byte[ args.buffer_size ];
		new Random().nextBytes( data_block );

		// Determine the data checksum if a digest will be used
		if ( args.digest != null ) {
			MessageDigest digest = ( MessageDigest ) args.digest.clone();

			long remaining_bytes = args.data_size;
			while( remaining_bytes > 0 ) {
				int update_size = ( int ) Math.min( remaining_bytes, data_block.length );
				digest.update( data_block, 0, update_size );
				remaining_bytes -= update_size;
			}
			checksum = digest.digest();
		}
	}

	@After
	public void tearDown() throws Exception {
		// Re-enable
		IntrepidTesting.setInterInstanceBridgeDisabled( false );

		if ( client_instance != null ) client_instance.close();
		if ( server_instance != null ) server_instance.close();
	}



	@Test( timeout = 600000 )   // 10 min
	public void virtualByteChannel() throws Exception {
		CountDownLatch reader_latch = new CountDownLatch( args.threads );

		List<String> read_error_list = Collections.synchronizedList( new ArrayList<>() );
		Consumer<String> error_consumer = message -> {
			read_error_list.add( message );
			reader_latch.countDown();
		};

		List<Double> read_bps_list = Collections.synchronizedList( new ArrayList<>() );
		Consumer<Double> bps_consumer = bps -> {
			read_bps_list.add( bps );
			reader_latch.countDown();
		};


		CountDownLatch writer_latch = new CountDownLatch( args.threads );
		List<Double> write_bps_list = Collections.synchronizedList( new ArrayList<>() );
		List<String> write_error_list = Collections.synchronizedList( new ArrayList<>() );


		final long total_data_to_write = args.data_size * args.threads;

		final AtomicLong total_read = new AtomicLong( 0 );
		server_instance = Intrepid.create(
			new IntrepidSetup()
				.vmidHint( "server" )
				.openServer()
				.performanceListener( new PerformanceListener() {
					@Override public void messageReceived( VMID source_vmid,
						IMessage message ) {

//						System.out.println( "Server received: " + message );
					}
				} )
				.channelAcceptor(
					new TestAcceptor( args.digest, error_consumer,
						bps_consumer, total_read ) ) );
		Integer server_port = server_instance.getServerPort();
		assertNotNull( server_port );

		client_instance = Intrepid.create( new IntrepidSetup().vmidHint( "client" ) );

		// Connect to the server
		VMID server_vmid = client_instance.connect( InetAddress.getByName( "127.0.0.1" ),
			server_port.intValue(), null, null );
		assertNotNull( server_vmid );

		AtomicLong total_data_written = new AtomicLong( 0 );
		Timer timer = new Timer( "Progress printer", true );
		TimerTask write_task =
			createProgressTask( "Write", total_data_to_write, total_data_written::get );
		timer.scheduleAtFixedRate( write_task, 10000, 5000 );

		TimerTask read_task =
			createProgressTask( "Read", total_data_to_write, total_read::get );
		timer.scheduleAtFixedRate( read_task, 10000, 5000 );

		for( int i = 0; i < args.threads; i++ ) {
			new Thread( () -> {
				ByteBuffer write_buffer = ByteBuffer.wrap( data_block );

				long start = System.nanoTime();
				try ( ByteChannel channel =
					client_instance.createChannel( server_vmid, null ) ) {

					long remaining_bytes = args.data_size;
					while ( remaining_bytes > 0 ) {
						write_buffer
							.clear(); // NOTE: doesn't erase, resets for read, 'cuz... NIO


						if ( remaining_bytes < write_buffer.remaining() ) {
							write_buffer.limit( ( int ) remaining_bytes );
						}

						while ( write_buffer.hasRemaining() ) {
							int written = channel.write( write_buffer );
							total_data_written.addAndGet( written );
							remaining_bytes -= written;
						}
					}

					long end = System.nanoTime();

					double write_bps = ( ( double ) args.data_size / ( double ) ( end - start ) ) *
						TimeUnit.SECONDS.toNanos( 1 );
					write_bps_list.add( write_bps );
				}
				catch( Exception ex ) {
					write_error_list.add( ex.toString() );
					ex.printStackTrace();
				}
				finally {
					writer_latch.countDown();
				}

			}, "Writer " + i ).start();
		}


		writer_latch.await();
		reader_latch.await();

		read_task.cancel();
		write_task.cancel();

		assertTrue( write_error_list.toString(), write_error_list.isEmpty() );
		assertTrue( read_error_list.toString(), read_error_list.isEmpty() );

		DoubleSummaryStatistics write_stats = write_bps_list.stream()
			.mapToDouble( Double::doubleValue )
			.summaryStatistics();

		DoubleSummaryStatistics read_stats = read_bps_list.stream()
			.mapToDouble( Double::doubleValue )
			.summaryStatistics();

		System.out.println( args + "  Write: " +
			stats( write_stats ) + "   Read: " + stats( read_stats ) );
	}

	private String stats( DoubleSummaryStatistics stats ) {
		if ( stats.getCount() == 1 ) {
			return BinaryByteUnit.format( Math.round( stats.getAverage() ) ) + "/s";
		}
		else {
			double range = Math.max(
				stats.getMax() - stats.getAverage(),
				stats.getAverage() - stats.getMin() );
			return BinaryByteUnit.format( Math.round( stats.getAverage() ) ) + "/s ±" +
				BinaryByteUnit.format( Math.round( range ) ) + "/s";
		}
	}


	public class TestAcceptor implements ChannelAcceptor {
		private final MessageDigest digest;
		private final Consumer<String> error_message_consumer;
		private final Consumer<Double> bps_consumer;
		private final AtomicLong total_read;


		TestAcceptor( @Nullable MessageDigest digest,
			Consumer<String> error_message_consumer, Consumer<Double> bps_consumer,
			AtomicLong total_read ) {

			this.digest = digest;
			this.error_message_consumer = error_message_consumer;
			this.bps_consumer = bps_consumer;
			this.total_read = total_read;
		}

		@Override
		public void newChannel( ByteChannel channel, VMID source_vmid,
			Serializable attachment ) throws ChannelRejectedException {

			try {
				new ChannelReadThread( channel,
					digest == null ? null : ( MessageDigest ) digest.clone(),
					error_message_consumer, bps_consumer, total_read ).start();
			}
			catch ( CloneNotSupportedException e ) {
				error_message_consumer.accept( e.toString() );
			}
		}
	}


	public class ChannelReadThread extends Thread {
		private final ByteChannel channel;
		private final MessageDigest digest;
		private final Consumer<String> error_message_consumer;
		private final Consumer<Double> bps_consumer;
		private final AtomicLong total_read;

		private final byte[] read_buffer_array = new byte[ 256_000 ];
		private final ByteBuffer read_buffer = ByteBuffer.wrap( read_buffer_array );

		ChannelReadThread( ByteChannel channel, @Nullable MessageDigest digest,
			Consumer<String> error_message_consumer, Consumer<Double> bps_consumer,
			AtomicLong total_read ) {

			super( "ChannelReadThread: " + channel );

			this.channel = channel;
			this.digest = digest;
			this.error_message_consumer= error_message_consumer;
			this.bps_consumer = bps_consumer;
			this.total_read = total_read;
		}

		@Override
		public void run() {
			try {
				long start = System.nanoTime();
				int read;
				while( ( read = channel.read( read_buffer ) ) != -1 ) {
					if ( digest != null ) {
						digest.update( read_buffer_array, 0, read );
					}
					read_buffer.clear();

					total_read.addAndGet( read );
				}
				long end = System.nanoTime();

				if ( digest != null ) {
					byte[] checksum = digest.digest();
					if ( !Arrays.equals( LargeTransfersTest.this.checksum, checksum ) ) {
						error_message_consumer.accept( "Checksum mismatch:" +
							"\n   " + Arrays.toString( LargeTransfersTest.this.checksum ) +
							"\n   " + Arrays.toString( checksum ) );
					}
				}

				double bps = ( ( double ) args.data_size / ( double ) ( end - start ) ) *
					TimeUnit.SECONDS.toNanos( 1 );
				bps_consumer.accept( bps );
			}
			catch( Exception ex ) {
				error_message_consumer.accept( "Error: " + ex );
			}
		}
	}



	public static class Args {
		private final long data_size;
		private final int threads;
		private final @Nullable MessageDigest digest;
		private final int buffer_size;

		Args( long data_size, int threads, MessageDigest digest, int buffer_size ) {
			this.data_size = data_size;
			this.threads = threads;
			this.digest = digest;
			this.buffer_size = buffer_size;
		}



		@Override
		public String toString() {
			return data_size + " x " + threads + ", digester=" +
				( digest == null ? "none" : digest.getAlgorithm() ) +
				", buffer=" + buffer_size;
		}
	}


	private static TimerTask createProgressTask( String name, long total,
		LongSupplier done_supplier ) {

		return new TimerTask() {
			@Override
			public void run() {
				long done = done_supplier.getAsLong();

				double progress = done / ( double ) total;

				System.out.println( "  " + name + " Progress: " +
					NumberFormat.getPercentInstance().format( progress ) + "  - " +
					BinaryByteUnit.format( done ) + " / " +
					BinaryByteUnit.format( total ) );
			}
		};
	}
}
