/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.state;

import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.sdk.metrics.CollectionHandle;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.view.AttributesProcessor;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** A storage location for metric data collected by a specific instrument instance. */
public interface InstrumentStorage {
  /**
   * Collects the metrics from this storage and resets for the next collection period.
   *
   * @param collector The current pipeline collecting metrics.
   * @param allCollectors All (registered) pipelines that could collect metrics.
   * @param epochNanos The timestamp for this collection.
   */
  List<MetricData> collectAndReset(
      CollectionHandle collector,
      Set<CollectionHandle> allCollectors,
      long startEpochNanos,
      long epochNanos);

  /**
   * Construct storage for a synchronous insturment.
   *
   * <p>This guarantees a high-concurrency friendly implementation.
   */
  public static <T> WriteableInstrumentStorage createSynchronous(
      long startEpochNanos, Aggregator<T> aggregator, AttributesProcessor processor) {
    return SynchronousInstrumentStorage.create(aggregator, processor);
  }

  /**
   * Construct a "storage" for aysnchronous instrument.
   *
   * <p>This storage will poll for data when asked.
   */
  @SuppressWarnings("unused")
  public static <T> InstrumentStorage createAsynchronous(
      long startEpochNanos,
      Consumer<? extends ObservableMeasurement> callback,
      Aggregator<T> aggregator,
      AttributesProcessor processor) {
    return AsynchronousInstrumentStorage.create(callback, aggregator, processor);
  }
}
