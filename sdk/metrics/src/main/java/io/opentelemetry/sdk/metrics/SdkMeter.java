/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** {@link SdkMeter} is SDK implementation of {@link Meter}. */
final class SdkMeter implements Meter {
  private final MeterProviderSharedState meterProviderSharedState;
  private final MeterSharedState meterSharedState;

  SdkMeter(
      MeterProviderSharedState meterProviderSharedState,
      InstrumentationLibraryInfo instrumentationLibraryInfo) {
    this.meterProviderSharedState = meterProviderSharedState;
    this.meterSharedState = MeterSharedState.create(instrumentationLibraryInfo);
  }

  InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
    return meterSharedState.getInstrumentationLibraryInfo();
  }

  @Override
  public DoubleCounterSdk.Builder doubleCounterBuilder(String name) {
    return new DoubleCounterSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public LongCounterSdk.Builder longCounterBuilder(String name) {
    return new LongCounterSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public DoubleUpDownCounterSdk.Builder doubleUpDownCounterBuilder(String name) {
    return new DoubleUpDownCounterSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public LongUpDownCounterSdk.Builder longUpDownCounterBuilder(String name) {
    return new LongUpDownCounterSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public DoubleValueRecorderSdk.Builder doubleValueRecorderBuilder(String name) {
    return new DoubleValueRecorderSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public LongValueRecorderSdk.Builder longValueRecorderBuilder(String name) {
    return new LongValueRecorderSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public DoubleSumObserverSdk.Builder doubleSumObserverBuilder(String name) {
    return new DoubleSumObserverSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public LongSumObserverSdk.Builder longSumObserverBuilder(String name) {
    return new LongSumObserverSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public DoubleUpDownSumObserverSdk.Builder doubleUpDownSumObserverBuilder(String name) {
    return new DoubleUpDownSumObserverSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public LongUpDownSumObserverSdk.Builder longUpDownSumObserverBuilder(String name) {
    return new LongUpDownSumObserverSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public DoubleValueObserverSdk.Builder doubleValueObserverBuilder(String name) {
    return new DoubleValueObserverSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public LongValueObserverSdk.Builder longValueObserverBuilder(String name) {
    return new LongValueObserverSdk.Builder(name, meterProviderSharedState, meterSharedState);
  }

  @Override
  public BatchRecorderSdk newBatchRecorder(String... keyValuePairs) {
    return new BatchRecorderSdk(keyValuePairs);
  }

  /** Collects all the metric recordings that changed since the previous call. */
  Collection<MetricData> collectAll() {
    InstrumentRegistry instrumentRegistry = meterSharedState.getInstrumentRegistry();
    Collection<AbstractInstrument> instruments = instrumentRegistry.getInstruments();
    List<MetricData> result = new ArrayList<>(instruments.size());
    for (AbstractInstrument instrument : instruments) {
      instrument.collectAll(result);
    }
    return result;
  }
}
