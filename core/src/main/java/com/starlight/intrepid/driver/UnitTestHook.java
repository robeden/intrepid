package com.starlight.intrepid.driver;

import com.starlight.intrepid.VMID;
import com.starlight.intrepid.message.IMessage;

import java.io.IOException;


/**
 * Hooks to allow unit tests to do magical things for testing.
 */
public interface UnitTestHook {
	/**
	 * Allows dropping a message that would otherwise be sent.
	 *
	 * @return      <tt>true</tt> to send the message normally or <tt>false</tt> to act
	 *              like it was sent but not actually send it.
	 *
	 * @throws IOException      Throwing an exception will raise the exception from
	 *              {@link IntrepidDriver#sendMessage} to simulate
	 *              an error during sending.
	 */
	boolean dropMessageSend( VMID destination, IMessage message )
		throws IOException;

	/**
	 * Allows dropping a message that would otherwise be received.
	 *
	 * @return      <tt>true</tt> to process the message normally or <tt>false</tt> to
	 *              drop it.
	 *
	 * @throws IOException      Throwing an exception will raise the exception from
	 *              {@link IntrepidDriver#sendMessage} to simulate
	 *              an error during receive.
	 */
	boolean dropMessageReceive( VMID source, IMessage message ) throws IOException;
}
