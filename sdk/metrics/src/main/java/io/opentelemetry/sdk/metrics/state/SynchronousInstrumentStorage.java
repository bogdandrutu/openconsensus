/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.state;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.internal.GuardedBy;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.CollectionHandle;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.aggregator.SynchronousHandle;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import io.opentelemetry.sdk.metrics.view.AttributesProcessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Storage for measurements from a synchronous instrument. */
final class SynchronousInstrumentStorage<T> implements WriteableInstrumentStorage {
  private final ReentrantLock collectLock;
  private final Aggregator<T> aggregator;
  private final AttributesProcessor attributesProcessor;
  private final ConcurrentHashMap<Attributes, SynchronousHandle<T>> perAttrributeStorage;

  @GuardedBy("collectLock")
  private final Map<CollectionHandle, LastReportedAccumulation<T>> reportHistory;

  @GuardedBy("collectLock")
  private final List<DeltaAccumulation<T>> savedDeltas;

  public static <T> SynchronousInstrumentStorage<T> create(
      Aggregator<T> aggregator, AttributesProcessor attributesProcessor) {
    return new SynchronousInstrumentStorage<T>(aggregator, attributesProcessor);
  }

  SynchronousInstrumentStorage(Aggregator<T> aggregator, AttributesProcessor attributesProcessor) {
    collectLock = new ReentrantLock();
    this.aggregator = aggregator;
    this.attributesProcessor = attributesProcessor;
    perAttrributeStorage = new ConcurrentHashMap<>();
    reportHistory = new HashMap<>();
    savedDeltas = new ArrayList<>();
  }

  private final StorageHandle lateBoundStorageHandle =
      new StorageHandle() {
        @Override
        public void record(Measurement measurement) {
          SynchronousInstrumentStorage.this.record(measurement);
        }

        @Override
        public void release() {}
      };

  /**
   * Obtain exclusive write access to metric stream for this instrument defined by this set of
   * attributes.
   */
  @Override
  public StorageHandle bind(Attributes attributes) {
    Objects.requireNonNull(attributes, "attributes");
    if (attributesProcessor.usesContext()) {
      return lateBoundStorageHandle;
    }
    return doBind(attributesProcessor.process(attributes, Context.current()));
  }

  /** version of "bind" that does NOT call attributesProcessor. */
  private StorageHandle doBind(Attributes attributes) {
    SynchronousHandle<T> storageHandle = perAttrributeStorage.get(attributes);
    if (storageHandle != null && storageHandle.acquire()) {
      // At this moment it is guaranteed that the Bound is in the map and will not be removed.
      return storageHandle;
    }

    // Missing entry or no longer mapped, try to add a new entry.
    storageHandle = aggregator.createStreamStorage();
    while (true) {
      SynchronousHandle<T> boundStorageHandle =
          perAttrributeStorage.putIfAbsent(attributes, storageHandle);
      if (boundStorageHandle != null) {
        if (boundStorageHandle.acquire()) {
          // At this moment it is guaranteed that the Bound is in the map and will not be removed.
          return boundStorageHandle;
        }
        // Try to remove the boundAggregator. This will race with the collect method, but only one
        // will succeed.
        perAttrributeStorage.remove(attributes, boundStorageHandle);
        continue;
      }
      return storageHandle;
    }
  }

  /** Writes a measurement into the appropriate metric stream. */
  @Override
  public void record(Measurement measurement) {
    StorageHandle handle =
        doBind(attributesProcessor.process(measurement.getAttributes(), measurement.getContext()));
    try {
      handle.record(measurement);
    } finally {
      handle.release();
    }
  }

  /**
   * Collects the currently accumulated measurements from the concurrent-friendly synchronous
   * storage.
   *
   * <p>All synchronous handles will be collected + reset during this method. Additionally cleanup
   * related stale concurrent-map handles will occur. Any {@code null} measurements are ignored.
   *
   * <p>This method should be behind a lock.
   */
  @GuardedBy("collectLock")
  private DeltaAccumulation<T> collectSynchronousDeltaAccumulationAndReset() {
    Map<Attributes, T> result = new HashMap<>();
    for (Map.Entry<Attributes, SynchronousHandle<T>> entry : perAttrributeStorage.entrySet()) {
      boolean unmappedEntry = entry.getValue().tryUnmap();
      if (unmappedEntry) {
        // If able to unmap then remove the record from the current Map. This can race with the
        // acquire but because we requested a specific value only one will succeed.
        perAttrributeStorage.remove(entry.getKey(), entry.getValue());
      }
      T accumulation = entry.getValue().accumulateThenReset();
      if (accumulation == null) {
        continue;
      }
      // Feed latest batch to the aggregator.
      result.put(entry.getKey(), accumulation);
    }
    return new DeltaAccumulation<>(result);
  }

  /**
   * This method leverages the `savedDeltas` and `reportHistory` to construct a final metric point
   * to report for this synchronous instrument.
   */
  @GuardedBy("collectLock")
  private MetricData buildMetricFor(
      CollectionHandle collector, long startEpochNanos, long epochNanos) {
    Map<Attributes, T> result = new HashMap<>();
    // Next merge the delta w/ the last set of points.
    for (DeltaAccumulation<T> point : savedDeltas) {
      if (!point.wasReadBy(collector)) {
        mergeInPlace(result, point.read(collector));
      }
    }

    long lastEpochNanos;
    // First pull the last cumulative value.
    if (reportHistory.containsKey(collector)) {
      LastReportedAccumulation<T> last = reportHistory.get(collector);
      // Send the accumulated deltas in w/ pervious accumulation to get final result.
      result =
          aggregator.diffPrevious(
              last.getAccumlation(), result, /*isAsynchronousMeasurement=*/ false);
      lastEpochNanos = last.getEpochNanos();
    } else {
      lastEpochNanos = startEpochNanos;
    }

    // Now write the aggregated value back, and generate final metric.
    reportHistory.put(collector, new LastReportedAccumulation<>(result, epochNanos));
    // Don't make a metric if we have no values.
    if (result.isEmpty()) {
      return null;
    }
    return aggregator.buildMetric(result, startEpochNanos, lastEpochNanos, epochNanos);
  }

  /** Merges accumulations from {@code toMerge} into {@code result}. */
  private void mergeInPlace(Map<Attributes, T> result, Map<Attributes, T> toMerge) {
    toMerge.forEach(
        (k, v) -> {
          if (result.containsKey(k)) {
            result.put(k, aggregator.merge(result.get(k), v));
          } else {
            result.put(k, v);
          }
        });
  }

  /** Removes deltas once all collectors have pulled them. */
  @GuardedBy("collectLock")
  private void cleanup(Set<CollectionHandle> collectors) {
    Iterator<DeltaAccumulation<T>> i = savedDeltas.iterator();
    while (i.hasNext()) {
      DeltaAccumulation<T> delta = i.next();
      if (delta.wasReadyByAll(collectors)) {
        i.remove();
      }
    }
    // TODO: Do we allow different pipelines?
    Iterator<CollectionHandle> c = reportHistory.keySet().iterator();
    while (c.hasNext()) {
      if (!collectors.contains(c.next())) {
        c.remove();
      }
    }
  }

  /** Collects bucketed metrics and resets the underlying storage for the next collection period. */
  @Override
  public List<MetricData> collectAndReset(
      CollectionHandle collector,
      Set<CollectionHandle> allCollectors,
      long startEpochNanos,
      long epochNanos) {
    collectLock.lock();
    try {
      // TODO: Refactor this for per-collector storage.
      // First reset currently accumulating synchronous handles.
      savedDeltas.add(collectSynchronousDeltaAccumulationAndReset());
      // Next build metric from past history and latest deltas.
      MetricData result = buildMetricFor(collector, startEpochNanos, epochNanos);
      // finally, cleanup stale deltas.
      cleanup(allCollectors);
      if (result != null) {
        return Arrays.asList(result);
      }
      return Collections.emptyList();
    } finally {
      collectLock.unlock();
    }
  }

  /** Remembers what was presented to a specific exporter. */
  private static class LastReportedAccumulation<T> {
    private final Map<Attributes, T> accumulation;
    private final long epochNanos;

    LastReportedAccumulation(Map<Attributes, T> accumulation, long epochNanos) {
      this.accumulation = accumulation;
      this.epochNanos = epochNanos;
    }

    long getEpochNanos() {
      return epochNanos;
    }

    Map<Attributes, T> getAccumlation() {
      return accumulation;
    }
  }

  /**
   * Synchronous recording of delta-accumulated measurements.
   *
   * <p>This stores in-progress metric values that haven't been exported yet.
   */
  private static class DeltaAccumulation<T> {
    private final Map<Attributes, T> recording;
    private final Set<CollectionHandle> readers;

    DeltaAccumulation(Map<Attributes, T> recording) {
      this.recording = recording;
      this.readers = CollectionHandle.mutableSet();
    }

    boolean wasReadBy(CollectionHandle handle) {
      return readers.contains(handle);
    }

    boolean wasReadyByAll(Set<CollectionHandle> handles) {
      return readers.containsAll(handles);
    }

    Map<Attributes, T> read(CollectionHandle handle) {
      readers.add(handle);
      return recording;
    }
  }
}
