package com.logicartisan.intrepid.driver;

import com.logicartisan.intrepid.message.IMessage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 *
 */
@FunctionalInterface
public interface ResponseHandler {
	void sendMessage( @Nonnull IMessage message, @Nullable SessionCloseOption close );
}
