/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.context.Context;
import javax.annotation.concurrent.ThreadSafe;

/** An up-down-counter instrument with pre-bound attributes. */
@ThreadSafe
public interface BoundDoubleUpDownCounter {
  /**
   * Record a value with a pre-bound attributes.
   *
   * @param value The increment amount. May be positive, negative or zero.
   * @param context The explicit context to associate with this measurement.
   */
  public void add(double value, Context context);
  /**
   * Reecord a value with pre-bound attributes.
   *
   * <p>Note: This may use {@code Context.current()} to pull the context associated with this
   * measurement.
   *
   * @param value The increment amount. May be positive, negative or zero.
   */
  public void add(double value);

  public void unbind();
}
