/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.DoubleGaugeData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Aggregator that aggregates recorded values by storing the last recorded value.
 *
 * <p>Limitation: The current implementation does not store a time when the value was recorded, so
 * merging multiple LastValueAggregators will not preserve the ordering of records. This is not a
 * problem because LastValueAggregator is currently only available for Observers which record all
 * values once.
 */
public class LastValueAggregator implements Aggregator<DoubleAccumulation> {
  private final LastValueConfig config;
  private final Resource resource;
  private final InstrumentationLibraryInfo instrumentationLibrary;
  private final ExemplarSampler sampler;

  /**
   * Construct a gauge from measurements.
   *
   * @param config Configuration for the gauge aggregation.
   * @param resource Resource to assocaiate metrics.
   * @param instrumentationLibrary InstrumentationLibrary to associate metrics.
   * @param sampler When/how to pull Exemplars.
   */
  public LastValueAggregator(
      LastValueConfig config,
      Resource resource,
      InstrumentationLibraryInfo instrumentationLibrary,
      ExemplarSampler sampler) {
    this.config = config;
    this.resource = resource;
    this.instrumentationLibrary = instrumentationLibrary;
    this.sampler = sampler;
  }

  @Override
  public SynchronousHandle<DoubleAccumulation> createStreamStorage() {
    return new MyHandle(sampler);
  }

  // Note:  Storage handle has high contention and need atomic increments.
  static class MyHandle extends SynchronousHandle<DoubleAccumulation> {
    private final AtomicReference<Double> latest = new AtomicReference<>(null);

    MyHandle(ExemplarSampler sampler) {
      super(sampler);
    }

    @Override
    protected void doRecord(Measurement value) {
      latest.lazySet(value.asDouble().getValue());
    }

    @Override
    protected DoubleAccumulation doAccumulateThenReset(Iterable<Measurement> exemplars) {
      Double result = latest.getAndSet(null);
      if (result == null) {
        return null;
      }
      return DoubleAccumulation.create(result, exemplars);
    }
  }

  @Override
  public DoubleAccumulation asyncAccumulation(Measurement measurement) {
    return DoubleAccumulation.create(measurement.asDouble().getValue());
  }

  @Override
  public DoubleAccumulation merge(DoubleAccumulation current, DoubleAccumulation accumulated) {
    return current;
  }

  @Override
  public MetricData buildMetric(
      Map<Attributes, DoubleAccumulation> accumulated,
      long startEpochNanos,
      long lastEpochNanos,
      long epochNanos) {
    return MetricData.createDoubleGauge(
        resource,
        instrumentationLibrary,
        config.getName(),
        config.getDescription(),
        config.getUnit(),
        DoubleGaugeData.create(
            MetricDataUtils.toDoublePointList(accumulated, startEpochNanos, epochNanos)));
  }

  @Override
  public Map<Attributes, DoubleAccumulation> diffPrevious(
      Map<Attributes, DoubleAccumulation> previous,
      Map<Attributes, DoubleAccumulation> current,
      boolean isAsynchronousMeasurement) {
    return current;
  }
}
