/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Histogram;
import io.opentelemetry.api.metrics.HistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.instrument.InstrumentType;
import io.opentelemetry.sdk.metrics.instrument.InstrumentValueType;
import io.opentelemetry.sdk.metrics.state.MeterProviderSharedState;
import io.opentelemetry.sdk.metrics.state.MeterSharedState;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;

class SdkHistogramBuilder<InstrumentT extends Histogram> implements HistogramBuilder<InstrumentT> {
  private final MeterProviderSharedState meterProviderSharedState;
  private final MeterSharedState meterSharedState;
  private final String instrumentName;
  private String description;
  private String unit;
  private InstrumentValueType valueType;

  SdkHistogramBuilder(
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      String name) {
    this.instrumentName = name;
    description = "";
    unit = "1";
    valueType = InstrumentValueType.DOUBLE;
    this.meterProviderSharedState = meterProviderSharedState;
    this.meterSharedState = meterSharedState;
  }

  @Override
  public HistogramBuilder<InstrumentT> setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public HistogramBuilder<InstrumentT> setUnit(String unit) {
    this.unit = unit;
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public HistogramBuilder<LongHistogram> ofLongs() {
    this.valueType = InstrumentValueType.LONG;
    return (HistogramBuilder<LongHistogram>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public HistogramBuilder<DoubleHistogram> ofDoubles() {
    this.valueType = InstrumentValueType.DOUBLE;
    return (HistogramBuilder<DoubleHistogram>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public InstrumentT build() {
    InstrumentDescriptor descriptor =
        InstrumentDescriptor.create(
            instrumentName, description, unit, InstrumentType.HISTOGRAM, valueType);
    WriteableInstrumentStorage storage =
        meterSharedState
            .getInstrumentStorageRegistry()
            .register(
                descriptor,
                () ->
                    meterProviderSharedState
                        .getMeasurementProcessor()
                        .createStorage(descriptor, meterProviderSharedState, meterSharedState));
    if (valueType == InstrumentValueType.LONG) {
      return (InstrumentT) new LongHistogramSdk(storage);
    }
    return (InstrumentT) new DoubleHistogramSdk(storage);
  }
}
