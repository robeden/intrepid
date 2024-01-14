package com.starlight.intrepid.driver.netty;

import com.starlight.intrepid.VMID;
import com.starlight.intrepid.driver.MessageConsumedButInvalidException;
import com.starlight.intrepid.driver.MessageDecoder;
import com.starlight.intrepid.driver.SessionCloseOption;
import com.starlight.intrepid.driver.SessionInfo;
import com.starlight.intrepid.message.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static com.starlight.intrepid.driver.netty.NettyIntrepidDriver.SESSION_INFO_KEY;
import static java.util.Objects.requireNonNull;

public class NettyIMessageDecoder extends ByteToMessageDecoder {
	private static final Logger LOG =
		LoggerFactory.getLogger( NettyIMessageDecoder.class );


	private final VMID vmid;
	private final ThreadLocal<VMID> deserialization_context_vmid;
	private final BiFunction<UUID,String,VMID> vmid_creator;

	NettyIMessageDecoder( @Nonnull VMID vmid,
		@Nonnull ThreadLocal<VMID> deserialization_context_vmid,
		@Nonnull BiFunction<UUID,String,VMID> vmid_creator ) {

		this.vmid = requireNonNull( vmid );
		this.deserialization_context_vmid = requireNonNull( deserialization_context_vmid );
		this.vmid_creator = requireNonNull( vmid_creator );
	}


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in,
                          List<Object> out) throws Exception {

		deserialization_context_vmid.set( vmid );
		try {
			int position_before = in.readerIndex();

			SessionInfo session_info = ctx.attr( SESSION_INFO_KEY ).get();
			final Byte protocol_version = session_info.getProtocolVersion();

			IMessage message = MessageDecoder.decode( new ByteBufWrapper( in ),
				protocol_version,
				( response, close_option ) -> {
					LOG.debug( "Response: {}", response );

                    ctx.write( response );

					if ( close_option != null ) {
						long flush_time = 0;
						if ( close_option == SessionCloseOption.ATTEMPT_FLUSH ) {
							flush_time = 2000;
						}
						CloseHandler.close( ctx.channel(), flush_time );
					}
				}, vmid_creator );
			if ( message == null ) {
                in.readerIndex(position_before);
			}
			else {
				out.add( message );
			}
		}
		catch( MessageConsumedButInvalidException ex ) {
			LOG.debug( "Invalid message consumed: {}", ex.getMessage() );
		}
		catch( Exception ex ) {
			LOG.warn( "Error during decode", ex );
			throw ex;
		}
		finally {
			deserialization_context_vmid.remove();
		}
    }
}
