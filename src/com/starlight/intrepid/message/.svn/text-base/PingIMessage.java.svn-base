package com.starlight.intrepid.message;

/**
 *
 */
public class PingIMessage implements IMessage {
	private final short sequence_number;

	public PingIMessage( short sequence_number ) {
		this.sequence_number = sequence_number;
	}

	@Override
	public IMessageType getType() {
		return IMessageType.PING;
	}


	public short getSequenceNumber() {
		return sequence_number;
	}
}
