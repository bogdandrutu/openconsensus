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
public abstract class LongMeasurement implements Measurement {

  public static LongMeasurement empty() {
    return createNoContext(0, Attributes.empty());
  }

  public static LongMeasurement create(long value, Attributes attributes, Context context) {
    return new AutoValue_LongMeasurement(attributes, context, value);
  }

  public static LongMeasurement createWithCurrentContext(long value, Attributes attributes) {
    return create(value, attributes, Context.current());
  }

  public static LongMeasurement createNoContext(long value, Attributes attributes) {
    // For now we record as "root" context, rather than allowing null.
    return new AutoValue_LongMeasurement(attributes, Context.root(), value);
  }
  /** Returns the value of the measurement. */
  public abstract long getValue();

  @Override
  public DoubleMeasurement asDouble() {
    return DoubleMeasurement.create(getValue(), getAttributes(), getContext());
  }
}
