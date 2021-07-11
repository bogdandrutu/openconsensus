/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import io.opentelemetry.sdk.metrics.state.StorageHandle;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import java.util.Objects;

/** Sdk implementation of LongCounter. */
public class LongCounterSdk implements LongCounter {
  private final WriteableInstrumentStorage storage;

  LongCounterSdk(WriteableInstrumentStorage storage) {
    this.storage = storage;
  }

  @Override
  public void add(long value, Attributes attributes, Context context) {
    if (value < 0) {
      throw new IllegalArgumentException("Counters can only increase");
    }
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
  public BoundLongCounter bind(Attributes attributes) {
    Objects.requireNonNull(attributes, "Null attributes");
    return new SdkBoundLongCounter(attributes, storage.bind(attributes));
  }

  /** Simple implementation of bound counter for now. */
  private static class SdkBoundLongCounter implements BoundLongCounter {
    private final Attributes attributes;
    private final StorageHandle handle;

    SdkBoundLongCounter(Attributes attributes, StorageHandle handle) {
      this.attributes = attributes;
      this.handle = handle;
    }

    @Override
    public void add(long value, Context context) {
      if (value < 0) {
        throw new IllegalArgumentException("Counters can only increase");
      }
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
