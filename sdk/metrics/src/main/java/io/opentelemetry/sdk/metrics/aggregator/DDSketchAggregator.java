/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import com.datadoghq.sketch.ddsketch.SignedDDSketch;
import com.datadoghq.sketch.ddsketch.mapping.CubicallyInterpolatedMapping;
import com.datadoghq.sketch.ddsketch.store.UnboundedSizeDenseStore;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.sdk.metrics.data.MetricData.SummaryPoint;
import io.opentelemetry.sdk.metrics.data.MetricData.ValueAtPercentile;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public final class DDSketchAggregator extends AbstractAggregator {
  static final double PRECISION = 0.001;

  private static final AggregatorFactory AGGREGATOR_FACTORY =
      () ->
          new DDSketchAggregator(
              () ->
                  new SignedDDSketch(
                      new CubicallyInterpolatedMapping(PRECISION), UnboundedSizeDenseStore::new));

  public static AggregatorFactory getBalancedFactory() {
    return AGGREGATOR_FACTORY;
  }

  // Since DDSketch is not thread safe, this queue is used to buffer calls to record, reducing
  // the need to take a lock for every call. (With the downside of having to box the double.)
  private final ArrayBlockingQueue<Double> pendingValues = new ArrayBlockingQueue<>(64);

  private final Supplier<SignedDDSketch> sketchSupplier;

  @GuardedBy("lock")
  private volatile SignedDDSketch current;

  @GuardedBy("lock")
  private volatile double sum = 0;

  private final Object lock = new Object();

  private DDSketchAggregator(@Nonnull Supplier<SignedDDSketch> sketchSupplier) {
    this.sketchSupplier = sketchSupplier;
    current = sketchSupplier.get();
    assert current != null;
  }

  @Override
  void doMergeAndReset(Aggregator target) {
    SignedDDSketch oldSketch;
    double oldSum;
    synchronized (lock) {
      drain();
      oldSketch = current;
      oldSum = sum;
      current = sketchSupplier.get();
      sum = 0;
    }

    ((DDSketchAggregator) target).mergeFrom(oldSketch, oldSum);
  }

  private void mergeFrom(SignedDDSketch other, double oldSum) {
    synchronized (lock) {
      current.mergeWith(other);
      sum += oldSum;
    }
  }

  @Nullable
  @Override
  public SummaryPoint toPoint(long startEpochNanos, long epochNanos, Labels labels) {
    synchronized (lock) {
      drain();
      long count = (long) current.getCount();
      return count == 0
          ? null
          : SummaryPoint.create(
              startEpochNanos,
              epochNanos,
              labels,
              count,
              sum,
              Arrays.asList(
                  ValueAtPercentile.create(0.0, current.getMinValue()),
                  ValueAtPercentile.create(100.0, current.getMaxValue())));
    }
  }

  @Override
  protected void doRecordDouble(double value) {
    // autoboxing seems better than blocking
    if (!pendingValues.offer(value)) {
      // queue is full, so we must add the value directly to the sketch.
      synchronized (lock) {
        // We have a lock, might as well drain the queue.
        drain();
        current.accept(value);
        sum += value;
      }
    }
  }

  /** Must be called under lock. */
  private void drain() {
    assert Thread.holdsLock(lock);
    // Should already be under lock, but errorprone complains without the extra synchronized.
    synchronized (lock) {
      for (Double value = pendingValues.poll(); value != null; value = pendingValues.poll()) {
        current.accept(value);
        sum += value;
      }
    }
  }
}
