/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundLongHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import io.opentelemetry.sdk.metrics.state.StorageHandle;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import java.util.Objects;

/** Sdk implementation of LongHistogram. */
public class LongHistogramSdk implements LongHistogram {
  private final WriteableInstrumentStorage storage;

  LongHistogramSdk(WriteableInstrumentStorage storage) {
    this.storage = storage;
  }

  @Override
  public void record(long value, Attributes attributes, Context context) {
    storage.record(LongMeasurement.create(value, attributes, context));
  }

  @Override
  public void record(long value, Attributes attributes) {
    record(value, attributes, Context.current());
  }

  @Override
  public void record(long value) {
    record(value, Attributes.empty());
  }

  @Override
  public BoundLongHistogram bind(Attributes attributes) {
    Objects.requireNonNull(attributes, "Null attributes");
    return new SdkBoundLongHistogram(attributes, storage.bind(attributes));
  }

  private static class SdkBoundLongHistogram implements BoundLongHistogram {
    private final Attributes attributes;
    private final StorageHandle handle;

    SdkBoundLongHistogram(Attributes attributes, StorageHandle handle) {
      this.attributes = attributes;
      this.handle = handle;
    }

    @Override
    public void record(long value, Context context) {
      handle.record(LongMeasurement.create(value, attributes, context));
    }

    @Override
    public void record(long value) {
      record(value, Context.current());
    }

    @Override
    public void unbind() {
      handle.release();
    }
  }
}
