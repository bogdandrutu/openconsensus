/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.api.internal.Utils;
import io.opentelemetry.api.metrics.Instrument;
import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.common.InstrumentValueType;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;
import java.util.Objects;

abstract class AbstractInstrument implements Instrument {

  private final InstrumentDescriptor descriptor;

  // All arguments cannot be null because they are checked in the abstract builder classes.
  AbstractInstrument(InstrumentDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  final InstrumentDescriptor getDescriptor() {
    return descriptor;
  }

  /**
   * Collects records from all the entries (labelSet, Bound) that changed since the previous call.
   */
  abstract void collectAll(List<MetricData> output);

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractInstrument)) {
      return false;
    }

    AbstractInstrument that = (AbstractInstrument) o;

    return descriptor.equals(that.descriptor);
  }

  @Override
  public int hashCode() {
    return descriptor.hashCode();
  }

  abstract static class Builder<B extends AbstractInstrument.Builder<?>>
      implements Instrument.Builder {
    /* VisibleForTesting */ static final String ERROR_MESSAGE_INVALID_NAME =
        "Name should be a ASCII string with a length no greater than "
            + StringUtils.METRIC_NAME_MAX_LENGTH
            + " characters.";

    private final String name;
    private final InstrumentType instrumentType;
    private final InstrumentValueType instrumentValueType;
    private String description = "";
    private String unit = "1";

    Builder(String name, InstrumentType instrumentType, InstrumentValueType instrumentValueType) {
      Objects.requireNonNull(name, "name");
      Utils.checkArgument(StringUtils.isValidMetricName(name), ERROR_MESSAGE_INVALID_NAME);
      this.name = name;
      this.instrumentType = instrumentType;
      this.instrumentValueType = instrumentValueType;
    }

    @Override
    public final B setDescription(String description) {
      this.description = Objects.requireNonNull(description, "description");
      return getThis();
    }

    @Override
    public final B setUnit(String unit) {
      this.unit = Objects.requireNonNull(unit, "unit");
      return getThis();
    }

    abstract B getThis();

    final InstrumentDescriptor buildDescriptor() {
      return InstrumentDescriptor.create(
          name, description, unit, instrumentType, instrumentValueType);
    }
  }
}
