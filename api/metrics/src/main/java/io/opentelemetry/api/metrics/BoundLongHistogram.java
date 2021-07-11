/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.context.Context;
import javax.annotation.concurrent.ThreadSafe;

/** A hisgogram instrument that records {@code long} values with preassociated attributes. */
@ThreadSafe
public interface BoundLongHistogram {
  /**
   * Record a value with a pre-bound set of attributes.
   *
   * @param value The amount of the measurement.
   * @param context The explicit context to associate with this measurement.
   */
  public void record(long value, Context context);
  /**
   * Record a value with a pre-bound set of attributes.
   *
   * <p>Note: This may use {@code Context.current()} to pull the context associated with this
   * measurement.
   *
   * @param value The amount of the measurement.
   */
  public void record(long value);

  public void unbind();
}
