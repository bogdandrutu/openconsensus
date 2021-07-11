/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.Counter;
import io.opentelemetry.api.metrics.CounterBuilder;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.instrument.InstrumentType;
import io.opentelemetry.sdk.metrics.instrument.InstrumentValueType;
import io.opentelemetry.sdk.metrics.state.MeterProviderSharedState;
import io.opentelemetry.sdk.metrics.state.MeterSharedState;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import java.util.function.Consumer;

class SdkCounterBuilder<InstrumentT extends Counter, MeasurementT extends ObservableMeasurement>
    implements CounterBuilder<InstrumentT, MeasurementT> {
  private final MeterProviderSharedState meterProviderSharedState;
  private final MeterSharedState meterSharedState;
  private final String instrumentName;
  private String description;
  private String unit;
  private InstrumentValueType valueType;

  SdkCounterBuilder(
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      String name) {
    this.instrumentName = name;
    description = "";
    unit = "1";
    valueType = InstrumentValueType.LONG;
    this.meterProviderSharedState = meterProviderSharedState;
    this.meterSharedState = meterSharedState;
  }

  @Override
  public CounterBuilder<InstrumentT, MeasurementT> setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public CounterBuilder<InstrumentT, MeasurementT> setUnit(String unit) {
    this.unit = unit;
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public CounterBuilder<LongCounter, ObservableLongMeasurement> ofLongs() {
    this.valueType = InstrumentValueType.LONG;
    return (CounterBuilder<LongCounter, ObservableLongMeasurement>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public CounterBuilder<DoubleCounter, ObservableDoubleMeasurement> ofDoubles() {
    this.valueType = InstrumentValueType.DOUBLE;
    return (CounterBuilder<DoubleCounter, ObservableDoubleMeasurement>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public InstrumentT build() {
    InstrumentDescriptor descriptor =
        InstrumentDescriptor.create(
            instrumentName, description, unit, InstrumentType.COUNTER, valueType);
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
      return (InstrumentT) new LongCounterSdk(storage);
    }
    return (InstrumentT) new DoubleCounterSdk(storage);
  }

  @Override
  public void buildWithCallback(Consumer<MeasurementT> callback) {
    InstrumentDescriptor descriptor =
        InstrumentDescriptor.create(
            instrumentName, description, unit, InstrumentType.OBSERVABLE_SUM, valueType);
    // TODO: Should we register TWO callbacks for the same instrument?
    meterSharedState
        .getInstrumentStorageRegistry()
        .register(
            descriptor,
            () ->
                meterProviderSharedState
                    .getMeasurementProcessor()
                    .createAsynchronousStorage(
                        descriptor, meterProviderSharedState, meterSharedState, callback));
  }
}
