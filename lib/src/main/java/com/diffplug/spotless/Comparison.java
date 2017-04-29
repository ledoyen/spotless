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

import java.util.Arrays;
import java.util.Objects;

final class Comparison {

	/** Interface is provided by static functions */
	private Comparison() {}

	/** Selects the character sequence of a list with the minimum edit distance to another sequence */
	public static <T extends CharSequence> T nearest(Iterable<T> of, CharSequence to) {
		T nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (T from : of) {
			int distanceFrom = distance(from, to);
			if (nearestDistance > distanceFrom) {
				nearestDistance = distanceFrom;
				nearest = from;
			}
		}
		Objects.requireNonNull(nearest, "'of' must contain at least one element");
		return nearest;
	}

	/** Returns the edit distance between the two sequences */
	public static int distance(CharSequence seq1, CharSequence seq2) {
		return ONP.compare(seq1, seq2);
	}

	/**
	 * Implementation based on "An O(NP) Sequence Comparison Algorithm",
	 * by Sun Wu, Udi Manber, Gene Myers.
	 */
	private static class ONP {
		private final CharSequence a, b;
		private final int m, n; //Length of a,b
		private final int delta; //diagonal delta = n − m contains the sink
		private final int[] fp; //furthest d-points
		private final int fp_offset; //Offset to cope with negative array indexes

		private ONP(CharSequence seq1, CharSequence seq2) {
			//Algorithm preliminaries n>=m
			if (seq1.length() < seq2.length()) {
				a = seq1;
				b = seq2;
			} else {
				a = seq2;
				b = seq1;
			}
			m = a.length();
			n = b.length();
			delta = n - m;
			fp = new int[m + n + 3]; //Index range is −(m+1)..(n+1)
			fp_offset = m + 1; //Offset to index range
			Arrays.fill(fp, -1); //fp[−(m+1)..(n+1)] := −1;
		}

		static int compare(CharSequence seq1, CharSequence seq2) {
			Objects.requireNonNull(seq1, "seq1");
			Objects.requireNonNull(seq2, "seq2");
			ONP onp = new ONP(seq1, seq2);
			return onp.editDistance();
		}

		private int editDistance() {
			int p = -1;
			do {
				//Note that the max calculation moved to the snake method
				++p; //p := p + 1;
				for (int k = -p + fp_offset; k <= delta - 1 + fp_offset; ++k) { //For k := −p to delta−1
					//fp[k] := snake( k, max (fp[k−1] + 1, fp[k+1]) );
					fp[k] = snake(k);
				}
				for (int k = delta + p + fp_offset; k >= delta + 1 + fp_offset; --k) { // For k := delta + p downto delta + 1 by −1 do
					//fp[k] := snake( k, max (fp[k−1] + 1, fp[k+1]) );
					fp[k] = snake(k);
				}
				//fp[delta] := snake( delta, max (fp[delta−1] + 1, fp[delta+1]) );
				fp[delta + fp_offset] = snake(delta + fp_offset);
			} while (fp[delta + fp_offset] < n);
			return delta + 2 * p; //The edit distance
		}

		/**
		 * Function snake( k, y: int) : int
		 * y calculation moved from editDistance into this method
		 */
		private int snake(int k) {
			int y = Math.max(fp[k - 1] + 1, fp[k + 1]);
			int x = y - k + fp_offset;
			while (x < m && y < n && a.charAt(x) == b.charAt(y)) {
				++x;
				++y;
			}
			return y;
		}
	}
}
