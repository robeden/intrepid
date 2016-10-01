package com.logicartisan.intrepid.message;

/**
 *
 */
public class PingResponseIMessage implements IMessage {
	private final short sequence_number;

	public PingResponseIMessage( short sequence_number ) {
		this.sequence_number = sequence_number;
	}


	@Override
	public IMessageType getType() {
		return IMessageType.PING_RESPONSE;
	}


	public short getSequenceNumber() {
		return sequence_number;
	}
}
