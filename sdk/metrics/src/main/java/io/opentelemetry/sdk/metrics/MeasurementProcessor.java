/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.state.InstrumentStorage;
import io.opentelemetry.sdk.metrics.state.MeterProviderSharedState;
import io.opentelemetry.sdk.metrics.state.MeterSharedState;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import java.util.function.Consumer;

/**
 * MeasurementProcessor is an interface which is used to construct metric storage.
 *
 * <p>By specification, this processor needs access to:
 *
 * <ul>
 *   <li>The Measurement being recorded.
 *   <li>The Instrument being recorded.
 *   <li>The resource which is accoidated with the MeterProvider of recorded measurement.
 *   <li>(optionally) The Context (and Baggage or Span) associated with synchronous measurements.
 *       <i>Note: Measurement contains a {@code Context} where {@code Baggage} and {@code Span} may
 *       be extracted</i>
 * </ul>
 *
 * <p>This met because the measurement processor is responsible both for constructing the actual
 * storage of in-memory metrics as well as the pipeline/flow of measurements into this storage.
 */
public interface MeasurementProcessor {
  /** Constructs storage for a synchronous instrument. */
  WriteableInstrumentStorage createStorage(
      InstrumentDescriptor instrument,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState);

  /** Constructs storage for an asynchronous instrument. */
  <T extends ObservableMeasurement> InstrumentStorage createAsynchronousStorage(
      InstrumentDescriptor instrument,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      Consumer<T> callback);
}
