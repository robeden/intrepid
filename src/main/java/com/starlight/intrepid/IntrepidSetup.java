// Copyright (c) 2010 Rob Eden.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Intrepid nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.starlight.intrepid;

import com.starlight.NotNull;
import com.starlight.intrepid.auth.AuthenticationHandler;
import com.starlight.intrepid.auth.PreInvocationValidator;
import com.starlight.intrepid.spi.IntrepidSPI;
import com.starlight.intrepid.spi.NoAuthenticationHandler;
import com.starlight.intrepid.spi.UnitTestHook;
import com.starlight.thread.ScheduledExecutor;

import java.net.InetAddress;
import java.util.Objects;
import java.util.function.Predicate;


/**
 * <a href="http://en.wikipedia.org/wiki/Builder_pattern">Builder</a> class to initialize
 * an Intrepid instance.
 */
public class IntrepidSetup {
	private IntrepidSPI spi;
	private InetAddress server_address;
	private Integer server_port;
	private ScheduledExecutor thread_pool;
	private AuthenticationHandler auth_handler;
	private String vmid_hint;
	private ConnectionListener connection_listener;
	private PerformanceListener performance_listener;
	private ChannelAcceptor channel_acceptor;
	private PreInvocationValidator validator;
	private Predicate<Class> proxy_class_filter = c -> true;	// default accepts all

	private UnitTestHook unit_test_hook;


	public IntrepidSetup authHandler( AuthenticationHandler auth_handler ) {
		if ( this.auth_handler != null ) {
			throw new IllegalStateException(
				"An AuthenticationHandler is already installed." );
		}
		this.auth_handler = auth_handler;
		return this;
	}

	public IntrepidSetup serverAddress( InetAddress server_address ) {
		this.server_address = server_address;
		return this;
	}

	public IntrepidSetup serverPort( int server_port ) {
		this.server_port = Integer.valueOf( server_port );
		return this;
	}

	public IntrepidSetup spi( IntrepidSPI spi ) {
		this.spi = spi;
		return this;
	}

	public IntrepidSetup threadPool( ScheduledExecutor thread_pool ) {
		this.thread_pool = thread_pool;
		return this;
	}

	public IntrepidSetup vmidHint( String vmid_hint ) {
		this.vmid_hint = vmid_hint;
		return this;
	}

	public IntrepidSetup openServer() {
		if ( this.auth_handler != null ) {
			throw new IllegalStateException(
				"An AuthenticationHandler is already installed." );
		}
		this.auth_handler = new NoAuthenticationHandler();
		if ( server_port == null ) server_port = Integer.valueOf( 0 );

		return this;
	}

	public IntrepidSetup connectionListener( ConnectionListener listener ) {
		this.connection_listener = listener;
		return this;
	}

	public IntrepidSetup performanceListener( PerformanceListener listener ) {
		this.performance_listener = listener;
		return this;
	}

	public IntrepidSetup channelAcceptor( ChannelAcceptor acceptor ) {
		this.channel_acceptor = acceptor;
		return this;
	}

	public IntrepidSetup preInvocationValidator(
		@NotNull PreInvocationValidator validator ) {

		this.validator = Objects.requireNonNull( validator );
		return this;
	}

	/**
	 * A proxy class filter controls the classes that are allowed for inclusion in a proxy
	 * via {@link Intrepid#createProxy(Object)} or via automatic creation. During
	 * creation, eligible classes will be tested against the filter and included if the
	 * filter indicates that they are acceptable. The default filter accepts all classes.
	 */
	public void proxyClassFilter( @NotNull Predicate<Class> filter ) {
		this.proxy_class_filter = Objects.requireNonNull( filter );
	}




	public AuthenticationHandler getAuthHandler() {
		return auth_handler;
	}

	public InetAddress getServerAddress() {
		return server_address;
	}

	public Integer getServerPort() {
		return server_port;
	}

	public IntrepidSPI getSPI() {
		return spi;
	}

	public ScheduledExecutor getThreadPool() {
		return thread_pool;
	}

	public String getVMIDHint() {
		return vmid_hint;
	}

	public ConnectionListener getConnectionListener() {
		return connection_listener;
	}

	public PerformanceListener getPerformanceListener() {
		return performance_listener;
	}

	public ChannelAcceptor getChannelAcceptor() {
		return channel_acceptor;
	}

	PreInvocationValidator getPreInvocationValidator() {
		return validator;
	}

	@NotNull Predicate<Class> getProxyClassFilter() {
		return proxy_class_filter;
	}



	IntrepidSetup unitTestHook( @NotNull UnitTestHook hook ) {
		this.unit_test_hook = hook;
		return this;
	}

	UnitTestHook getUnitTestHook() {
		return unit_test_hook;
	}
}
