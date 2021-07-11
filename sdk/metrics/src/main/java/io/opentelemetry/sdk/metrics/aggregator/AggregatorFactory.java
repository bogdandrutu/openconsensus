/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.resources.Resource;
import javax.annotation.concurrent.Immutable;

/** Factory class for {@link Aggregator}. */
@Immutable
public interface AggregatorFactory<A> {

  /**
   * Create a factory for last-value accumulation.
   *
   * @param config Settings for last-value accumulation.
   * @return A factory which will instantiate aggregators that keep the last value.
   */
  public static AggregatorFactory<DoubleAccumulation> lastValue(final LastValueConfig config) {
    return (resource, instrumentationLibrary, startEpochNanos, sampler) ->
        new LastValueAggregator(config, resource, instrumentationLibrary, sampler);
  }

  /**
   * Create a factory for sum accumulation.
   *
   * @param config Settings for sum accumulation.
   * @return A factory which will instantiate aggregators that keep an integer sum.
   */
  public static AggregatorFactory<LongAccumulation> longSum(final SumConfig config) {
    return (resource, instrumentationLibrary, startEpochNanos, sampler) ->
        new LongSumAggregator(config, resource, instrumentationLibrary, sampler);
  }

  /**
   * Create a factory for sum accumulation.
   *
   * @param config Settings for sum accumulation.
   * @return A factory which will instantiate aggregators that keep a floating point sum.
   */
  public static AggregatorFactory<DoubleAccumulation> doubleSum(final SumConfig config) {
    return (resource, instrumentationLibrary, startEpochNanos, sampler) ->
        new DoubleSumAggregator(config, resource, instrumentationLibrary, sampler);
  }

  /**
   * Create a factory for histogram accumulation.
   *
   * @param config Settings for histogram accumulation.
   * @return A factory which will instantiate aggregators that generate histogram points.
   */
  public static AggregatorFactory<HistogramAccumulation> doubleHistogram(
      final HistogramConfig config) {
    return (resource, instrumentationLibrary, startEpochNanos, sampler) ->
        new DoubleHistogramAggregator(config, resource, instrumentationLibrary, sampler);
  }

  /**
   * Returns a new {@link Aggregator}.
   *
   * @param resource the Resource associated with the {@code Instrument} that will record
   *     measurements.
   * @param insturmentationLibrary the InstrumentationLibraryInfo associated with the {@code
   *     Instrument} that will record measurements.
   * @param startEpochNanos The start time of this application.
   * @param sampler How to sample {code Exemplar}s off measurements.
   * @return a new {@link Aggregator}.
   */
  Aggregator<A> create(
      Resource resource,
      InstrumentationLibraryInfo insturmentationLibrary,
      long startEpochNanos,
      ExemplarSampler sampler);
}
