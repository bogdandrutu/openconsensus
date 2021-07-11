/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.state;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.CollectionHandle;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.instrument.DoubleMeasurement;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import io.opentelemetry.sdk.metrics.view.AttributesProcessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class AsynchronousInstrumentStorage<T> implements InstrumentStorage {
  private final ReentrantLock collectLock = new ReentrantLock();
  private final BiConsumer<Consumer<Measurement>, AttributesProcessor> metricUpdater;
  private final Aggregator<T> aggregator;
  private final AttributesProcessor attributesProcessor;
  private final Map<CollectionHandle, Map<Attributes, T>> previousCollections;

  static <T> AsynchronousInstrumentStorage<T> create(
      Consumer<? extends ObservableMeasurement> callback,
      Aggregator<T> aggregator,
      AttributesProcessor attributesProcessor) {
    return new AsynchronousInstrumentStorage<>(
        wrapCallback(callback), aggregator, attributesProcessor);
  }

  /** Type gymnastics to handle both long/double observable measurements. */
  @SuppressWarnings("unchecked")
  private static <T extends ObservableMeasurement>
      BiConsumer<Consumer<Measurement>, AttributesProcessor> wrapCallback(Consumer<T> callback) {
    return (storage, processor) ->
        callback.accept((T) new MyObservableMeasurement(storage, processor));
  }

  private AsynchronousInstrumentStorage(
      BiConsumer<Consumer<Measurement>, AttributesProcessor> metricUpdater,
      Aggregator<T> aggregator,
      AttributesProcessor attributesProcessor) {
    this.metricUpdater = metricUpdater;
    this.aggregator = aggregator;
    this.attributesProcessor = attributesProcessor;
    this.previousCollections = new HashMap<>();
  }

  @Override
  public List<MetricData> collectAndReset(
      CollectionHandle collector,
      Set<CollectionHandle> allCollectors,
      long startEpochNanos,
      long epochNanos) {
    // TODO: Collector specific storage and previous calculations.
    collectLock.lock();
    try {
      final Map<Attributes, T> currentMeasurements = new HashMap<>();
      // Construct a lambda that will record measurements in this new hashmap.
      Consumer<Measurement> recorder =
          (measurement) -> {
            T current = aggregator.asyncAccumulation(measurement);
            // Ignore null measurements.
            if (current == null) {
              return;
            }
            if (currentMeasurements.containsKey(measurement.getAttributes())) {
              // TODO: This behave preceeds us, but seems borked.   Should we allow multiple
              // asynchronous
              // measurements and merge them or consider this an error and only take the latest?
              currentMeasurements.put(
                  measurement.getAttributes(),
                  aggregator.merge(currentMeasurements.get(measurement.getAttributes()), current));
            } else {
              currentMeasurements.put(measurement.getAttributes(), current);
            }
          };
      metricUpdater.accept(recorder, attributesProcessor);

      Map<Attributes, T> result = accountForPrevious(collector, currentMeasurements);
      if (result.isEmpty()) {
        return Collections.emptyList();
      }
      // Calculate resulting metric.
      MetricData metricResult =
          aggregator.buildMetric(result, startEpochNanos, /* TODO: diff */ 0, epochNanos);
      if (metricResult != null) {
        return Collections.singletonList(metricResult);
      }
      return Collections.emptyList();
    } finally {
      collectLock.unlock();
    }
  }

  /**
   * This method allows comparison of current value against previous. Used by "DELTA" accumulators.
   */
  private Map<Attributes, T> accountForPrevious(
      CollectionHandle collector, Map<Attributes, T> currentMeasurements) {
    // Allow aggregator to diff vs. previous.
    final Map<Attributes, T> previous = previousCollections.put(collector, currentMeasurements);
    final Map<Attributes, T> result;
    if (previous != null) {
      result =
          aggregator.diffPrevious(
              previous, currentMeasurements, /*isAsynchronousMeasurement=*/ true);
    } else {
      result = currentMeasurements;
    }
    return result;
  }

  /** Converts from observable callbacks to measurements. */
  static class MyObservableMeasurement
      implements ObservableLongMeasurement, ObservableDoubleMeasurement {
    private final Consumer<Measurement> storage;
    private final AttributesProcessor attributesProcessor;

    MyObservableMeasurement(
        Consumer<Measurement> storage, AttributesProcessor attributesProcessor) {
      this.storage = storage;
      this.attributesProcessor = attributesProcessor;
    }

    @Override
    public void observe(double value, Attributes attributes) {
      final Attributes realAttributes = attributesProcessor.process(attributes, Context.current());
      storage.accept(DoubleMeasurement.createNoContext(value, realAttributes));
    }

    @Override
    public void observe(double value) {
      observe(value, Attributes.empty());
    }

    @Override
    public void observe(long value, Attributes attributes) {
      final Attributes realAttributes = attributesProcessor.process(attributes, Context.current());
      storage.accept(LongMeasurement.createNoContext(value, realAttributes));
    }

    @Override
    public void observe(long value) {
      observe(value, Attributes.empty());
    }
  }
}
