package com.starlight.intrepid.driver.netty;

import com.starlight.intrepid.driver.DataSink;
import com.starlight.intrepid.driver.MessageEncoder;
import com.starlight.intrepid.driver.SessionInfo;
import com.starlight.intrepid.message.IMessage;
import com.starlight.intrepid.message.IMessageType;
import com.starlight.intrepid.message.SessionInitIMessage;
import com.starlight.intrepid.message.SessionInitResponseIMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.starlight.intrepid.driver.netty.NettyIntrepidDriver.SESSION_INFO_KEY;

public class NettyIMessageEncoder extends MessageToByteEncoder<IMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyIMessageEncoder.class);

	private static final int ALLOCATE_SIZE =
		Integer.getInteger( "intrepid.netty.encoder.allocate_size", 2_000 ).intValue();


    @Override
    protected void encode(ChannelHandlerContext ctx, IMessage message, ByteBuf out)
        throws Exception {

		ByteBuf length_buf = Unpooled.buffer(4);

		final int initial_writer_index = out.writerIndex();
        if (out.readerIndex() == initial_writer_index) {
            out.writerIndex(initial_writer_index + 4);

            encode0(ctx, message, length_buf, out);
			final int final_writer_index = out.writerIndex();

			final int length_size = length_buf.readableBytes();
			if (length_size > 4) {
				throw new Exception("Logic error: more than four length bytes: " + length_buf);
			}

			out.writerIndex(initial_writer_index + (4 - length_size));
			if (length_size < 4) {
				out.readerIndex(out.readerIndex() + (4 - length_size));
			}
			out.writeBytes(length_buf);
			out.writerIndex(final_writer_index);
        }
        else {
            ByteBuf data = Unpooled.buffer(ALLOCATE_SIZE);
            encode0(ctx, message, length_buf, data);
			out.writeBytes(length_buf);
			out.writeBytes(data);
        }
    }


    private void encode0(ChannelHandlerContext ctx, IMessage message,
						 ByteBuf length_buffer, ByteBuf out) throws Exception {

		DataSink length_slice_wrapper = new ByteBufWrapper(length_buffer);
		DataSink buffer_wrapper = new ByteBufWrapper(out);

		if ( message.getType() == IMessageType.SESSION_INIT ) {
			MessageEncoder.encodeSessionInit( (SessionInitIMessage) message,
				length_slice_wrapper, buffer_wrapper );
		}
		else if ( message.getType() == IMessageType.SESSION_INIT_RESPONSE ) {
			MessageEncoder.encodeSessionInitResponse(
				(SessionInitResponseIMessage) message, length_slice_wrapper,
				buffer_wrapper );
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

			MessageEncoder.encode( message, protocol_version,
				length_slice_wrapper, buffer_wrapper );
		}
    }
}
