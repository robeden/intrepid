package com.starlight.intrepid.driver;

import com.starlight.intrepid.message.IMessage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 *
 */
@FunctionalInterface
public interface ResponseHandler {
	void sendMessage( @Nonnull IMessage message, @Nullable SessionCloseOption close );
}
