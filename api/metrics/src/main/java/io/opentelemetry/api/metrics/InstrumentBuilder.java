/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

public interface InstrumentBuilder<InstrumentT> {
  /**
   * Sets the description for this instrument.
   *
   * <p>Description stirngs should follw the instrument description rules:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-description
   */
  public InstrumentBuilder<InstrumentT> setDescription(String description);
  /**
   * Set the unit of measure for this instrument.
   *
   * <p>Unit strings should follow the instrument unit rules:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-unit
   */
  public InstrumentBuilder<InstrumentT> setUnit(String unit);
  /**
   * Builds and returns a {@code InstrumentT} with the desired options.
   *
   * @return a {@code InstrumentT} with the desired options.
   */
  public InstrumentT build();
}
