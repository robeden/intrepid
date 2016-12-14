package com.logicartisan.intrepid.tools;

import com.logicartisan.common.core.IOKit;
import com.logicartisan.common.core.thread.ObjectSlot;
import com.logicartisan.intrepid.ChannelAcceptor;
import com.logicartisan.intrepid.Intrepid;
import com.logicartisan.intrepid.IntrepidContext;
import com.logicartisan.intrepid.VMID;
import com.logicartisan.intrepid.exception.ChannelRejectedException;
import com.starlight.locale.FormattedTextResourceKey;
import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TShortObjectHashMap;

import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 *
 */
public class ObjectDrop implements ChannelAcceptor {
	// NOTE: only a short is used
	private static final AtomicInteger DROP_INSTANCE_COUNTER =
		new AtomicInteger( new Random().nextInt() );
	private static final TShortObjectMap<WeakReference<ObjectDrop>> DROP_INSTANCE_MAP =
		new TShortObjectHashMap<WeakReference<ObjectDrop>>();
	private static final Lock DROP_INSTANCE_MAP_LOCK = new ReentrantLock();


	private final short instance_id;

	private final Map<ID,ObjectSlot> object_map =
		new WeakHashMap<ID, ObjectSlot>();
	private final Lock object_map_lock = new ReentrantLock();

	private final AtomicInteger id_counter = new AtomicInteger( new Random().nextInt() );

	public ObjectDrop() {
		instance_id = ( short ) DROP_INSTANCE_COUNTER.incrementAndGet();

		DROP_INSTANCE_MAP_LOCK.lock();
		try {
			DROP_INSTANCE_MAP.put( instance_id, new WeakReference<ObjectDrop>( this ) );
		}
		finally {
			DROP_INSTANCE_MAP_LOCK.unlock();
		}
	}


	public <T> ID<T> prepareForDrop() {
		int id = id_counter.getAndIncrement();
		ID<T> id_obj = new ID<T>( instance_id, id );

		object_map_lock.lock();
		try {
			object_map.put( id_obj, new ObjectSlot<Object>() );
		}
		finally {
			object_map_lock.unlock();
		}

		return id_obj;
	}


	public <T> T retrieve( ID<T> id ) throws InterruptedException, IOException {
		return retrieve( id, Long.MAX_VALUE, TimeUnit.MILLISECONDS );
	}

	public <T> T retrieve( ID<T> id, long timeout, TimeUnit timeout_unit )
		throws InterruptedException, IOException {

		if ( timeout_unit == null ) timeout_unit = TimeUnit.MILLISECONDS;

		ObjectSlot<Object> slot;
		object_map_lock.lock();
		try {
			slot = object_map.remove( id );
		}
		finally {
			object_map_lock.unlock();
		}

		if ( slot == null ) {
			throw new IllegalStateException( MessageFormat.format(
				Resources.ERROR_UNKNOWN_ID.getValue(), id ) );
		}

		Object result = slot.waitForValue( timeout_unit.toMillis( timeout ) );
		if ( result == null ) return null;
		else if ( result instanceof ExceptionWrapper ) {
			Throwable root_exception = ( ( ExceptionWrapper ) result ).exception;
			if ( root_exception instanceof IOException ) {
				throw ( IOException ) root_exception;
			}
			else if ( root_exception instanceof InterruptedException ) {
				throw ( InterruptedException ) root_exception;
			}
			else throw new IOException( root_exception );
		}
		else {
			//noinspection unchecked
			return ( T ) result;
		}
	}


	public static <T> void dropToCaller( ID<T> id, T object, boolean compress )
		throws ChannelRejectedException, IOException {

		VMID vmid = IntrepidContext.getCallingVMID();
		Intrepid instance = IntrepidContext.getActiveInstance();

		if ( vmid != null && instance == null ) {
			throw new IllegalStateException(
				Resources.ERROR_DROP_WITH_VMID_BUT_NO_INSTANCE.getValue() );
		}

		drop( vmid, id, instance, object, compress );
	}

	public static <T> void drop( VMID destination, ID<T> id, Intrepid instance,
		T object, boolean compress ) throws ChannelRejectedException, IOException {

		if ( destination == null ) {
			// Find the ObjectDrop instance
			ObjectDrop drop_instance;
			DROP_INSTANCE_MAP_LOCK.lock();
			try {
				WeakReference<ObjectDrop> ref =
					DROP_INSTANCE_MAP.get( id.getDropInstanceID() );
				if ( ref == null ) {
					throw new IllegalStateException( MessageFormat.format(
						Resources.ERROR_DROP_INSTANCE_NOT_FOUND.getValue(),
						Short.valueOf( id.getDropInstanceID() ) ) );
				}

				drop_instance = ref.get();
				if ( drop_instance == null ) {
					DROP_INSTANCE_MAP.remove( id.getDropInstanceID() );

					throw new IllegalStateException( MessageFormat.format(
						Resources.ERROR_DROP_INSTANCE_GCED.getValue(),
						Short.valueOf( id.getDropInstanceID() ) ) );
				}
			}
			finally {
				DROP_INSTANCE_MAP_LOCK.unlock();
			}

			// NOTE: drop_instance can't be null

			ObjectSlot<T> slot;
			drop_instance.object_map_lock.lock();
			try {
				//noinspection unchecked
				slot = drop_instance.object_map.get( id );
			}
			finally {
				drop_instance.object_map_lock.unlock();
			}

			if ( slot == null ) {
				throw new IllegalStateException( new FormattedTextResourceKey(
					Resources.ERROR_UNKNOWN_ID, id ).getValue() );
			}
			else slot.set( object );

			return;
		}

		ByteChannel channel = null;
		OutputStream out = null;
		ObjectOutputStream oout = null;
		try {
			channel = instance.createChannel( destination, id );
			out = new BufferedOutputStream( Channels.newOutputStream( channel ),
				100 * 1024 );

			// VERSION
			out.write( 0 );

			// COMPRESS
			out.write( compress ? 1 : 0 );

			if ( compress ) oout = new ObjectOutputStream( new GZIPOutputStream( out ) );
			else oout = new ObjectOutputStream( out );

			oout.writeUnshared( object );
			oout.flush();
		}
		finally {
			IOKit.close( ( Closeable ) oout );
			IOKit.close( out );
			IOKit.close( channel );
		}
	}


	@Override
	public void newChannel( ByteChannel channel, VMID source_vmid,
		Serializable attachment ) throws ChannelRejectedException {

		// Verify the expected type
		if ( !( attachment instanceof ID ) ) {
			throw new ChannelRejectedException( Resources.ERROR_ATTACHMENT_NOT_ID );
		}

		ObjectSlot<Object> slot;
		object_map_lock.lock();
		try {
			//noinspection SuspiciousMethodCalls
			slot = object_map.get( attachment );
		}
		finally {
			object_map_lock.unlock();
		}

		// Make sure we're expecting the object
		if ( slot == null ) {
			throw new ChannelRejectedException(
				new FormattedTextResourceKey( Resources.ERROR_UNKNOWN_ID, attachment ) );
		}
		// Make sure the object hasn't already been read
		else if ( slot.get() != null ) {
			throw new ChannelRejectedException(
				new FormattedTextResourceKey( Resources.ERROR_SLOT_FULL, attachment ) );
		}

		new ChannelReader( channel, slot ).start();
	}


	private final class ChannelReader extends Thread {
		private final ByteChannel channel;
		private final ObjectSlot<Object> slot;

		ChannelReader( ByteChannel channel, ObjectSlot<Object> slot ) {
			this.channel = channel;
			this.slot = slot;
		}

		@Override
		public void run() {
			InputStream in = null;
			ObjectInputStream oin = null;
			try {
				in = Channels.newInputStream( channel );

				// VERSION
				//noinspection ResultOfMethodCallIgnored
				in.read();

				// COMPRESSED
				boolean compressed = in.read() > 0;

				if ( compressed ) oin = new ObjectInputStream( new GZIPInputStream( in ) );
				else oin = new ObjectInputStream( in );

				slot.set( oin.readObject() );
			}
			catch( Exception ex ) {
				slot.set( new ExceptionWrapper( ex ) );
			}
			finally {
				IOKit.close( ( Closeable ) oin );
				IOKit.close( in );
			}
		}
	}


	private class ExceptionWrapper {
		private final Exception exception;

		ExceptionWrapper( Exception exception ) {
			this.exception = exception;
		}
	}


	@SuppressWarnings( { "UnusedDeclaration" } )
	public static class ID<T> implements Externalizable {
		private static final long serialVersionUID = -3899261949742494011L;


		private short drop_instance_id;
		private int id;

		/** FOR EXTERNALIZATION ONLY!!! */
		public ID() {
			assert IOKit.isBeingDeserialized();
		}

		private ID( short drop_instance_id, int id ) {
			this.drop_instance_id = drop_instance_id;
			this.id = id;
		}

		private short getDropInstanceID() {
			return drop_instance_id;
		}

		private int getID() {
			return id;
		}


		@Override
		public boolean equals( Object o ) {
			if ( this == o ) return true;
			if ( o == null || getClass() != o.getClass() ) return false;

			ID id1 = ( ID ) o;

			return drop_instance_id == id1.drop_instance_id && id == id1.id;
		}

		@Override
		public int hashCode() {
			int result = ( int ) drop_instance_id;
			result = 31 * result + id;
			return result;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append( "ID" );
			sb.append( "{drop_instance_id=" ).append( drop_instance_id );
			sb.append( ", id=" ).append( id );
			sb.append( '}' );
			return sb.toString();
		}

		@Override
		public void readExternal( ObjectInput in )
			throws IOException, ClassNotFoundException {

			// VERSION
			in.readByte();

			// DROP INSTANCE ID
			drop_instance_id = in.readShort();

			// ID
			id = in.readInt();

		}

		@Override
		public void writeExternal( ObjectOutput out ) throws IOException {
			// VERSION
			out.writeByte( 0 );

			// DROP INSTANCE ID
			out.writeShort( drop_instance_id );

			// ID
			out.writeInt( id );
		}
	}
}
