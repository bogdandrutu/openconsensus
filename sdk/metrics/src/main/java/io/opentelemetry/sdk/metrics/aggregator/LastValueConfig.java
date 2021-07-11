/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import com.google.auto.value.AutoValue;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import javax.annotation.concurrent.Immutable;

/** Configuration for {@code Gauge} metric production. */
@AutoValue
@Immutable
public abstract class LastValueConfig {
  /** Returns the name of the sum metric produced. */
  public abstract String getName();
  /** Returns the description of the sum metric, which can be used in documentation. */
  public abstract String getDescription();
  /**
   * The unit in which the metric value is reported. Follows the format described by
   * http://unitsofmeasure.org/ucum.html.
   */
  public abstract String getUnit();

  public static Builder builder() {
    return new AutoValue_LastValueConfig.Builder();
  }

  /** Returns the default sum aggregation configuration for a given instrument. */
  public static LastValueConfig buildDefaultFromInstrument(InstrumentDescriptor instrument) {
    // TODO: assert insturment type is one of the Sum aggregations...
    return builder()
        .setName(instrument.getName())
        .setDescription(instrument.getDescription())
        .setUnit(instrument.getUnit())
        .build();
  }

  /** Builder for {@link LastValueConfig}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setDescription(String name);

    public abstract Builder setUnit(String unit);

    public abstract LastValueConfig build();
  }
}
