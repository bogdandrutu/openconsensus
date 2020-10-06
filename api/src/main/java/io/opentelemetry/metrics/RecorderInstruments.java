/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.metrics;

public interface RecorderInstruments {
  /**
   * Returns a builder for a {@link DoubleCounter}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a {@code DoubleCounter.Builder}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   * @since 0.1.0
   */
  DoubleCounter.Builder doubleCounterBuilder(String name);

  /**
   * Returns a builder for a {@link LongCounter}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a {@code LongCounter.Builder}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   * @since 0.1.0
   */
  LongCounter.Builder longCounterBuilder(String name);

  /**
   * Returns a builder for a {@link DoubleUpDownCounter}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a {@code DoubleCounter.Builder}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   * @since 0.1.0
   */
  DoubleUpDownCounter.Builder doubleUpDownCounterBuilder(String name);

  /**
   * Returns a builder for a {@link LongUpDownCounter}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a {@code LongCounter.Builder}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   * @since 0.1.0
   */
  LongUpDownCounter.Builder longUpDownCounterBuilder(String name);

  /**
   * Returns a new builder for a {@link DoubleValueRecorder}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code DoubleValueRecorder}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   * @since 0.1.0
   */
  DoubleValueRecorder.Builder doubleValueRecorderBuilder(String name);

  /**
   * Returns a new builder for a {@link LongValueRecorder}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code LongValueRecorder}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   * @since 0.1.0
   */
  LongValueRecorder.Builder longValueRecorderBuilder(String name);
}
