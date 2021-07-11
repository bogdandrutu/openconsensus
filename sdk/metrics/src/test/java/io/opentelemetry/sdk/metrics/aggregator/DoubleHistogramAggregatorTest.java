/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.instrument.DoubleMeasurement;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

public class DoubleHistogramAggregatorTest {
  private static final double[] boundaries = new double[] {10.0, 100.0, 1000.0};
  private static final DoubleHistogramAggregator aggregator =
      new DoubleHistogramAggregator(
          HistogramConfig.builder()
              .setName("name")
              .setDescription("description")
              .setUnit("unit")
              .setBoundaries(boundaries)
              .setTemporality(AggregationTemporality.DELTA)
              .build(),
          Resource.getDefault(),
          InstrumentationLibraryInfo.empty(),
          ExemplarSampler.NEVER);

  @Test
  void createStreamStorage() {
    assertThat(aggregator.createStreamStorage())
        .isInstanceOf(DoubleHistogramAggregator.MyHandle.class);
  }

  @Test
  void testRecordings() {
    SynchronousHandle<HistogramAccumulation> aggregatorHandle = aggregator.createStreamStorage();
    aggregatorHandle.record(raw(20));
    aggregatorHandle.record(raw(5));
    aggregatorHandle.record(raw(150));
    aggregatorHandle.record(raw(2000));
    assertThat(aggregatorHandle.accumulateThenReset())
        .isEqualTo(
            HistogramAccumulation.create(2175, new long[] {1, 1, 1, 1}, Collections.emptyList()));
  }

  @Test
  void toAccumulationAndReset() {
    SynchronousHandle<HistogramAccumulation> aggregatorHandle = aggregator.createStreamStorage();
    assertThat(aggregatorHandle.accumulateThenReset()).isNull();

    aggregatorHandle.record(raw(100));
    assertThat(aggregatorHandle.accumulateThenReset())
        .isEqualTo(
            HistogramAccumulation.create(100, new long[] {0, 1, 0, 0}, Collections.emptyList()));
    assertThat(aggregatorHandle.accumulateThenReset()).isNull();

    aggregatorHandle.record(raw(0));
    assertThat(aggregatorHandle.accumulateThenReset())
        .isEqualTo(
            HistogramAccumulation.create(0, new long[] {1, 0, 0, 0}, Collections.emptyList()));
    assertThat(aggregatorHandle.accumulateThenReset()).isNull();
  }

  @Test
  void buildMetric() {
    SynchronousHandle<HistogramAccumulation> aggregatorHandle = aggregator.createStreamStorage();
    aggregatorHandle.record(raw(10d));

    MetricData metricData =
        aggregator.buildMetric(
            Collections.singletonMap(Attributes.empty(), aggregatorHandle.accumulateThenReset()),
            0,
            10,
            100);
    assertThat(metricData).isNotNull();
    assertThat(metricData.getType()).isEqualTo(MetricDataType.HISTOGRAM);
    assertThat(metricData.getDoubleHistogramData().getAggregationTemporality())
        .isEqualTo(AggregationTemporality.DELTA);
  }

  @Test
  void testMultithreadedUpdates() throws InterruptedException {
    final SynchronousHandle<HistogramAccumulation> aggregatorHandle =
        aggregator.createStreamStorage();
    final Histogram summarizer = new Histogram();
    final ImmutableList<Long> updates =
        ImmutableList.of(1L, 2L, 3L, 5L, 7L, 11L, 13L, 17L, 19L, 23L);
    final int numberOfThreads = updates.size();
    final int numberOfUpdates = 10000;
    final ThreadPoolExecutor executor =
        (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfThreads);

    executor.invokeAll(
        updates.stream()
            .map(
                v ->
                    Executors.callable(
                        () -> {
                          for (int j = 0; j < numberOfUpdates; j++) {
                            aggregatorHandle.record(raw(v));
                            if (ThreadLocalRandom.current().nextInt(10) == 0) {
                              summarizer.process(aggregatorHandle.accumulateThenReset());
                            }
                          }
                        }))
            .collect(Collectors.toList()));

    // make sure everything gets merged when all the aggregation is done.
    summarizer.process(aggregatorHandle.accumulateThenReset());

    assertThat(summarizer.accumulation)
        .isEqualTo(
            HistogramAccumulation.create(
                1010000, new long[] {50000, 50000, 0, 0}, Collections.emptyList()));
  }

  private static final class Histogram {
    private final Object mutex = new Object();

    @Nullable private HistogramAccumulation accumulation;

    void process(@Nullable HistogramAccumulation other) {
      if (other == null) {
        return;
      }

      synchronized (mutex) {
        if (accumulation == null) {
          accumulation = other;
          return;
        }
        accumulation = aggregator.merge(accumulation, other);
      }
    }
  }
  /** DSL for cleaning up tests. */
  private static Measurement raw(long value) {
    return LongMeasurement.createNoContext(value, Attributes.empty());
  }

  private static Measurement raw(double value) {
    return DoubleMeasurement.createNoContext(value, Attributes.empty());
  }
}
