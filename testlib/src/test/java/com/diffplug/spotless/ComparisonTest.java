/*
 * Copyright 2016 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.spotless;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.Assert;
import org.junit.Test;

public class ComparisonTest {

	/**
	 * Testing example provided in "An O(NP) Sequence Comparison Algorithm",
	 * by Sun Wu, Udi Manber, Gene Myers.
	 */
	@Test
	public void distanceExample() {
		String a = "acbdeacbed";
		String b = "acebdabbabed";
		int d = 6;
		assertEquals(d, Comparison.distance(a, b));
		assertEquals(d, Comparison.distance(b, a));
	}

	@Test
	public void distanceLimits() {
		int limit = (int) Math.pow(2, 16);
		String a = "";
		char[] b_array = new char[limit];
		CharSequence b = java.nio.CharBuffer.wrap(b_array);
		assertEquals(limit, Comparison.distance(a, b));
	}

	@Test
	public void nearest() {
		BiFunction<String, String, String> nearestOf = (of, to) -> {
			List<String> unordered = Arrays.asList(of.split(","));
			String sameResult = null;
			for (int i = 0; i < unordered.size(); ++i) {
				// try every rotation of the list
				Collections.rotate(unordered, 1);
				String result = Comparison.nearest(unordered, to);
				if (null == sameResult)
					sameResult = result;
				Assert.assertEquals("Order independence (incase they have not the same distance).", sameResult, result);
			}
			return sameResult;
		};
		Assert.assertEquals("abc", nearestOf.apply("abc,abd,ab", "abc"));
		Assert.assertEquals("Abc", nearestOf.apply("Abc,ABc,ABC", "abc"));
		Assert.assertEquals("ac", nearestOf.apply("ac", "abc"));
	}

}
