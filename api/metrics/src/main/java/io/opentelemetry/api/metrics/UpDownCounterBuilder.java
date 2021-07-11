/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

/**
 * Builder class for {@link UpDownCounter}.
 *
 * <p>This can build both {@link LongUpDownCounter} and {@link DoubleUpDownCounter} instruments.
 */
public interface UpDownCounterBuilder<
        InstrumentT extends UpDownCounter, ObservableMeasurementT extends ObservableMeasurement>
    extends InstrumentBuilder<InstrumentT>, ObservableInstrumentBuilder<ObservableMeasurementT> {
  @Override
  public UpDownCounterBuilder<InstrumentT, ObservableMeasurementT> setDescription(
      String description);

  @Override
  public UpDownCounterBuilder<InstrumentT, ObservableMeasurementT> setUnit(String unit);
  /** Sets the up-down-counter for recording {@code long} values. */
  @Override
  public UpDownCounterBuilder<LongUpDownCounter, ObservableLongMeasurement> ofLongs();
  /** Sets the up-down-counter for recording {@code double} values. */
  @Override
  public UpDownCounterBuilder<DoubleUpDownCounter, ObservableDoubleMeasurement> ofDoubles();
}
