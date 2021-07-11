/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundLongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import io.opentelemetry.sdk.metrics.state.StorageHandle;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import java.util.Objects;

/** Sdk implementation of LongUpDownCounter. */
public class LongUpDownCounterSdk implements LongUpDownCounter {
  private final WriteableInstrumentStorage storage;

  LongUpDownCounterSdk(WriteableInstrumentStorage storage) {
    this.storage = storage;
  }

  @Override
  public void add(long value, Attributes attributes, Context context) {
    storage.record(LongMeasurement.create(value, attributes, context));
  }

  @Override
  public void add(long value, Attributes attributes) {
    add(value, attributes, Context.current());
  }

  @Override
  public void add(long value) {
    add(value, Attributes.empty());
  }

  @Override
  public BoundLongUpDownCounter bind(Attributes attributes) {
    Objects.requireNonNull(attributes, "Null attributes");
    return new SdkBoundLongUpDownCounter(attributes, storage.bind(attributes));
  }

  /** Simple implementation of bound counter for now. */
  private static class SdkBoundLongUpDownCounter implements BoundLongUpDownCounter {
    private final Attributes attributes;
    private final StorageHandle handle;

    SdkBoundLongUpDownCounter(Attributes attributes, StorageHandle handle) {
      this.attributes = attributes;
      this.handle = handle;
    }

    @Override
    public void add(long value, Context context) {
      handle.record(LongMeasurement.create(value, attributes, context));
    }

    @Override
    public void add(long value) {
      add(value, Context.current());
    }

    @Override
    public void unbind() {
      handle.release();
    }
  }
}
