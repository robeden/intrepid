package com.starlight.intrepid.tools;

import com.starlight.intrepid.ChannelAcceptor;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.exception.ChannelRejectedException;

import java.io.Serializable;
import java.nio.channels.ByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * An implementation of {@link com.starlight.intrepid.ChannelAcceptor} that allows
 * multiple implementations to be dynamically registered and unregistered at runtime.
 * This class will delegate calls to the other implementations 
 */
public class PluggableChannelAcceptor implements ChannelAcceptor {
	private final List<ChannelAcceptor> delegates =
		Collections.synchronizedList( new ArrayList<ChannelAcceptor> () );


	/**
	 * Adds a new delegate acceptor to the end of the list.
	 */
	@SuppressWarnings( "WeakerAccess" )
	public void addDelegate( ChannelAcceptor delegate ) {
		delegates.add( Objects.requireNonNull( delegate ) );
	}

	/**
	 * Adds a new delegate acceptor to the front of the list.
	 */
	@SuppressWarnings( "WeakerAccess" )
	public void addDelegateToFront( ChannelAcceptor delegate ) {
		delegates.add( 0, Objects.requireNonNull( delegate ) );
	}


	/**
	 * Remove a delegate acceptor.
	 */
	@SuppressWarnings( { "WeakerAccess", "unused" } )
	public void removeDelegate( ChannelAcceptor delegate ) {
		if ( delegate == null ) return;

		delegates.remove( delegate );
	}


	/**
	 * Remove all delegate acceptors.
	 */
	@SuppressWarnings( "WeakerAccess" )
	public void removeAll() {
		delegates.clear();
	}
	

	@Override
	public void newChannel( ByteChannel channel, VMID source_vmid,
		Serializable attachment ) throws ChannelRejectedException {

		// Try the delegates sequentially
		for( ChannelAcceptor delegate : delegates ) {
			try {
				delegate.newChannel( channel, source_vmid, attachment );
				return;
			}
			catch( ChannelRejectedException ex ) {
				// ignore, keep trying
			}
		}

		throw new ChannelRejectedException( Resources.ERROR_NO_SUITABLE_ACCEPTOR_FOUND );
	}
}
