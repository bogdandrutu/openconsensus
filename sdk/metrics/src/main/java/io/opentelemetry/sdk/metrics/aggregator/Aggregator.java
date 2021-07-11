/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import java.util.Map;

/**
 * A processor that can generate aggregators for metrics streams while also combining those streams
 * into {@link MetricData}.
 *
 * <p>Aggregators have the following lifecycle:
 */
public interface Aggregator<T> {

  /**
   * Construct a handle for storing highly-concurrent measurement input.
   *
   * <p>SynchronousHandle instances *must* be threadsafe and allow for high contention across
   * threads.
   */
  public SynchronousHandle<T> createStreamStorage();

  /**
   * In the event we receive two asynchronous measurements for the same set of attributes, this
   * method merges the values.
   */
  T merge(T current, T accumulated);

  /** Constructs the accumulation storage from a raw measurement. */
  T asyncAccumulation(Measurement measurement);

  /**
   * Returns final accumulation result after looking at the previous reported accumulation and the
   * current set of measurement accumulations.
   *
   * @param isAsynchronousMeasurement true if measurements were taken asynchronously (cumulatives)
   *     or synchronously (deltas).
   */
  Map<Attributes, T> diffPrevious(
      Map<Attributes, T> previous, Map<Attributes, T> current, boolean isAsynchronousMeasurement);

  /**
   * Construct a metric stream.
   *
   * @param accumulated The underlying stream points.
   * @param startEpochNanos The start time for the metrics SDK.
   * @param lastEpochNanos The time of the last collection period (i.e. delta start time).
   * @param epochNanos The current collection period time (i.e. end time).
   */
  MetricData buildMetric(
      Map<Attributes, T> accumulated, long startEpochNanos, long lastEpochNanos, long epochNanos);
}
