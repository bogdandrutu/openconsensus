/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Labels;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.view.AggregationConfiguration;
import io.opentelemetry.sdk.resources.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@code InstrumentProcessor} represents an internal instance of an {@code Accumulator} for a
 * specific {code Instrument}. It records individual measurements (via the {@code Aggregator}). It
 * batches together {@code Aggregator}s for the similar sets of labels.
 *
 * <p>An entire collection cycle must be protected by a lock. A collection cycle is defined by
 * multiple calls to {@code #batch(...)} followed by one {@code #completeCollectionCycle(...)};
 */
final class InstrumentProcessor<T> {
  private final InstrumentDescriptor descriptor;
  private final Aggregator<T> aggregator;
  private final Resource resource;
  private final InstrumentationLibraryInfo instrumentationLibraryInfo;
  private final Clock clock;
  private Map<Labels, T> accumulationMap;
  private long startEpochNanos;
  private final boolean delta;

  /**
   * Create a new {@link InstrumentProcessor} for use in metric recording aggregation.
   *
   * <p>"Delta" temporality means that all metrics that are generated are only for the most recent
   * collection interval.
   *
   * <p>"Cumulative" temporality means that all metrics that are generated will be considered for
   * the lifetime of the Instrument being aggregated.
   */
  static <T> InstrumentProcessor<T> createProcessor(
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      InstrumentDescriptor descriptor,
      AggregationConfiguration configuration) {
    Aggregator<T> aggregator =
        configuration.getAggregatorFactory().create(descriptor.getValueType());
    return new InstrumentProcessor<>(
        descriptor,
        aggregator,
        meterProviderSharedState.getResource(),
        meterSharedState.getInstrumentationLibraryInfo(),
        meterProviderSharedState.getClock(),
        isDelta(configuration.getTemporality()));
  }

  private InstrumentProcessor(
      InstrumentDescriptor descriptor,
      Aggregator<T> aggregator,
      Resource resource,
      InstrumentationLibraryInfo instrumentationLibraryInfo,
      Clock clock,
      boolean delta) {
    this.descriptor = descriptor;
    this.aggregator = aggregator;
    this.resource = resource;
    this.instrumentationLibraryInfo = instrumentationLibraryInfo;
    this.clock = clock;
    this.delta = delta;
    this.accumulationMap = new HashMap<>();
    startEpochNanos = clock.now();
  }

  /**
   * Batches multiple entries together that are part of the same metric. It may remove labels from
   * the {@link Labels} and merge aggregations together.
   *
   * @param labelSet the {@link Labels} associated with this {@code Aggregator}.
   * @param accumulation the accumulation produced by this instrument.
   */
  void batch(Labels labelSet, T accumulation) {
    T currentAccumulation = accumulationMap.get(labelSet);
    if (currentAccumulation == null) {
      accumulationMap.put(labelSet, accumulation);
      return;
    }
    accumulationMap.put(labelSet, aggregator.merge(currentAccumulation, accumulation));
  }

  /**
   * Ends the current collection cycle and appends the list of metrics batched in this {@code
   * InstrumentProcessor} to the {@code output}.
   *
   * <p>There may be more than one MetricData in case a multi aggregator is configured.
   *
   * <p>Based on the configured options this method may reset the internal state to produce deltas,
   * or keep the internal state to produce cumulative metrics.
   *
   * @param output appends the list of metrics batched in this {@code InstrumentProcessor}.
   */
  void completeCollectionCycle(List<MetricData> output) {
    long epochNanos = clock.now();
    if (accumulationMap.isEmpty()) {
      return;
    }

    MetricData metricData =
        aggregator.toMetricData(
            resource,
            instrumentationLibraryInfo,
            descriptor,
            accumulationMap,
            startEpochNanos,
            epochNanos);

    if (delta) {
      startEpochNanos = epochNanos;
      accumulationMap = new HashMap<>();
    }

    if (metricData != null) {
      output.add(metricData);
    }
  }

  private static boolean isDelta(MetricData.AggregationTemporality temporality) {
    switch (temporality) {
      case CUMULATIVE:
        return false;
      case DELTA:
        return true;
    }
    throw new IllegalStateException("unsupported Temporality: " + temporality);
  }

  Aggregator<T> getAggregator() {
    return this.aggregator;
  }
}
