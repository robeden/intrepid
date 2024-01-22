package com.starlight.intrepid.message;

/**
 * This message is sent to acknowledge that the server received a invocation request
 * and that it is still working on the invocation. It will be sent immediately after
 * receiving an {@link InvokeIMessage} and periodically
 * while still working on the invocation until it is complete.
 */
public class InvokeAckIMessage implements IMessage {
	private final int call_id;



	/**
	 * @param call_id			ID of the call being acknowledged.
	 */
	public InvokeAckIMessage( int call_id ) {
		this.call_id = call_id;
	}



	public int getCallID() {
		return call_id;
	}



	@Override
	public IMessageType getType() {
		return IMessageType.INVOKE_ACK;
	}



	@Override
	public String toString() {
		return "InvokeAckIMessage{call_id=" + call_id + '}';
	}



	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		InvokeAckIMessage that = ( InvokeAckIMessage ) o;

		return call_id == that.call_id;
	}


	@Override
	public int hashCode() {
		return call_id;
	}
}
