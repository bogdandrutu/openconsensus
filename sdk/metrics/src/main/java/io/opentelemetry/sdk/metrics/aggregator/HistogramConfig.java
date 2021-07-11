/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import com.google.auto.value.AutoValue;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import javax.annotation.concurrent.Immutable;

/** Configuration for {@code Histogram} metric production. */
@AutoValue
@Immutable
public abstract class HistogramConfig {

  // Default histograms are designed for average HTTP/RPC latency measurements in "millisecond".
  // Note: These are similar to prometheus default buckets (although prometheus uses "seconds").
  @SuppressWarnings("MutablePublicArray")
  public static final double[] DEFAULT_HISTOGRAM_BOUNDARIES = {
    5, 10, 25, 50, 75, 100, 250, 500, 750, 1_000, 2_500, 5_000, 7_500, 10_000
  };

  /** Returns the name of the sum metric produced. */
  public abstract String getName();
  /** Returns the description of the sum metric, which can be used in documentation. */
  public abstract String getDescription();
  /**
   * The unit in which the metric value is reported. Follows the format described by
   * http://unitsofmeasure.org/ucum.html.
   */
  public abstract String getUnit();
  /**
   * Returns the {@code AggregationTemporality} of this metric,
   *
   * <p>AggregationTemporality describes if the aggregator reports delta changes since last report
   * time, or cumulative changes since a fixed start time.
   *
   * @return the {@code AggregationTemporality} of this metric
   */
  public abstract AggregationTemporality getTemporality();

  /** Returns the histogram bucket boundaries. */
  @SuppressWarnings("mutable")
  public abstract double[] getBoundaries();

  public static Builder builder() {
    return new AutoValue_HistogramConfig.Builder();
  }

  public abstract Builder toBuilder();

  /** Returns the default sum aggregation configuration for a given instrument. */
  public static HistogramConfig buildDefaultFromInstrument(InstrumentDescriptor instrument) {
    // TODO: assert insturment type is one of the Histogram aggregations...
    return builder()
        .setName(instrument.getName())
        .setDescription(instrument.getDescription())
        .setUnit(instrument.getUnit())
        .setBoundaries(DEFAULT_HISTOGRAM_BOUNDARIES)
        .setTemporality(AggregationTemporality.CUMULATIVE)
        .build();
  }

  /** Builder for {@link SumConfig}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setDescription(String name);

    public abstract Builder setUnit(String unit);

    public abstract Builder setTemporality(AggregationTemporality temporality);

    public abstract Builder setBoundaries(double[] boundaries);

    public abstract HistogramConfig build();
  }
}
