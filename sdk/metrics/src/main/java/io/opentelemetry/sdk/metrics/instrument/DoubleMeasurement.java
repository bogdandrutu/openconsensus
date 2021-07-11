/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.instrument;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import javax.annotation.concurrent.Immutable;

@Immutable
@AutoValue
public abstract class DoubleMeasurement implements Measurement {

  /** Creates a measurement, attaching specific context to the measurement. */
  public static DoubleMeasurement create(double value, Attributes attributes, Context context) {
    return new AutoValue_DoubleMeasurement(attributes, context, value);
  }

  /** Creates a measurement using the currently attached context. */
  public static DoubleMeasurement createWithCurrentContext(double value, Attributes attributes) {
    return create(value, attributes, Context.current());
  }

  /**
   * Creates a measurement with no known telemetry context.
   *
   * <p>For use by Asynchronous Instruments ONLY
   */
  public static DoubleMeasurement createNoContext(double value, Attributes attributes) {
    // For now we record as "root" context, rather than allowing null.
    return new AutoValue_DoubleMeasurement(attributes, Context.root(), value);
  }
  /** Returns the value of the measurement. */
  public abstract double getValue();

  @Override
  public DoubleMeasurement asDouble() {
    return this;
  }
}
