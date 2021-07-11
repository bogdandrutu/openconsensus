/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundDoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.instrument.DoubleMeasurement;
import io.opentelemetry.sdk.metrics.state.StorageHandle;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import java.util.Objects;

/** Sdk implementation of DoubleHistogram. */
public class DoubleHistogramSdk implements DoubleHistogram {
  private final WriteableInstrumentStorage storage;

  DoubleHistogramSdk(WriteableInstrumentStorage storage) {
    this.storage = storage;
  }

  @Override
  public void record(double value, Attributes attributes, Context context) {
    storage.record(DoubleMeasurement.create(value, attributes, context));
  }

  @Override
  public void record(double value, Attributes attributes) {
    record(value, attributes, Context.current());
  }

  @Override
  public void record(double value) {
    record(value, Attributes.empty());
  }

  @Override
  public BoundDoubleHistogram bind(Attributes attributes) {
    Objects.requireNonNull(attributes, "Null attributes");
    return new SdkBoundDoubleHistogram(attributes, storage.bind(attributes));
  }

  private static class SdkBoundDoubleHistogram implements BoundDoubleHistogram {
    private final Attributes attributes;
    private final StorageHandle handle;

    SdkBoundDoubleHistogram(Attributes attributes, StorageHandle handle) {
      this.attributes = attributes;
      this.handle = handle;
    }

    @Override
    public void record(double value, Context context) {
      handle.record(DoubleMeasurement.create(value, attributes, context));
    }

    @Override
    public void record(double value) {
      record(value, Context.current());
    }

    @Override
    public void unbind() {
      handle.release();
    }
  }
}
