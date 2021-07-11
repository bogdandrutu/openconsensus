/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import javax.annotation.concurrent.ThreadSafe;

/** An up-down-counter instrument that records {@code long} values. */
@ThreadSafe
public interface LongUpDownCounter extends UpDownCounter {
  /**
   * Record a value with a set of attributes.
   *
   * @param value The increment amount. May be positive, negative or zero.
   * @param attributes A set of attributes to associate with the count.
   * @param context The explicit context to associate with this measurement.
   */
  public void add(long value, Attributes attributes, Context context);
  /**
   * Record a value with a set of attributes.
   *
   * <p>Note: This may use {@code Context.current()} to pull the context associated with this
   * measurement.
   *
   * @param value The increment amount. May be positive, negative or zero.
   * @param attributes A set of attributes to associate with the count.
   */
  public void add(long value, Attributes attributes);
  /**
   * Reecord a value.
   *
   * <p>Note: This may use {@code Context.current()} to pull the context associated with this
   * measurement.
   *
   * @param value The increment amount. May be positive, negative or zero.
   */
  public void add(long value);

  /**
   * Construct a bound version of this instrument where all recorded values use the given
   * attributes.
   */
  public BoundLongUpDownCounter bind(Attributes attributes);
}
