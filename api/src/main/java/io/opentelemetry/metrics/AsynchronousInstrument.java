/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.metrics;

import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument.Result;
import javax.annotation.concurrent.ThreadSafe;

/**
 * {@code AsynchronousInstrument} is an interface that defines a type of instruments that are used
 * to report measurements asynchronously.
 *
 * <p>They are reported by a callback, once per collection interval, and lack Context. They are
 * permitted to report only one value per distinct label set per period. If the application observes
 * multiple values for the same label set, in a single callback, the last value is the only value
 * kept.
 *
 * @param <R> the callback Result type.
 * @since 0.1.0
 */
@ThreadSafe
public interface AsynchronousInstrument<R extends Result> extends Instrument {
  /**
   * A {@code Callback} for a {@code AsynchronousInstrument}.
   *
   * @since 0.1.0
   */
  interface Callback<R extends Result> {
    void update(R result);
  }

  /**
   * Sets a callback that gets executed every collection interval.
   *
   * <p>Evaluation is deferred until needed, if this {@code AsynchronousInstrument} metric is not
   * exported then it will never be called.
   *
   * @param callback the callback to be executed before export.
   * @since 0.1.0
   */
  void setCallback(Callback<R> callback);

  /** Builder class for {@link AsynchronousInstrument}. */
  interface Builder extends Instrument.Builder {
    @Override
    AsynchronousInstrument<?> build();
  }

  interface Result {}

  /** The result for the {@link Callback}. */
  interface LongResult extends Result {
    void observe(long value, Labels labels);
  }

  /** The result for the {@link Callback}. */
  interface DoubleResult extends Result {
    void observe(double value, Labels labels);
  }

  interface Observation {}

  interface LongObservation {
    Observation observation(long observation);
  }

  interface DoubleObservation {
    Observation observation(double observation);
  }
}
