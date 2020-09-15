/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument.Callback;
import io.opentelemetry.metrics.AsynchronousInstrument.Observation;
import io.opentelemetry.metrics.AsynchronousInstrument.ObservationType;
import io.opentelemetry.metrics.BatchObserver;
import io.opentelemetry.metrics.BatchRecorder;
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
import io.opentelemetry.sdk.metrics.data.MetricData.Descriptor;
import io.opentelemetry.sdk.metrics.data.MetricData.Point;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Minimal implementation of the {@link BatchRecorder} that simply redirects the calls to the
 * instruments.
 *
 * <p>TODO: Add an async queue processing to process batch records.
 */
final class BatchObserverSdk extends AbstractInstrument implements BatchObserver {

  private final MeterSdk meter;
  private final BatchObserverFunction function;
  private final BatchObserverBatcher batcher;
  private final ReentrantLock collectLock = new ReentrantLock();

  BatchObserverSdk(
      InstrumentDescriptor descriptor,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      MeterSdk meterSdk,
      BatchObserverBatcher batcher,
      BatchObserverFunction function) {
    super(descriptor, meterProviderSharedState, meterSharedState, new ActiveBatcher(batcher));
    this.meter = meterSdk;
    this.batcher = batcher;
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
          new BatchObserverResult() {
            @Override
            public void observe(Labels labels, Observation... observations) {
              batcher.setLabels(labels);
              for (Observation observation : observations) {
                ObservationType type = observation.getType();
                Aggregator aggregator;
                Descriptor descriptor;
                if (type == ObservationType.LONG_OBSERVATION) {
                  LongObservation longObservation = (LongObservation) observation;
                  aggregator = longObservation.getAggregator();
                  aggregator.recordLong(longObservation.getValue());
                  descriptor = longObservation.getDescription();
                } else {
                  DoubleObservation doubleObservation = (DoubleObservation) observation;
                  aggregator = doubleObservation.getAggregator();
                  aggregator.recordDouble(doubleObservation.getValue());
                  descriptor = doubleObservation.getDescription();
                }
                batcher.batch(descriptor, aggregator);
              }
              metricData.addAll(batcher.completeCollectionCycle());
            }
          });
    } finally {
      collectLock.unlock();
    }
    return Collections.unmodifiableList(metricData);
  }

  /** The result for the {@link Callback}. */
  interface LongObservation extends Observation {
    long getValue();

    Aggregator getAggregator();

    Descriptor getDescription();
  }

  /** The result for the {@link Callback}. */
  interface DoubleObservation extends Observation {
    double getValue();

    Aggregator getAggregator();

    Descriptor getDescription();
  }

  public static BatchObserverSdk newBatchObserverSdk(
      InstrumentDescriptor descriptor,
      MeterSdk meterSdk,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      BatchObserverFunction function) {

    BatchObserverBatcher batcher =
        new BatchObserverBatcher(
            meterProviderSharedState.getResource(),
            meterSharedState.getInstrumentationLibraryInfo(),
            NoopAggregator.getFactory(),
            meterProviderSharedState.getClock());
    return new BatchObserverSdk(
        descriptor, meterProviderSharedState, meterSharedState, meterSdk, batcher, function);
  }

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

    public void setLabels(Labels labels) {
      this.labels = labels;
    }

    @Override
    public final Aggregator getAggregator() {
      return aggregatorFactory.getAggregator();
    }

    @Override
    public Descriptor getDescriptor() {
      return null;
    }

    @Override
    public final void batch(Labels labelSet, Aggregator aggregator, boolean unmappedAggregator) {}

    public void batch(Descriptor descriptor, Aggregator aggregator) {
      this.reportList.add(new Report(descriptor, aggregator));
    }

    @Override
    public final List<MetricData> completeCollectionCycle() {
      List<MetricData> points = new ArrayList<>(reportList.size());
      long epochNanos = clock.now();
      for (Report report : reportList) {
        Point point = report.getAggregator().toPoint(startEpochNanos, epochNanos, this.labels);
        if (point != null) {
          points.add(
              MetricData.create(
                  report.getDescriptor(),
                  resource,
                  instrumentationLibraryInfo,
                  Collections.singletonList(point)));
        }
      }
      startEpochNanos = epochNanos;
      reportList = new ArrayList<>();
      return points;
    }

    private static class Report {
      private final Descriptor descriptor;
      private final Aggregator aggregator;

      Report(Descriptor descriptor, Aggregator aggregator) {
        this.aggregator = aggregator;
        this.descriptor = descriptor;
      }

      public Descriptor getDescriptor() {
        return descriptor;
      }

      public Aggregator getAggregator() {
        return aggregator;
      }
    }
  }
}
