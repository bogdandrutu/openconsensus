/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument.Observation;
import io.opentelemetry.metrics.BatchObserver;
import io.opentelemetry.metrics.DoubleSumObserver;
import io.opentelemetry.metrics.DoubleUpDownSumObserver;
import io.opentelemetry.metrics.DoubleValueObserver;
import io.opentelemetry.metrics.LongSumObserver;
import io.opentelemetry.metrics.LongUpDownSumObserver;
import io.opentelemetry.metrics.LongValueObserver;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.aggregator.NoopAggregator;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricData.Point;
import io.opentelemetry.sdk.metrics.view.Aggregations;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** Implementation of the {@link BatchObserver}. */
final class BatchObserverSdk extends AbstractInstrument implements BatchObserver {

  private final MeterSdk meter;
  private BatchObserverFunction function;
  private final BatchObserverBatcher batcher;
  private final ReentrantLock collectLock = new ReentrantLock();

  BatchObserverSdk(
      InstrumentDescriptor descriptor,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      MeterSdk meterSdk,
      BatchObserverBatcher batcher) {
    super(descriptor, meterProviderSharedState, meterSharedState, new ActiveBatcher(batcher));
    this.meter = meterSdk;
    this.batcher = batcher;
  }

  @Override
  public void setFunction(BatchObserverFunction function) {
    this.function = function;
  }

  @Override
  public DoubleSumObserver.Builder doubleSumObserverBuilder(String name) {
    return this.meter.doubleSumObserverBuilder(name);
  }

  @Override
  public LongSumObserver.Builder longSumObserverBuilder(String name) {
    return this.meter.longSumObserverBuilder(name);
  }

  @Override
  public DoubleUpDownSumObserver.Builder doubleUpDownSumObserverBuilder(String name) {
    return this.meter.doubleUpDownSumObserverBuilder(name);
  }

  @Override
  public LongUpDownSumObserver.Builder longUpDownSumObserverBuilder(String name) {
    return this.meter.longUpDownSumObserverBuilder(name);
  }

  @Override
  public DoubleValueObserver.Builder doubleValueObserverBuilder(String name) {
    return this.meter.doubleValueObserverBuilder(name);
  }

  @Override
  public LongValueObserver.Builder longValueObserverBuilder(String name) {
    return this.meter.longValueObserverBuilder(name);
  }

  @Override
  List<MetricData> collectAll() {
    final List<MetricData> metricData = new ArrayList<>();
    if (function == null) {
      return Collections.emptyList();
    }
    collectLock.lock();
    try {
      function.observe(
          (labels, observations) -> {
            batcher.setLabels(labels);
            for (Observation observation : observations) {
              batcher.batch((SdkObservation) observation);
            }
            metricData.addAll(batcher.completeCollectionCycle());
          });
    } finally {
      collectLock.unlock();
    }
    return Collections.unmodifiableList(metricData);
  }

  interface SdkObservation extends Observation {
    Aggregator record();

    InstrumentDescriptor getObservationDescriptor();
  }

  /** Creates a new instance of the {@link BatchObserverSdk}. */
  static BatchObserverSdk newBatchObserverSdk(
      InstrumentDescriptor descriptor,
      MeterSdk meterSdk,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState) {

    BatchObserverBatcher batcher =
        new BatchObserverBatcher(
            meterProviderSharedState.getResource(),
            meterSharedState.getInstrumentationLibraryInfo(),
            NoopAggregator.getFactory(),
            meterProviderSharedState.getClock());
    return new BatchObserverSdk(
        descriptor, meterProviderSharedState, meterSharedState, meterSdk, batcher);
  }

  /** Internal batcher used by the {@link BatchObserver}. */
  private static final class BatchObserverBatcher implements Batcher {
    private final Resource resource;
    private final InstrumentationLibraryInfo instrumentationLibraryInfo;
    private final Clock clock;
    private final AggregatorFactory aggregatorFactory;
    private List<Report> reportList;
    private long startEpochNanos;
    private Labels labels;

    private BatchObserverBatcher(
        Resource resource,
        InstrumentationLibraryInfo instrumentationLibraryInfo,
        AggregatorFactory aggregatorFactory,
        Clock clock) {
      this.resource = resource;
      this.instrumentationLibraryInfo = instrumentationLibraryInfo;
      this.clock = clock;
      this.aggregatorFactory = aggregatorFactory;
      this.reportList = new ArrayList<>();
      startEpochNanos = clock.now();
    }

    private void setLabels(Labels labels) {
      this.labels = labels;
    }

    @Override
    public final Aggregator getAggregator() {
      return aggregatorFactory.getAggregator();
    }

    @Override
    public final void batch(Labels labelSet, Aggregator aggregator, boolean unmappedAggregator) {}

    private void batch(SdkObservation observation) {
      this.reportList.add(new Report(observation.getObservationDescriptor(), observation.record()));
    }

    @Override
    public final List<MetricData> completeCollectionCycle() {
      List<MetricData> points = new ArrayList<>(reportList.size());
      long epochNanos = clock.now();
      for (Report report : reportList) {
        Point point = report.getAggregator().toPoint(startEpochNanos, epochNanos, this.labels);
        InstrumentDescriptor descriptor = report.getDescriptor();
        if (point != null) {
          points.add(
              MetricData.create(
                  resource,
                  instrumentationLibraryInfo,
                  descriptor.getName(),
                  descriptor.getDescription(),
                  descriptor.getUnit(),
                  getRegisteredAggregation(descriptor),
                  Collections.singletonList(point)));
        }
      }
      startEpochNanos = epochNanos;
      reportList = new ArrayList<>();
      return points;
    }

    private static class Report {
      private final InstrumentDescriptor descriptor;
      private final Aggregator aggregator;

      Report(InstrumentDescriptor descriptor, Aggregator aggregator) {
        this.descriptor = descriptor;
        this.aggregator = aggregator;
      }

      public InstrumentDescriptor getDescriptor() {
        return descriptor;
      }

      public Aggregator getAggregator() {
        return aggregator;
      }
    }
  }

  private static MetricData.Type getRegisteredAggregation(InstrumentDescriptor descriptor) {
    switch (descriptor.getType()) {
      case COUNTER:
      case UP_DOWN_COUNTER:
        return Aggregations.sum()
            .getDescriptorType(descriptor.getType(), descriptor.getValueType());
      case VALUE_RECORDER:
      case VALUE_OBSERVER:
      case BATCH_OBSERVER:
        return Aggregations.minMaxSumCount()
            .getDescriptorType(descriptor.getType(), descriptor.getValueType());
      case SUM_OBSERVER:
      case UP_DOWN_SUM_OBSERVER:
        return Aggregations.lastValue()
            .getDescriptorType(descriptor.getType(), descriptor.getValueType());
    }
    throw new IllegalArgumentException("Unknown descriptor type: " + descriptor.getType());
  }
}
