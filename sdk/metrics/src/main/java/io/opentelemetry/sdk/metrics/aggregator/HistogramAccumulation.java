/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import com.google.auto.value.AutoValue;
import io.opentelemetry.sdk.metrics.common.ImmutableLongArray;
import javax.annotation.concurrent.Immutable;

@Immutable
@AutoValue
abstract class HistogramAccumulation {
  /**
   * Creates a new {@link HistogramAccumulation} with the given values.
   *
   * @return a new {@link HistogramAccumulation} with the given values.
   */
  static HistogramAccumulation create(long count, double sum, ImmutableLongArray counts) {
    return new AutoValue_HistogramAccumulation(count, sum, counts);
  }

  HistogramAccumulation() {}

  /**
   * The number of measurements taken.
   *
   * @return the count of recorded measurements.
   */
  abstract long getCount();

  /**
   * The sum of all measurements recorded.
   *
   * @return the sum of recorded measurements.
   */
  abstract double getSum();

  /**
   * The counts in each bucket.
   *
   * @return the counts in each bucket.
   */
  abstract ImmutableLongArray getCounts();
}
