/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics.batch;

import io.opentelemetry.api.common.Labels;
import io.opentelemetry.api.metrics.Instrument;
import io.opentelemetry.api.metrics.batch.AsynchronousBatchInstrument.Observation;
import java.util.function.Consumer;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Util class for creating multiple instruments that can be atomically observed in a single
 * callback.
 *
 * <p>This class is equivalent to individually calling {@code observe} on every {@link Instrument},
 * but has the advantage of all recordings occurring atomically.
 *
 * <p>Example:
 *
 * <pre>{@code
 * meter.newBatchObserver(
 *     context -> {
 *       DoubleSumBatchObserver timeObserver =
 *           context.doubleSumObserverBuilder("time").setUnit("s").build();
 *       LongSumBatchObserver distanceObserver =
 *           context.longSumObserverBuilder("distance").setUnit("m").build();
 *
 *       context.registerCallback(
 *           result ->
 *               MyMeasure measure = MyMeasure.getdata();
 *               result.observe(
 *                   Labels.of("myKey", "myValue"),
 *                   distanceObserver.observation(measure.getDistance()),
 *                   timeObserver.observation(measure.getTime())));
 *     });
 * }</pre>
 */
@ThreadSafe
public interface BatchObserverContext extends Instrument {

  /**
   * Returns a new builder for a {@link DoubleValueBatchObserver}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code DoubleValueBatchObserver}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   */
  DoubleValueBatchObserver.Builder doubleValueObserverBuilder(String name);

  /**
   * Returns a new builder for a {@link LongValueBatchObserver}.
   *
   * @param name the name of the instrument. Should be a ASCII string with a length no greater than
   *     255 characters.
   * @return a new builder for a {@code LongValueBatchObserver}.
   * @throws NullPointerException if {@code name} is null.
   * @throws IllegalArgumentException if different metric with the same name already registered.
   * @throws IllegalArgumentException if the {@code name} does not match the requirements.
   */
  LongValueBatchObserver.Builder longValueObserverBuilder(String name);

  /**
   * Set a {@link Consumer} function that accepts a {@link BatchObserverContext}. This function will
   * call {@code observe} to execute the observation of multiple {@link Instrument}s.
   *
   * @param resultConsumer Function to consume a {@link BatchObserverContext}.
   */
  void registerCallback(Consumer<BatchObserverResult> resultConsumer);

  /** Builder class for {@link BatchObserverContext}. */
  interface Builder extends Instrument.Builder {
    @Override
    BatchObserverContext build();
  }

  @FunctionalInterface
  interface BatchObserverResult {
    void observe(Labels labels, Observation... observations);
  }
}
