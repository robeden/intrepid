package com.starlight.intrepid.auth;

import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.VMID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.net.SocketAddress;

/**
 *
 */
public interface PreInvocationValidator {
	void preCall(@Nonnull Intrepid instance, @Nonnull VMID calling_vmid,
				 @Nullable SocketAddress calling_host, @Nullable UserContextInfo user_context,
				 @Nonnull Method method, @Nonnull Object target, @Nonnull Object[] args )
		throws MethodInvocationRefusedException;
}
