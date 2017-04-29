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

import static com.diffplug.spotless.LibPreconditions.requireElementsNonNull;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Models the result of applying a {@link Formatter} on a given {@link File}
 * while characterizing various failure modes (slow convergence, cycles, and divergence).
 *
 * See {@link #check(Formatter, File)} as the entry point to this class.
 */
public final class PaddedCell {
	/** The kind of result. */
	public enum Type {
		CONVERGE, CYCLE, DIVERGE;

		/** Creates a PaddedCell with the given file and steps. */
		PaddedCell create(File file, List<String> steps, String original) {
			return new PaddedCell(file, this, steps, original);
		}

	}

	private final File file;
	private final Type type;
	private final List<String> steps;
	private final String original;

	private PaddedCell(File file, Type type, List<String> steps, String original) {
		this.file = Objects.requireNonNull(file, "file");
		this.type = Objects.requireNonNull(type, "type");
		this.original = Objects.requireNonNull(original, "original");
		// defensive copy
		this.steps = new ArrayList<>(steps);
		requireElementsNonNull(this.steps);
	}

	/** Returns the file which was tested. */
	public File file() {
		return file;
	}

	/** Returns the type of the result (either {@link Type#CONVERGE}, {@link Type#CYCLE}, or {@link Type#DIVERGE}). */
	public Type type() {
		return type;
	}

	/** Returns the steps it takes to get to the result. */
	public List<String> steps() {
		return Collections.unmodifiableList(steps);
	}

	/**
	 * Applies the given formatter to the given file, checking that
	 * F(F(input)) == F(input).
	 *
	 * If it meets this test, {@link #misbehaved()} will return false.
	 *
	 * If it fails the test, {@link #misbehaved()} will return true, and you can find
	 * out more about the misbehavior based on its {@link Type}.
	 *
	 */
	public static PaddedCell check(Formatter formatter, File file) {
		Objects.requireNonNull(formatter, "formatter");
		Objects.requireNonNull(file, "file");
		byte[] rawBytes = ThrowingEx.get(() -> Files.readAllBytes(file.toPath()));
		String raw = new String(rawBytes, formatter.getEncoding());
		String original = LineEnding.toUnix(raw);
		return check(formatter, file, original, MAX_CYCLE);
	}

	public static PaddedCell check(Formatter formatter, File file, String originalUnix) {
		return check(
				Objects.requireNonNull(formatter, "formatter"),
				Objects.requireNonNull(file, "file"),
				Objects.requireNonNull(originalUnix, "originalUnix"),
				MAX_CYCLE);
	}

	private static final int MAX_CYCLE = 10;

	private static PaddedCell check(Formatter formatter, File file, String original, int maxLength) {
		if (maxLength < 2) {
			throw new IllegalArgumentException("maxLength must be at least 2");
		}
		List<String> appliedN = new ArrayList<>();
		String input = formatter.compute(original, file);
		appliedN.add(input);
		if (input.equals(original)) {
			return Type.CONVERGE.create(file, appliedN, original);
		}

		while (appliedN.size() < maxLength) {
			String output = formatter.compute(input, file);
			if (output.equals(input)) {
				return Type.CONVERGE.create(file, appliedN, original);
			} else {
				int idx = appliedN.indexOf(output);
				if (idx >= 0) {
					return Type.CYCLE.create(file, appliedN.subList(idx, appliedN.size()), original);
				} else {
					appliedN.add(output);
					input = output;
				}
			}
		}
		return Type.DIVERGE.create(file, appliedN, original);
	}

	/**
	 * Returns true if the formatter misbehaved in any way
	 * (did not converge after a single iteration).
	 */
	public boolean misbehaved() {
		boolean isWellBehaved = type == Type.CONVERGE && steps.size() <= 1;
		return !isWellBehaved;
	}

	/** Returns true if the original input is final. */
	public boolean isOriginalFinal() {
		// @formatter:off
		switch (type) {
		case CONVERGE:
		case CYCLE:
			return steps.contains(original);
		case DIVERGE:
			return true; //If the result is diverging, there is nothing more the user can do
		default:
			throw new IllegalArgumentException("Unknown type: " + type);
		}
		// @formatter:on
	}

	/**
	 * Any result which doesn't diverge can be resolved.
	 * Use {@link #isOriginalFinal()} to determine whether the
	 * original file content meets the formatter rules.
	 */
	@Deprecated
	public boolean isResolvable() {
		return type != Type.DIVERGE;
	}

	/** Returns the solution on best effort basis */
	public String finalResult() {
		// @formatter:off
		switch (type) {
		case CONVERGE:
			/* Take the converged solution */
			return steps.get(steps.size()-1);
		case CYCLE:
			if (isOriginalFinal()) {
				/* in case user input is part of the cycle, let the user decided */
				return original;
			}
			/* In case the formatter converges into a cycle,
			 * select the minimum edit distance to preserve,
			 * user modifications */
			return Comparison.nearest(steps, original);
		case DIVERGE:
			return original;
		default:
			throw new IllegalArgumentException("Unknown type: " + type);
		}

	}

	/**
	 * Returns the "canonical" form for this particular result (only possible if isResolvable).
	 * The usage of the "canonical" formatted file content is discouraged, since
	 * it is not transparent for the user. Instead the {@link #finalResult()}
	 * should be used.
	 */
	@Deprecated
	public String canonical() {
		// @formatter:off
		switch (type) {
		case CONVERGE:	return steps.get(steps.size() - 1);
		case CYCLE:		return Collections.min(steps, Comparator.comparing(String::length).thenComparing(Function.identity()));
		case DIVERGE:	throw new IllegalArgumentException("No canonical form for a diverging result");
		default:	throw new IllegalArgumentException("Unknown type: " + type);
		}
		// @formatter:on
	}

	/** Returns a string which describes this result. */
	public String userMessage() {
		// @formatter:off
		switch (type) {
		case CONVERGE:	return "converges after " + steps.size() + " steps";
		case CYCLE:		return "cycles between " + steps.size() + " steps";
		case DIVERGE:	return "diverges after " + steps.size() + " steps";
		default:	throw new IllegalArgumentException("Unknown type: " + type);
		}
		// @formatter:on
	}
}
