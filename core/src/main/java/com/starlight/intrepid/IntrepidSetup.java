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

import com.logicartisan.common.core.thread.ScheduledExecutor;
import com.starlight.intrepid.auth.AuthenticationHandler;
import com.starlight.intrepid.auth.PreInvocationValidator;
import com.starlight.intrepid.driver.IntrepidDriver;
import com.starlight.intrepid.driver.NoAuthenticationHandler;
import com.starlight.intrepid.driver.UnitTestHook;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.net.InetAddress;
import java.nio.channels.ByteChannel;
import java.util.Optional;
import java.util.function.ToIntFunction;

import static java.util.Objects.requireNonNull;


/**
 * <a href="http://en.wikipedia.org/wiki/Builder_pattern">Builder</a> class to initialize
 * an Intrepid instance.
 */
@SuppressWarnings( { "WeakerAccess", "unused" } )
public class IntrepidSetup {
	private static final int CHANNEL_RX_WINDOW_DEFAULT_SIZE =
		Integer.getInteger( "intrepid.channel.default_rx_window", 10_000_000 ).intValue();



	private IntrepidDriver driver;
	private InetAddress server_address;
	private Integer server_port;
	private ScheduledExecutor thread_pool;
	private AuthenticationHandler auth_handler;
	private String vmid_hint;
	private ConnectionListener connection_listener;
	private PerformanceListener performance_listener;
	private ChannelAcceptor channel_acceptor;
	private PreInvocationValidator validator;
	private ToIntFunction<Optional<Object>> channel_rx_window_size_function =
		attachment -> CHANNEL_RX_WINDOW_DEFAULT_SIZE;
	private ProxyClassFilter proxy_class_filter = ( o, i ) -> true;

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

	public IntrepidSetup driver(IntrepidDriver driver ) {
		this.driver = driver;
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
		@Nonnull PreInvocationValidator validator ) {

		this.validator = requireNonNull( validator );
		return this;
	}

	/**
	 * Provide a function for specifying the "receive window" for data received in
	 * virtual byte channels. This essentially specifies the amount of data that can sit
	 * in a queue waiting to be processed. A smaller window size will sometimes result
	 * is slower performance, depending on the usage pattern of the application.
	 * This can make a noticeable difference when the sender is very "bursty" and data
	 * processing is relatively expensive. However, testing in your application is
	 * recommended because send rate/frequency, receive processing rate and network
	 * performance can all contribute.
	 * <p>
	 * The default implementation is a fixed size controlled by the system property
	 * {@code intrepid.channel.default_rx_window}.
	 *
	 * @param size_function         A function that returns the window size for a channel
	 *                              given the attachment (from
	 *                              {@link Intrepid#createChannel(VMID, Serializable)} or
	 *                              {@link ChannelAcceptor#newChannel(ByteChannel, VMID, Serializable)}).
	 *                              Note that an Rx Window will be required for both peers
	 *                              involved in the channel, so this will be called on
	 *                              both the "client" and "server" side. The windows do
	 *                              are only used for receiving data, so the returned
	 *                              values do not need to match between peers.
	 *                              The size must be greater than zero.
	 */
	public IntrepidSetup channelRxWindowSize(
		@Nonnull ToIntFunction<Optional<Object>> size_function ) {

		channel_rx_window_size_function = requireNonNull( size_function );
		return this;
	}


	/**
	 * Provide a filter that will verify the interfaces implemented by proxies. The
	 * default implementation approves all interfaces.
	 *
	 * @see ProxyClassFilter
	 */
	public IntrepidSetup proxyClassFilter( @Nonnull ProxyClassFilter filter ) {
		proxy_class_filter = requireNonNull( filter );
		return this;
	}


	AuthenticationHandler getAuthHandler() {
		return auth_handler;
	}

	InetAddress getServerAddress() {
		return server_address;
	}

	Integer getServerPort() {
		return server_port;
	}

	IntrepidDriver getDriver() {
		return driver;
	}

	ScheduledExecutor getThreadPool() {
		return thread_pool;
	}

	String getVMIDHint() {
		return vmid_hint;
	}

	ConnectionListener getConnectionListener() {
		return connection_listener;
	}

	PerformanceListener getPerformanceListener() {
		return performance_listener;
	}

	ChannelAcceptor getChannelAcceptor() {
		return channel_acceptor;
	}

	PreInvocationValidator getPreInvocationValidator() {
		return validator;
	}

	ToIntFunction<Optional<Object>> getChannelRxWindowSizeFunction() {
		return channel_rx_window_size_function;
	}

	ProxyClassFilter getProxyClassFilter() {
		return proxy_class_filter;
	}



	IntrepidSetup unitTestHook( @Nonnull UnitTestHook hook ) {
		this.unit_test_hook = hook;
		return this;
	}

	UnitTestHook getUnitTestHook() {
		return unit_test_hook;
	}
}
