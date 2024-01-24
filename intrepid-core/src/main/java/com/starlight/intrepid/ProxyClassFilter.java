package com.starlight.intrepid;

import javax.annotation.Nonnull;

/**
 * Interface that can be used to ensure proxies are create with only the expected
 * interfaces implemented.
 * <p>
 * Note that {@link java.io.Serializable} cannot be filtered. It will always be present
 * on proxies.
 *
 * @see <a href="https://bitbucket.org/robeden/intrepid/issues/14">Issue #14</a>
 */
@FunctionalInterface
public interface ProxyClassFilter {
	/**
	 * This should return {@code true} for interfaces that are allowed to be included in
	 * a proxy's implemented interfaces.
	 *
	 * @param real_object           The real object for which a proxy is being created.
	 * @param proposed_interface    The interface in question.
	 */
	boolean checkInterfaceAllowed( @Nonnull Object real_object,
		@Nonnull Class<?> proposed_interface );
}
