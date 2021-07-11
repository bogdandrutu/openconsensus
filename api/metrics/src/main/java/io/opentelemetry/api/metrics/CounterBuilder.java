/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

/**
 * Builder class for {@link Counter}.
 *
 * <p>This can build both {@link LongCounter} and {@link DoubleCounter} instruments.
 */
public interface CounterBuilder<
        InstrumentT extends Counter, ObservableMeasurementT extends ObservableMeasurement>
    extends InstrumentBuilder<InstrumentT>, ObservableInstrumentBuilder<ObservableMeasurementT> {
  @Override
  public CounterBuilder<InstrumentT, ObservableMeasurementT> setDescription(String description);

  @Override
  public CounterBuilder<InstrumentT, ObservableMeasurementT> setUnit(String unit);
  /** Sets the counter for recording {@code long} values. */
  @Override
  public CounterBuilder<LongCounter, ObservableLongMeasurement> ofLongs();
  /** Sets the counter for recording {@code double} values. */
  @Override
  public CounterBuilder<DoubleCounter, ObservableDoubleMeasurement> ofDoubles();
}
