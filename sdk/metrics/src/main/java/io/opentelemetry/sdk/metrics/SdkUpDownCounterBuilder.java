/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.api.metrics.UpDownCounter;
import io.opentelemetry.api.metrics.UpDownCounterBuilder;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.instrument.InstrumentType;
import io.opentelemetry.sdk.metrics.instrument.InstrumentValueType;
import io.opentelemetry.sdk.metrics.state.MeterProviderSharedState;
import io.opentelemetry.sdk.metrics.state.MeterSharedState;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import java.util.function.Consumer;

class SdkUpDownCounterBuilder<
        InstrumentT extends UpDownCounter, MeasurementT extends ObservableMeasurement>
    implements UpDownCounterBuilder<InstrumentT, MeasurementT> {
  private final MeterProviderSharedState meterProviderSharedState;
  private final MeterSharedState meterSharedState;
  private final String instrumentName;
  private String description;
  private String unit;
  private InstrumentValueType valueType;

  SdkUpDownCounterBuilder(
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
  public UpDownCounterBuilder<InstrumentT, MeasurementT> setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public UpDownCounterBuilder<InstrumentT, MeasurementT> setUnit(String unit) {
    this.unit = unit;
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public UpDownCounterBuilder<LongUpDownCounter, ObservableLongMeasurement> ofLongs() {
    this.valueType = InstrumentValueType.LONG;
    return (UpDownCounterBuilder<LongUpDownCounter, ObservableLongMeasurement>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public UpDownCounterBuilder<DoubleUpDownCounter, ObservableDoubleMeasurement> ofDoubles() {
    this.valueType = InstrumentValueType.DOUBLE;
    return (UpDownCounterBuilder<DoubleUpDownCounter, ObservableDoubleMeasurement>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public InstrumentT build() {
    InstrumentDescriptor descriptor =
        InstrumentDescriptor.create(
            instrumentName, description, unit, InstrumentType.UP_DOWN_COUNTER, valueType);
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
      return (InstrumentT) new LongUpDownCounterSdk(storage);
    }
    return (InstrumentT) new DoubleUpDownCounterSdk(storage);
  }

  @Override
  public void buildWithCallback(Consumer<MeasurementT> callback) {
    InstrumentDescriptor descriptor =
        InstrumentDescriptor.create(
            instrumentName, description, unit, InstrumentType.OBSERVBALE_UP_DOWN_SUM, valueType);
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
