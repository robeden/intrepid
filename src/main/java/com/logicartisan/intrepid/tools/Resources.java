package com.logicartisan.intrepid.tools;

import com.starlight.locale.ResourceKey;
import com.starlight.locale.ResourceList;
import com.starlight.locale.TextResourceKey;


/**
 *
 */
public class Resources extends ResourceList {
	static final ResourceKey<String> ERROR_ATTACHMENT_NOT_ID =
		new TextResourceKey( "Attachment should be an ObjectDrop.ID" );

	static final ResourceKey<String> ERROR_UNKNOWN_ID =
		new TextResourceKey( "Unknown ObjectDrop.ID {0}" );

	static final ResourceKey<String> ERROR_SLOT_FULL =
		new TextResourceKey( "Slot already full for ObjectDrop.ID {0}" );

	static final ResourceKey<String> ERROR_DROP_INSTANCE_GCED =
		new TextResourceKey( "ObjectDrop instance with ID {0} has been garbage " +
		"collected. A strong reference should be held while drops can still be done." );

	static final ResourceKey<String> ERROR_DROP_INSTANCE_NOT_FOUND =
		new TextResourceKey( "Unable to find ObjectDrop instance with ID {0} and no " +
			"destination VMID was provided. Either the ID is out of date " +
			"or a destination should have been provided." );

	static final ResourceKey<String> ERROR_DROP_WITH_VMID_BUT_NO_INSTANCE =
		new TextResourceKey( "Destination VMID was non-null, but " +
			"Intrepid instance was null. Please provide an instance with a " +
			"connection to the specified VMID." );


	static final ResourceKey<String> ERROR_NO_SUITABLE_ACCEPTOR_FOUND =
		new TextResourceKey( "No suitable ChannelAcceptor found." );
}
