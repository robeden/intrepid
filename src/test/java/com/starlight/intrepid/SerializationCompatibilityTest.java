package com.starlight.intrepid;

import com.starlight.IOKit;
import com.starlight.intrepid.auth.UserCredentialsConnectionArgs;
import com.starlight.intrepid.exception.*;
import com.starlight.io.TestIOKit;
import com.starlight.locale.UnlocalizableTextResourceKey;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 *
 */
@RunWith( Parameterized.class )
public class SerializationCompatibilityTest {
	@Parameterized.Parameters( name="{0}" )
	public static Collection<TestArg> args() throws NoSuchMethodException {
		UUID uuid = UUID.fromString( "189d7ac5-9e04-43a3-8531-30854df911f2" );
		VMID test_vmid = new VMID( uuid, null );

		return Arrays.asList(
			new TestArg(
				new VMID( uuid, null ),
				"VMID.1",
				"VMID with params:" +
					"\n  uuid: " + uuid +
					"\n  hint_text: null" ),
			new TestArg(
				new VMID( uuid, "hintText" ),
				"VMID.2",
				"VMID with params:" +
					"\n  uuid: " + uuid +
					"\n  hint_text: hintText" ),

			new TestArg(
				new UserCredentialsConnectionArgs( "reden", "12345".toCharArray() ),
				"UserCredentialsConnectionArgs.1",
				"UserCredentialsConnectionArgs with params:" +
					"\n  username: reden" +
					"\n  password: 12345" ),

			new TestArg(
				new ProxyInvocationHandler( test_vmid, 1, new TObjectIntHashMap<>(),
					"a name", "delegate" ),
				"ProxyInvocationHandler.1",
				"ProxyInvocationHandler with args:" +
					"\n  vmid: " + test_vmid +
					"\n  object_id: 1" +
					"\n  method_to_id_map: {}" +
					"\n  persistent_name: a name" +
					"\n  delegate: delegate (String)" ),
			new TestArg(
				new MethodIDTemplate(
					SerializationCompatibilityTest.class.getMethod( "args" ) ),
				"MethodIDTemplate.1",
				"MethodIDTemplate with args: " +
					"\n  method: SerializationCompatibilityTest.args()" ),

			// Exceptions...
			new TestArg(
				new ChannelRejectedException(),
				"ChannelRejectedException.1",
				"ChannelRejectedException with no args" ),
			new TestArg(
				new ConnectionFailureException(),
				"ConnectionFailureException.1",
				"ConnectionFailureException with no args" ),
			new TestArg(
				new IllegalProxyDelegateException( "message" ),
				"IllegalProxyDelegateException.1",
				"IllegalProxyDelegateException with args: " +
					"\n  message: message" ),
			new TestArg(
				new InterruptedCallException(),
				"InterruptedCallException.1",
				"InterruptedCallException with no args" ),
			new TestArg(
				new IntrepidRuntimeException(),
				"IntrepidRuntimeException.1",
				"IntrepidRuntimeException with no args" ),
			new TestArg(
				new MethodInvocationFailedException( "test message" ),
				"MethodInvocationFailedException.1",
				"MethodInvocationFailedException with arg:" +
					"\n  message: test message" ),
			new TestArg(
				new NotConnectedException( "a message", test_vmid ),
				"NotConnectedException.1",
				"NotConnectedException with args: " +
					"\n  message: a messgae" +
					"\n  vmid: " + test_vmid ),
			new TestArg(
				new ObjectNotBoundException(
					new UnlocalizableTextResourceKey( "message" ) ),
				"ObjectNotBoundException.1",
				"ObjectNotBoundException with args: " +
					"\n  message: message (UnlocalizableTextResourceKey)"),
			new TestArg(
				new ServerException( new RuntimeException( "test exception" ) ),
				"ServerException.1",
				"ServerException with args: " +
					"\n  throwable: RuntimeException(test exception)" ),
			new TestArg(
				new UnknownMethodException( 2 ),
				"UnknownMethodException.1",
				"UnknownMethodException with args: " +
					"\n  method_id: 2" ),
			new TestArg(
				new UnknownObjectException( 1, "a name", test_vmid ),
				"UnknownObjectException.1",
				"UnknownObjectException with args: " +
					"\n  object_id: 1" +
					"\n  persistent_name: a name" +
					"\n  vmid: " + test_vmid )
		);
	}


	private final TestArg arg;

	public SerializationCompatibilityTest( TestArg arg ) {
		this.arg = arg;
	}


	@Test
	public void testCurrentSanity() throws Exception {
		byte[] data = TestIOKit.serialize( arg.object );
		Object deserialized = TestIOKit.deserialize( data );
		checkEquality( arg.object, deserialized );
	}


	@Test
	public void testDeserializeMatch() throws Exception {
		try( Reader reader = new InputStreamReader(
			SerializationCompatibilityTest.class.getResourceAsStream( arg.file_name  ) ) ) {

			byte[] old_data = TestIOKit.readTestFile( reader );
			Object actual = TestIOKit.deserialize( old_data );
			checkEquality( arg.object, actual );
		}
	}

	@Test
	public void testSerializeMatch() throws Exception {
		Assume.assumeTrue( "Exceptions don't work well for this test",
			! ( arg.object instanceof Throwable ) );

		try( Reader reader = new InputStreamReader(
			SerializationCompatibilityTest.class.getResourceAsStream( arg.file_name  ) ) ) {

			byte[] old_data = TestIOKit.readTestFile( reader );

			byte[] current_data = TestIOKit.serialize( arg.object );

			assertArrayEquals( old_data, current_data );
		}
	}



	private static void checkEquality( Object expected, Object actual ) {
		// Throwables don't override equals(). This makes me sad. Rather than going
		// through and implementing it on everything, I'm going to do a simple class
		// check. Since they implement Serializable (rather than Externalizable), data
		// errors are less likely and I'm mainly checking for package changes at the
		// moment.
		if ( expected instanceof Throwable ) {
			assertEquals( expected.getClass(), actual.getClass() );
		}
		else {
			assertEquals( expected, actual );
		}
	}



	/**
	 * This main method will generate the test files.
	 */
	public static void main( String[] args ) throws Exception {
		for( TestArg arg : args() ) {
			try( Writer out = new FileWriter( arg.file_name, false ) ) {
				TestIOKit.writeTestFile( out, arg.comment,
					IOKit.serialize( arg.object ) );
				System.out.println( "Created " + arg.file_name );
			}
		}
	}



	private static class TestArg {
		private final Object object;
		private final String file_name;
		private final String comment;

		TestArg( Object object, String file_name, String comment ) {
			this.object = object;
			this.file_name = file_name + ".ser";
			this.comment = comment;
		}

		@Override
		public String toString() {
			return object.toString();
		}
	}
}
