package com.starlight.intrepid.auth;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.VMID;

import java.lang.reflect.Method;
import java.net.InetAddress;

/**
 *
 */
public interface PreInvocationValidator {
	void preCall( @Nonnull Intrepid instance, @Nonnull VMID calling_vmid,
		@Nullable InetAddress calling_host, @Nullable UserContextInfo user_context,
		@Nonnull Method method, @Nonnull Object target, @Nonnull Object[] args )
		throws MethodInvocationRefusedException;
}
