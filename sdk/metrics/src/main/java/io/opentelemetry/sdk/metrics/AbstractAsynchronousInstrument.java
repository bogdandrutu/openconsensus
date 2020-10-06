/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument;
import io.opentelemetry.sdk.metrics.BatchObserverSdk.SdkObservation;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

abstract class AbstractAsynchronousInstrument<T extends AsynchronousInstrument.Result>
    extends AbstractInstrument implements AsynchronousInstrument<T> {
  @Nullable private volatile Callback<T> metricUpdater = null;
  private final ReentrantLock collectLock = new ReentrantLock();

  AbstractAsynchronousInstrument(
      InstrumentDescriptor descriptor,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      ActiveBatcher activeBatcher) {
    super(descriptor, meterProviderSharedState, meterSharedState, activeBatcher);
  }

  @Override
  List<MetricData> collectAll() {
    Callback<T> currentMetricUpdater = metricUpdater;
    if (currentMetricUpdater == null) {
      return Collections.emptyList();
    }
    collectLock.lock();
    try {
      final ActiveBatcher activeBatcher = getActiveBatcher();
      currentMetricUpdater.update(newResult(activeBatcher));
      return activeBatcher.completeCollectionCycle();
    } finally {
      collectLock.unlock();
    }
  }

  @Override
  public void setCallback(Callback<T> callback) {
    this.metricUpdater = Objects.requireNonNull(callback, "callback");
  }

  abstract T newResult(ActiveBatcher activeBatcher);

  abstract static class Builder<B extends AbstractInstrument.Builder<?>>
      extends AbstractInstrument.Builder<B> {
    Builder(
        String name,
        MeterProviderSharedState meterProviderSharedState,
        MeterSharedState meterSharedState,
        MeterSdk meterSdk) {
      super(name, meterProviderSharedState, meterSharedState, meterSdk);
    }
  }

  static class AbstractLongAsynchronousInstrument extends AbstractAsynchronousInstrument<LongResult>
      implements LongObservation, SdkObservation {
    long observedValue = 0;

    AbstractLongAsynchronousInstrument(
        InstrumentDescriptor descriptor,
        MeterProviderSharedState meterProviderSharedState,
        MeterSharedState meterSharedState,
        ActiveBatcher activeBatcher) {
      super(descriptor, meterProviderSharedState, meterSharedState, activeBatcher);
    }

    @Override
    LongResultSdk newResult(ActiveBatcher activeBatcher) {
      return new LongResultSdk(activeBatcher);
    }

    @Override
    public Observation observation(long observation) {
      this.observedValue = observation;
      return this;
    }

    @Override
    public Aggregator record() {
      Aggregator aggregator = this.getActiveBatcher().getAggregator();
      aggregator.recordLong(this.observedValue);
      return aggregator;
    }

    @Override
    public InstrumentDescriptor getObservationDescriptor() {
      return this.getDescriptor();
    }

    private static final class LongResultSdk implements LongResult {

      private final ActiveBatcher activeBatcher;

      private LongResultSdk(ActiveBatcher activeBatcher) {
        this.activeBatcher = activeBatcher;
      }

      @Override
      public void observe(long sum, Labels labels) {
        Aggregator aggregator = activeBatcher.getAggregator();
        aggregator.recordLong(sum);
        activeBatcher.batch(labels, aggregator, /* mappedAggregator= */ false);
      }
    }
  }

  static class AbstractDoubleAsynchronousInstrument
      extends AbstractAsynchronousInstrument<DoubleResult>
      implements DoubleObservation, SdkObservation {
    double observedValue = 0;

    AbstractDoubleAsynchronousInstrument(
        InstrumentDescriptor descriptor,
        MeterProviderSharedState meterProviderSharedState,
        MeterSharedState meterSharedState,
        ActiveBatcher activeBatcher) {
      super(descriptor, meterProviderSharedState, meterSharedState, activeBatcher);
    }

    @Override
    DoubleResultSdk newResult(ActiveBatcher activeBatcher) {
      return new DoubleResultSdk(activeBatcher);
    }

    @Override
    public Observation observation(double observation) {
      this.observedValue = observation;
      return this;
    }

    @Override
    public Aggregator record() {
      Aggregator aggregator = this.getActiveBatcher().getAggregator();
      aggregator.recordDouble(this.observedValue);
      return aggregator;
    }

    @Override
    public InstrumentDescriptor getObservationDescriptor() {
      return this.getDescriptor();
    }

    private static final class DoubleResultSdk implements DoubleResult {

      private final ActiveBatcher activeBatcher;

      private DoubleResultSdk(ActiveBatcher activeBatcher) {
        this.activeBatcher = activeBatcher;
      }

      @Override
      public void observe(double sum, Labels labels) {
        Aggregator aggregator = activeBatcher.getAggregator();
        aggregator.recordDouble(sum);
        activeBatcher.batch(labels, aggregator, /* mappedAggregator= */ false);
      }
    }
  }
}
