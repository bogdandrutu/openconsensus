/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.common.InstrumentValueType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

public class DoubleHistogramAggregatorTest {
  private static final double[] boundaries = new double[] {10.0, 100.0, 1000.0};
  private static final DoubleHistogramAggregator aggregator =
      new DoubleHistogramAggregator(
          Resource.getDefault(),
          InstrumentationLibraryInfo.empty(),
          InstrumentDescriptor.create(
              "name",
              "description",
              "unit",
              InstrumentType.VALUE_RECORDER,
              InstrumentValueType.LONG),
          boundaries,
          /* stateful= */ false);

  @Test
  void createHandle() {
    assertThat(aggregator.createHandle()).isInstanceOf(DoubleHistogramAggregator.Handle.class);
  }

  @Test
  void testRecordings() {
    AggregatorHandle<HistogramAccumulation> aggregatorHandle = aggregator.createHandle();
    aggregatorHandle.recordLong(20);
    aggregatorHandle.recordLong(5);
    aggregatorHandle.recordLong(150);
    aggregatorHandle.recordLong(2000);
    assertThat(aggregatorHandle.accumulateThenReset())
        .isEqualTo(HistogramAccumulation.create(2175, new long[] {1, 1, 1, 1}));
  }

  @Test
  void toAccumulationAndReset() {
    AggregatorHandle<HistogramAccumulation> aggregatorHandle = aggregator.createHandle();
    assertThat(aggregatorHandle.accumulateThenReset()).isNull();

    aggregatorHandle.recordLong(100);
    assertThat(aggregatorHandle.accumulateThenReset())
        .isEqualTo(HistogramAccumulation.create(100, new long[] {0, 1, 0, 0}));
    assertThat(aggregatorHandle.accumulateThenReset()).isNull();

    aggregatorHandle.recordLong(0);
    assertThat(aggregatorHandle.accumulateThenReset())
        .isEqualTo(HistogramAccumulation.create(0, new long[] {1, 0, 0, 0}));
    assertThat(aggregatorHandle.accumulateThenReset()).isNull();
  }

  @Test
  void accumulateData() {
    assertThat(aggregator.accumulateDouble(11.1))
        .isEqualTo(HistogramAccumulation.create(11.1, new long[] {0, 1, 0, 0}));
    assertThat(aggregator.accumulateLong(10))
        .isEqualTo(HistogramAccumulation.create(10.0, new long[] {1, 0, 0, 0}));
  }

  @Test
  void toMetricData() {
    AggregatorHandle<HistogramAccumulation> aggregatorHandle = aggregator.createHandle();
    aggregatorHandle.recordLong(10);

    MetricData metricData =
        aggregator.toMetricData(
            Collections.singletonMap(Labels.empty(), aggregatorHandle.accumulateThenReset()),
            0,
            10,
            100);
    assertThat(metricData).isNotNull();
    assertThat(metricData.getType()).isEqualTo(MetricDataType.HISTOGRAM);
    assertThat(metricData.getDoubleHistogramData().getAggregationTemporality())
        .isEqualTo(AggregationTemporality.DELTA);
  }

  @Test
  void testHistogramCounts() {
    assertThat(aggregator.accumulateDouble(1.1).getCounts().length)
        .isEqualTo(boundaries.length + 1);
    assertThat(aggregator.accumulateLong(1).getCounts().length).isEqualTo(boundaries.length + 1);

    AggregatorHandle<HistogramAccumulation> aggregatorHandle = aggregator.createHandle();
    aggregatorHandle.recordDouble(1.1);
    HistogramAccumulation histogramAccumulation = aggregatorHandle.accumulateThenReset();
    assertThat(histogramAccumulation).isNotNull();
    assertThat(histogramAccumulation.getCounts().length).isEqualTo(boundaries.length + 1);
  }

  @Test
  void testMultithreadedUpdates() throws Exception {
    final AggregatorHandle<HistogramAccumulation> aggregatorHandle = aggregator.createHandle();
    final Histogram summarizer = new Histogram();
    int numberOfThreads = 10;
    final long[] updates = new long[] {1, 2, 3, 5, 7, 11, 13, 17, 19, 23};
    final int numberOfUpdates = 1000;
    final CountDownLatch starter = new CountDownLatch(numberOfThreads);
    List<Thread> workers = new ArrayList<>();
    for (int i = 0; i < numberOfThreads; i++) {
      final int index = i;
      Thread t =
          new Thread(
              () -> {
                long update = updates[index];
                try {
                  starter.await();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
                for (int j = 0; j < numberOfUpdates; j++) {
                  aggregatorHandle.recordLong(update);
                  if (ThreadLocalRandom.current().nextInt(10) == 0) {
                    summarizer.process(aggregatorHandle.accumulateThenReset());
                  }
                }
              });
      workers.add(t);
      t.start();
    }
    for (int i = 0; i <= numberOfThreads; i++) {
      starter.countDown();
    }

    for (Thread worker : workers) {
      worker.join();
    }
    // make sure everything gets merged when all the aggregation is done.
    summarizer.process(aggregatorHandle.accumulateThenReset());

    assertThat(summarizer.accumulation)
        .isEqualTo(HistogramAccumulation.create(101000, new long[] {5000, 5000, 0, 0}));
  }

  private static final class Histogram {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @GuardedBy("lock")
    @Nullable
    private HistogramAccumulation accumulation;

    void process(@Nullable HistogramAccumulation other) {
      if (other == null) {
        return;
      }
      lock.writeLock().lock();
      try {
        if (accumulation == null) {
          accumulation = other;
          return;
        }
        accumulation = aggregator.merge(accumulation, other);
      } finally {
        lock.writeLock().unlock();
      }
    }
  }
}