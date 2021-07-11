/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.CounterBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.GaugeBuilder;
import io.opentelemetry.api.metrics.HistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.UpDownCounterBuilder;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.state.InstrumentStorage;
import io.opentelemetry.sdk.metrics.state.InstrumentStorageRegistry;
import io.opentelemetry.sdk.metrics.state.MeterProviderSharedState;
import io.opentelemetry.sdk.metrics.state.MeterSharedState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** {@link SdkMeter} is SDK implementation of {@link Meter}. */
public class SdkMeter implements Meter {
  private final MeterProviderSharedState meterProviderSharedState;
  private final MeterSharedState meterSharedState;

  SdkMeter(
      MeterProviderSharedState meterProviderSharedState,
      InstrumentationLibraryInfo instrumentationLibraryInfo) {
    this.meterProviderSharedState = meterProviderSharedState;
    this.meterSharedState = MeterSharedState.create(instrumentationLibraryInfo);
  }

  @Override
  public CounterBuilder<LongCounter, ObservableLongMeasurement> counterBuilder(String name) {
    return new SdkCounterBuilder<>(meterProviderSharedState, meterSharedState, name);
  }

  @Override
  public UpDownCounterBuilder<LongUpDownCounter, ObservableLongMeasurement> upDownCounterBuilder(
      String name) {
    return new SdkUpDownCounterBuilder<>(meterProviderSharedState, meterSharedState, name);
  }

  @Override
  public HistogramBuilder<DoubleHistogram> histogramBuilder(String name) {
    return new SdkHistogramBuilder<>(meterProviderSharedState, meterSharedState, name);
  }

  @Override
  public GaugeBuilder<ObservableDoubleMeasurement> gaugeBuilder(String name) {
    return new SdkGaugeBuilder<>(meterProviderSharedState, meterSharedState, name);
  }

  /** Collects all the metric recordings that changed since the previous call. */
  Collection<MetricData> collectAll(
      CollectionHandle collector, Set<CollectionHandle> allCollectors, long epochNanos) {
    InstrumentStorageRegistry instrumentRegistry = meterSharedState.getInstrumentStorageRegistry();
    Collection<InstrumentStorage> instruments = instrumentRegistry.getInstruments();
    List<MetricData> result = new ArrayList<>(instruments.size());
    for (InstrumentStorage instrument : instruments) {
      result.addAll(
          instrument.collectAndReset(
              collector, allCollectors, meterProviderSharedState.getStartEpochNanos(), epochNanos));
    }
    return result;
  }
}
