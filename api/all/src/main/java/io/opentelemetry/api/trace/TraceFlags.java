/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.trace;

import javax.annotation.concurrent.Immutable;

/**
 * A valid trace flags is a byte or 2 character lowercase hex (base16) String.
 *
 * <p>These options are propagated to all child {@link Span spans}. These determine features such as
 * whether a {@code Span} should be traced.
 */
@Immutable
public interface TraceFlags {
  /**
   * Returns the length of the lowercase hex (base16) representation of the {@link TraceFlags}.
   *
   * @return the length of the lowercase hex (base16) representation of the {@link TraceFlags}.
   */
  static int getLength() {
    return ImmutableTraceFlags.HEX_LENGTH;
  }

  /**
   * Returns the default (with all flag bits off) byte representation of the {@link TraceFlags}.
   *
   * @return the default (with all flag bits off) byte representation of the {@link TraceFlags}.
   */
  static TraceFlags getDefault() {
    return ImmutableTraceFlags.DEFAULT;
  }

  /**
   * Returns the lowercase hex (base16) representation of the {@link TraceFlags} with the sampling
   * flag bit on.
   *
   * @return the lowercase hex (base16) representation of the {@link TraceFlags} with the sampling
   *     flag bit on.
   */
  static TraceFlags getSampled() {
    return ImmutableTraceFlags.SAMPLED;
  }

  /**
   * Returns the {@link TraceFlags} converted from the given lowercase hex (base16) representation.
   * If the input cannot be parsed, a {@link TraceFlags} will be returned where {@link
   * TraceFlags#isValid()} returns {@code false}.
   *
   * <p>This may throw runtime exceptions if the input is invalid.
   *
   * @param src the buffer where the hex (base16) representation of the {@link TraceFlags} is.
   * @param srcOffset the offset int buffer.
   * @return the {@link TraceFlags} converted from the given lowercase hex (base16) representation.
   */
  static TraceFlags fromHex(CharSequence src, int srcOffset) {
    return ImmutableTraceFlags.fromHex(src, srcOffset);
  }

  /**
   * Returns the {@link TraceFlags} converted from the given byte representation.
   *
   * @param traceFlagsByte the byte representation of the {@link TraceFlags}.
   * @return the {@link TraceFlags} converted from the given byte representation.
   */
  static TraceFlags fromByte(byte traceFlagsByte) {
    return ImmutableTraceFlags.fromByte(traceFlagsByte);
  }

  /**
   * Returns {@code true} if the sampling bit is on for this {@link TraceFlags}, otherwise {@code
   * false}.
   *
   * @return {@code true} if the sampling bit is on for this {@link TraceFlags}, otherwise {@code *
   *     false}.
   */
  boolean isSampled();

  /**
   * Returns the lowercase hex (base16) representation of this {@link TraceFlags}.
   *
   * @return the byte representation of the {@link TraceFlags}.
   */
  String asHex();

  /**
   * Returns the byte representation of this {@link TraceFlags}.
   *
   * @return the byte representation of the {@link TraceFlags}.
   */
  byte asByte();

  /**
   * Returns whether this {@link TraceFlags} is valid. An invalid {@link TraceFlags} should
   * generally be discarded.
   */
  boolean isValid();
}
