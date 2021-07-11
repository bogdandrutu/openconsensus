/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.internal.GuardedBy;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Accumulates measurements into a Histogram metric.
 *
 * <p>This aggregator supports {@code DoubleMeasurement} and {@code LongMeasurement} inputs.
 */
public class DoubleHistogramAggregator implements Aggregator<HistogramAccumulation> {
  private final HistogramConfig config;
  private final Resource resource;
  private final InstrumentationLibraryInfo instrumentationLibrary;
  private final ExemplarSampler sampler;

  // a cache for converting to MetricData
  private final List<Double> boundaryList;

  /**
   * Construct a histogram from measurements.
   *
   * @param config Configuration for the histogram aggregation.
   * @param resource Resource to assocaiate metrics.
   * @param instrumentationLibrary InstrumentationLibrary to associate metrics.
   * @param sampler When/how to pull Exemplars.
   */
  public DoubleHistogramAggregator(
      HistogramConfig config,
      Resource resource,
      InstrumentationLibraryInfo instrumentationLibrary,
      ExemplarSampler sampler) {
    this.config = config;
    this.resource = resource;
    this.instrumentationLibrary = instrumentationLibrary;
    this.sampler = sampler;
    List<Double> boundaryList = new ArrayList<>(this.config.getBoundaries().length);
    for (double v : this.config.getBoundaries()) {
      boundaryList.add(v);
    }
    this.boundaryList = Collections.unmodifiableList(boundaryList);
  }

  // Benchmark shows that linear search performs better than binary search with ordinary
  // buckets.
  private static int findBucketIndex(double[] boundaries, double value) {
    for (int i = 0; i < boundaries.length; ++i) {
      if (value <= boundaries[i]) {
        return i;
      }
    }
    return boundaries.length;
  }

  private static double valueOf(Measurement measurement) {
    return measurement.asDouble().getValue();
  }

  @Override
  public SynchronousHandle<HistogramAccumulation> createStreamStorage() {
    return new MyHandle(config.getBoundaries(), sampler);
  }

  // Note:  Storage handle has high contention and need atomic increments.
  static class MyHandle extends SynchronousHandle<HistogramAccumulation> {
    // read-only
    private final double[] boundaries;

    @GuardedBy("lock")
    private double sum;

    @GuardedBy("lock")
    private final long[] counts;

    private final ReentrantLock lock = new ReentrantLock();

    MyHandle(double[] boundaries, ExemplarSampler sampler) {
      super(sampler);
      this.boundaries = boundaries;
      this.counts = new long[this.boundaries.length + 1];
      this.sum = 0;
    }

    @Override
    protected void doRecord(Measurement measurement) {
      double value = valueOf(measurement);
      int bucketIndex = findBucketIndex(this.boundaries, value);

      lock.lock();
      try {
        this.sum += value;
        this.counts[bucketIndex]++;
      } finally {
        lock.unlock();
      }
    }

    @Override
    protected HistogramAccumulation doAccumulateThenReset(Iterable<Measurement> exemplars) {
      lock.lock();
      try {
        HistogramAccumulation acc =
            HistogramAccumulation.create(sum, Arrays.copyOf(counts, counts.length), exemplars);
        this.sum = 0;
        Arrays.fill(this.counts, 0);
        return acc;
      } finally {
        lock.unlock();
      }
    }
  }

  @Override
  public HistogramAccumulation asyncAccumulation(Measurement measurement) {
    double value = valueOf(measurement);
    int bucketIndex = findBucketIndex(config.getBoundaries(), value);
    long[] counts = new long[config.getBoundaries().length + 1];
    counts[bucketIndex] = 1;
    return HistogramAccumulation.create(value, counts, Collections.emptyList());
  }

  @Override
  public final HistogramAccumulation merge(
      HistogramAccumulation current, HistogramAccumulation accumulated) {
    long[] mergedCounts = new long[current.getCounts().length];
    for (int i = 0; i < current.getCounts().length; ++i) {
      mergedCounts[i] = current.getCounts()[i] + accumulated.getCounts()[i];
    }

    return HistogramAccumulation.create(
        current.getSum() + accumulated.getSum(),
        mergedCounts,
        // We drop old exemplars when pulling new ones.
        current.getExemplars());
  }

  @Override
  public MetricData buildMetric(
      Map<Attributes, HistogramAccumulation> accumulated,
      long startEpochNanos,
      long lastEpochNanos,
      long epochNanos) {
    return MetricData.createDoubleHistogram(
        resource,
        instrumentationLibrary,
        config.getName(),
        config.getDescription(),
        config.getUnit(),
        DoubleHistogramData.create(
            config.getTemporality(),
            MetricDataUtils.toDoubleHistogramPointList(
                accumulated,
                config.getTemporality() == AggregationTemporality.CUMULATIVE
                    ? startEpochNanos
                    : lastEpochNanos,
                epochNanos,
                boundaryList)));
  }

  @Override
  public Map<Attributes, HistogramAccumulation> diffPrevious(
      Map<Attributes, HistogramAccumulation> previous,
      Map<Attributes, HistogramAccumulation> current,
      boolean isAsynchronousMeasurement) {
    // TODO: Share this.
    if (config.getTemporality() == AggregationTemporality.CUMULATIVE
        && !isAsynchronousMeasurement) {
      previous.forEach(
          (k, v) -> {
            if (current.containsKey(k)) {
              current.put(k, merge(current.get(k), v));
            } else {
              current.put(k, v);
            }
          });
    }

    return current;
  }
}
