/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundDoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.instrument.DoubleMeasurement;
import io.opentelemetry.sdk.metrics.state.StorageHandle;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import java.util.Objects;

/** Sdk implementation of DoubleUpDownCounter. */
public class DoubleUpDownCounterSdk implements DoubleUpDownCounter {
  private final WriteableInstrumentStorage storage;

  DoubleUpDownCounterSdk(WriteableInstrumentStorage storage) {
    this.storage = storage;
  }

  @Override
  public void add(double value, Attributes attributes, Context context) {
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
  public BoundDoubleUpDownCounter bind(Attributes attributes) {
    Objects.requireNonNull(attributes, "Null attributes");
    return new SdkBoundDoubleUpDownCounter(attributes, storage.bind(attributes));
  }

  /** Simple implementation of bound counter for now. */
  private static class SdkBoundDoubleUpDownCounter implements BoundDoubleUpDownCounter {
    private final Attributes attributes;
    private final StorageHandle handle;

    SdkBoundDoubleUpDownCounter(Attributes attributes, StorageHandle handle) {
      this.attributes = attributes;
      this.handle = handle;
    }

    @Override
    public void add(double value, Context context) {
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
