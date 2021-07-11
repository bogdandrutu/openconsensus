/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleSumData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * An aggregator which returns Sum metrics.
 *
 * <p>This aggregator supports generating DELTA or CUMULATIVE sums, as well as monotonic or
 * non-monotonic.
 */
public class DoubleSumAggregator implements Aggregator<DoubleAccumulation> {
  private final SumConfig config;
  private final Resource resource;
  private final InstrumentationLibraryInfo instrumentationLibrary;
  private final ExemplarSampler sampler;

  /**
   * Construct a sum from measurements.
   *
   * @param config Configuration for the sum aggregation.
   * @param resource Resource to assocaiate metrics.
   * @param instrumentationLibrary InstrumentationLibrary to associate metrics.
   * @param sampler When/how to pull Exemplars.
   */
  public DoubleSumAggregator(
      SumConfig config,
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

  @Override
  public DoubleAccumulation asyncAccumulation(Measurement measurement) {
    // TODO: Use measurement as exemplar?
    return DoubleAccumulation.create(measurement.asDouble().getValue());
  }

  // Note:  Storage handle has high contention and need atomic increments.
  static class MyHandle extends SynchronousHandle<DoubleAccumulation> {
    private final DoubleAdder count = new DoubleAdder();

    MyHandle(ExemplarSampler sampler) {
      super(sampler);
    }

    @Override
    protected void doRecord(Measurement value) {
      count.add(value.asDouble().getValue());
    }

    @Override
    protected DoubleAccumulation doAccumulateThenReset(Iterable<Measurement> exemplars) {
      return DoubleAccumulation.create(count.sumThenReset(), exemplars);
    }
  }

  @Override
  public DoubleAccumulation merge(DoubleAccumulation current, DoubleAccumulation accumulated) {
    // Drop previous exemplars when aggregating.
    return DoubleAccumulation.create(
        current.getValue() + accumulated.getValue(), current.getExemplars());
  }

  @Override
  public MetricData buildMetric(
      Map<Attributes, DoubleAccumulation> accumulated,
      long startEpochNanos,
      long lastEpochNanos,
      long epochNanos) {
    return MetricData.createDoubleSum(
        resource,
        instrumentationLibrary,
        config.getName(),
        config.getDescription(),
        config.getUnit(),
        DoubleSumData.create(
            config.isMonotonic(),
            config.getTemporality(),
            MetricDataUtils.toDoublePointList(
                accumulated,
                config.getTemporality() == AggregationTemporality.CUMULATIVE
                    ? startEpochNanos
                    : lastEpochNanos,
                epochNanos)));
  }

  @Override
  public Map<Attributes, DoubleAccumulation> diffPrevious(
      Map<Attributes, DoubleAccumulation> previous,
      Map<Attributes, DoubleAccumulation> current,
      boolean isAsynchronousMeasurement) {
    if (config.getTemporality() == AggregationTemporality.CUMULATIVE
        && !isAsynchronousMeasurement) {

      // TODO: MERGE EVERYTHING!
      previous.forEach(
          (k, v) -> {
            if (current.containsKey(k)) {
              current.put(k, merge(current.get(k), v));
            } else {
              current.put(k, v);
            }
          });
    }
    // TODO: IF asynch + delta, then diff.

    return current;
  }
}
