/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.common.Attributes;

/** An interface for observing measurements with {@code double} values. */
public interface ObservableDoubleMeasurement extends ObservableMeasurement {
  /**
   * Record a measurement with a set of attributes.
   *
   * @param value The measurement amount. MUST be non-negative.
   * @param attributes A set of attributes to associate with the count.
   */
  public void observe(double value, Attributes attributes);
  /**
   * Reecord a measurement.
   *
   * @param value The measurement amount. MUST be non-negative.
   */
  public void observe(double value);
}
