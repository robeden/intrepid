package com.starlight.intrepid.driver.netty;

import com.starlight.intrepid.driver.DataSink;
import com.starlight.intrepid.driver.MessageEncoder;
import com.starlight.intrepid.driver.SessionInfo;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.IMessageType;
import com.starlight.intrepid.message.SessionInitIMessage;
import com.starlight.intrepid.message.SessionInitResponseIMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.starlight.intrepid.driver.netty.NettyIntrepidDriver.SESSION_INFO_KEY;

public class NettyIMessageEncoder extends MessageToByteEncoder<IMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyIMessageEncoder.class);


    @Override
    protected void encode(ChannelHandlerContext ctx, IMessage message, ByteBuf out)
        throws Exception {

		final int initial_writer_index = out.writerIndex();
		out.writerIndex(initial_writer_index + 4);

		int length = encode0(ctx, message, out);
		final int final_writer_index = out.writerIndex();

		// Prepend the length
		out.writerIndex(initial_writer_index);
		out.writeInt(length);
		out.writerIndex(final_writer_index);
    }


    private int encode0(ChannelHandlerContext ctx, IMessage message,
						ByteBuf out) throws Exception {

		DataSink buffer_wrapper = new ByteBufWrapper(out);

		if ( message.getType() == IMessageType.SESSION_INIT ) {
			return MessageEncoder.encodeSessionInit(
				(SessionInitIMessage) message, buffer_wrapper );
		}
		else if ( message.getType() == IMessageType.SESSION_INIT_RESPONSE ) {
			return MessageEncoder.encodeSessionInitResponse(
				(SessionInitResponseIMessage) message, buffer_wrapper );
		}
		else {
			SessionInfo session_info = ctx.attr( SESSION_INFO_KEY ).get();
			if ( session_info == null ) {
				// TODO: seeing this in unit tests
				assert false : "Unable to send " + message.getType() +
					" message since session info is unavailable";
				throw new IllegalStateException(
					"Unable to send message (session info unavailable)" );
			}
			final Byte protocol_version = session_info.getProtocolVersion();

			// Getting here is a logic error.
			if ( protocol_version == null ) {
				ctx.close();

				String error_message = "Logic error: Should not be sending a " +
					message.getClass().getName() + " message without the " +
					"session protocol version being known";
				LOG.error( error_message );
				// NOTE: This used to be an AssertionError, but that causes MINA to get into a state where the session
				// is not correctly removed and causes the shutdown to hang waiting for session destroyed event that
				// is never fired
				throw new IllegalStateException( error_message );
			}

			return MessageEncoder.encode( message, protocol_version, buffer_wrapper );
		}
    }
}
