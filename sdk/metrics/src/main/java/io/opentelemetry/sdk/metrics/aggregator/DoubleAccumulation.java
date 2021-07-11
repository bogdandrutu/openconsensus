/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import com.google.auto.value.AutoValue;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import java.util.Collections;
import javax.annotation.concurrent.Immutable;

/** An accumulation representing {@code double} values and exemplars. */
@Immutable
@AutoValue
abstract class DoubleAccumulation {

  static DoubleAccumulation create(double value, Iterable<Measurement> exemplars) {
    return new AutoValue_DoubleAccumulation(value, exemplars);
  }

  static DoubleAccumulation create(double value) {
    return create(value, Collections.emptyList());
  }

  DoubleAccumulation() {}

  /** The current value. */
  abstract double getValue();

  /** Sampled measurements recorded during this accumulation. */
  abstract Iterable<Measurement> getExemplars();
}
