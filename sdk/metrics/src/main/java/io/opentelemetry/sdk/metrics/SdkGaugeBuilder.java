/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.GaugeBuilder;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.instrument.InstrumentType;
import io.opentelemetry.sdk.metrics.instrument.InstrumentValueType;
import io.opentelemetry.sdk.metrics.state.MeterProviderSharedState;
import io.opentelemetry.sdk.metrics.state.MeterSharedState;
import java.util.function.Consumer;

/** Sdk version of gauage builder. */
public class SdkGaugeBuilder<ObservableMeasurementT extends ObservableMeasurement>
    implements GaugeBuilder<ObservableMeasurementT> {
  private final MeterProviderSharedState meterProviderSharedState;
  private final MeterSharedState meterSharedState;
  private final String instrumentName;
  private String description;
  private String unit;
  private InstrumentValueType valueType;

  SdkGaugeBuilder(
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
  public GaugeBuilder<ObservableMeasurementT> setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public GaugeBuilder<ObservableMeasurementT> setUnit(String unit) {
    this.unit = unit;
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public GaugeBuilder<ObservableLongMeasurement> ofLongs() {
    this.valueType = InstrumentValueType.LONG;
    return (GaugeBuilder<ObservableLongMeasurement>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public GaugeBuilder<ObservableDoubleMeasurement> ofDoubles() {
    this.valueType = InstrumentValueType.DOUBLE;
    return (GaugeBuilder<ObservableDoubleMeasurement>) this;
  }

  @Override
  public void buildWithCallback(Consumer<ObservableMeasurementT> callback) {
    InstrumentDescriptor descriptor =
        InstrumentDescriptor.create(
            instrumentName, description, unit, InstrumentType.OBSERVABLE_GAUGE, valueType);
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
