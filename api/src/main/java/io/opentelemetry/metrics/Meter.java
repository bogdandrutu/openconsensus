/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.metrics;

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
public interface Meter extends RecorderInstruments, ObserverInstruments {

  /**
   * Utility method that allows users to atomically record measurements to a set of Instruments with
   * a common set of labels.
   *
   * @param keyValuePairs The set of labels to associate with this recorder and all it's recordings.
   * @return a {@code MeasureBatchRecorder} that can be use to atomically record a set of
   *     measurements associated with different Measures.
   * @since 0.1.0
   */
  BatchRecorder newBatchRecorder(String... keyValuePairs);

  /**
   * Utility method that allows users to atomically record asynchronous measurements to a set of
   * Instruments with a common set of labels.
   *
   * @param name The name of the BatchObserver.
   * @return a {@code BatchObserver} that can be used to create new asynchronous instruments.
   */
  BatchObserver newBatchObserver(String name);
}
