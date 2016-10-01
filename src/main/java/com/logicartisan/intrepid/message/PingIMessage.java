package com.logicartisan.intrepid.message;

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



	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		PingIMessage that = ( PingIMessage ) o;

		return sequence_number == that.sequence_number;

	}

	@Override
	public int hashCode() {
		return ( int ) sequence_number;
	}
}
