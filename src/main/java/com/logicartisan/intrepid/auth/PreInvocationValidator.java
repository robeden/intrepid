package com.logicartisan.intrepid.auth;

import com.starlight.NotNull;
import com.starlight.Nullable;
import com.logicartisan.intrepid.Intrepid;
import com.logicartisan.intrepid.VMID;

import java.lang.reflect.Method;
import java.net.InetAddress;

/**
 *
 */
public interface PreInvocationValidator {
	void preCall( @NotNull Intrepid instance, @NotNull VMID calling_vmid,
		@Nullable InetAddress calling_host, @Nullable UserContextInfo user_context,
		@NotNull Method method, @NotNull Object target, @NotNull Object[] args )
		throws MethodInvocationRefusedException;
}
