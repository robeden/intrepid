package com.starlight.intrepid;

import gnu.trove.map.TIntObjectMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;


/**
 *
 */
public class MethodMapTest {
	@Test
	public void testList() {
		TIntObjectMap<Method> map = MethodMap.generateMethodMap( List.class );
		System.out.println( "Map: " + map );
	}
}