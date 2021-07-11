/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import java.util.function.Consumer;

public interface ObservableInstrumentBuilder<ObservableMeasurementT extends ObservableMeasurement> {
  /**
   * Sets the description for this instrument.
   *
   * <p>Description stirngs should follw the instrument description rules:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-description
   */
  public ObservableInstrumentBuilder<ObservableMeasurementT> setDescription(String description);
  /**
   * Set the unit of measure for this instrument.
   *
   * <p>Unit strings should follow the instrument unit rules:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-unit
   */
  public ObservableInstrumentBuilder<ObservableMeasurementT> setUnit(String unit);

  /** Sets the gauge for recording {@code double} values. */
  public ObservableInstrumentBuilder<ObservableDoubleMeasurement> ofDoubles();
  /** Sets the gauge for recording {@code long} values. */
  public ObservableInstrumentBuilder<ObservableLongMeasurement> ofLongs();

  /**
   * Builds this asynchronous insturment with the given callback.
   *
   * <p>The callback will only be called when the {@link Meter} is being observed.
   *
   * @param callback A state-capturing callback used to observe values on-demand.
   */
  public void buildWithCallback(Consumer<ObservableMeasurementT> callback);
}
