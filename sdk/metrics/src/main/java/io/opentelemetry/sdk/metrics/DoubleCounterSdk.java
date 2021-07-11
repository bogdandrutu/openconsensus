/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundDoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.instrument.DoubleMeasurement;
import io.opentelemetry.sdk.metrics.state.StorageHandle;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import java.util.Objects;

/** Sdk implementation of LongCounter. */
public class DoubleCounterSdk implements DoubleCounter {
  private final WriteableInstrumentStorage storage;

  DoubleCounterSdk(WriteableInstrumentStorage storage) {
    this.storage = storage;
  }

  @Override
  public void add(double value, Attributes attributes, Context context) {
    if (value < 0) {
      throw new IllegalArgumentException("Counters can only increase");
    }
    storage.record(DoubleMeasurement.create(value, attributes, context));
  }

  @Override
  public void add(double value, Attributes attributes) {
    add(value, attributes, Context.current());
  }

  @Override
  public void add(double value) {
    add(value, Attributes.empty());
  }

  @Override
  public BoundDoubleCounter bind(Attributes attributes) {
    Objects.requireNonNull(attributes, "Null attributes");
    return new SdkBoundDoubleCounter(attributes, storage.bind(attributes));
  }

  /**
   * Simple implementation of bound counter for now.
   *
   * <p>This should be updated to use bound handles.
   */
  private static class SdkBoundDoubleCounter implements BoundDoubleCounter {
    private final Attributes attributes;
    private final StorageHandle handle;

    SdkBoundDoubleCounter(Attributes attributes, StorageHandle handle) {
      this.attributes = attributes;
      this.handle = handle;
    }

    @Override
    public void add(double value, Context context) {
      if (value < 0) {
        throw new IllegalArgumentException("Counters can only increase");
      }
      handle.record(DoubleMeasurement.create(value, attributes, context));
    }

    @Override
    public void add(double value) {
      add(value, Context.current());
    }

    @Override
    public void unbind() {
      handle.release();
    }
  }
}
