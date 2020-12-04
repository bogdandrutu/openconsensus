/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.metrics.batch.BatchObserverContext;
import java.util.function.Consumer;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Meter is a simple, interface that allows users to record measurements (metrics).
 *
 * <p>There are two ways to record measurements:
 *
 * <ul>
 *   <li>Record raw measurements, and defer defining the aggregation and the labels for the exported
 *       Instrument. This should be used in libraries like gRPC to record measurements like
 *       "server_latency" or "received_bytes".
 *   <li>Record pre-defined aggregation data (or already aggregated data). This should be used to
 *       report cpu/memory usage, or simple metrics like "queue_length".
 * </ul>
 *
 * <p>TODO: Update comment.
 */
@ThreadSafe
public interface Meter {

  /**
   * Returns a {@link Meter} that only creates no-op {@link Instrument}s that neither record nor are
   * emitted.
   */
  static Meter getDefault() {
    return DefaultMeter.getInstance();
  }

  /**
   * Returns a builder for a {@link DoubleCounter}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a {@code DoubleCounter.Builder}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
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
   */
  LongValueRecorder.Builder longValueRecorderBuilder(String name);

  /**
   * Returns a new builder for a {@link DoubleSumObserver}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code DoubleSumObserver}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   */
  DoubleSumObserver.Builder doubleSumObserverBuilder(String name);

  /**
   * Returns a new builder for a {@link LongSumObserver}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code LongSumObserver}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   */
  LongSumObserver.Builder longSumObserverBuilder(String name);

  /**
   * Returns a new builder for a {@link DoubleUpDownSumObserver}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code DoubleUpDownObserver}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   */
  DoubleUpDownSumObserver.Builder doubleUpDownSumObserverBuilder(String name);

  /**
   * Returns a new builder for a {@link LongUpDownSumObserver}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code LongUpDownSumObserver}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   */
  LongUpDownSumObserver.Builder longUpDownSumObserverBuilder(String name);

  /**
   * Returns a new builder for a {@link DoubleValueObserver}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code DoubleValueObserver}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   */
  DoubleValueObserver.Builder doubleValueObserverBuilder(String name);

  /**
   * Returns a new builder for a {@link LongValueObserver}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code LongValueObserver}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   */
  LongValueObserver.Builder longValueObserverBuilder(String name);

  /**
   * Utility method that allows users to atomically record measurements to a set of Instruments with
   * a common set of labels.
   *
   * @param keyValuePairs The set of labels to associate with this recorder and all it's recordings.
   * @return a {@code MeasureBatchRecorder} that can be use to atomically record a set of
   *     measurements associated with different Measures.
   */
  BatchRecorder newBatchRecorder(String... keyValuePairs);

  /**
   * Register a new {@link BatchObserverContext} consumer for handling observations of Instruments
   * in a batch.
   *
   * @param batchObserverContextConsumer {@link Consumer} function accepting a {@link
   *     BatchObserverContext}.
   */
  void newBatchObserver(Consumer<BatchObserverContext> batchObserverContextConsumer);
}
