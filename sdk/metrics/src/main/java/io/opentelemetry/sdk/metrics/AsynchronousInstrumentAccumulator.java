/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Labels;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;

final class AsynchronousInstrumentAccumulator extends AbstractAccumulator {
  private final ReentrantLock collectLock = new ReentrantLock();
  private final InstrumentProcessor<?> instrumentProcessor;
  private final Runnable metricUpdater;

  static <T> AsynchronousInstrumentAccumulator doubleAsynchronousAccumulator(
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      InstrumentDescriptor descriptor,
      @Nullable Consumer<BiConsumer<Double, Labels>> metricUpdater) {
    Aggregator<T> aggregator =
        getAggregator(meterProviderSharedState, meterSharedState, descriptor);
    InstrumentProcessor<T> instrumentProcessor =
        new InstrumentProcessor<>(aggregator, meterProviderSharedState.getStartEpochNanos());
    // TODO: Decide what to do with null updater.
    if (metricUpdater == null) {
      return new AsynchronousInstrumentAccumulator(instrumentProcessor, () -> {});
    }

    BiConsumer<Double, Labels> result =
        (value, labels) -> instrumentProcessor.batch(labels, aggregator.accumulateDouble(value));

    return new AsynchronousInstrumentAccumulator(
        instrumentProcessor, () -> metricUpdater.accept(result));
  }

  static <T> AsynchronousInstrumentAccumulator longAsynchronousAccumulator(
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      InstrumentDescriptor descriptor,
      @Nullable Consumer<BiConsumer<Long, Labels>> metricUpdater) {
    Aggregator<T> aggregator =
        getAggregator(meterProviderSharedState, meterSharedState, descriptor);
    InstrumentProcessor<T> instrumentProcessor =
        new InstrumentProcessor<>(aggregator, meterProviderSharedState.getStartEpochNanos());
    // TODO: Decide what to do with null updater.
    if (metricUpdater == null) {
      return new AsynchronousInstrumentAccumulator(instrumentProcessor, () -> {});
    }

    BiConsumer<Long, Labels> result =
        (value, labels) -> instrumentProcessor.batch(labels, aggregator.accumulateLong(value));

    return new AsynchronousInstrumentAccumulator(
        instrumentProcessor, () -> metricUpdater.accept(result));
  }

  private AsynchronousInstrumentAccumulator(
      InstrumentProcessor<?> instrumentProcessor, Runnable metricUpdater) {
    this.instrumentProcessor = instrumentProcessor;
    this.metricUpdater = metricUpdater;
  }

  @Override
  List<MetricData> collectAll(long epochNanos) {
    collectLock.lock();
    try {
      metricUpdater.run();
      return instrumentProcessor.completeCollectionCycle(epochNanos);
    } finally {
      collectLock.unlock();
    }
  }
}
