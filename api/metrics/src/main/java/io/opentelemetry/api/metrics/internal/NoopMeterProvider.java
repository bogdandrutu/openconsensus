/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics.internal;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;

// TODO: Figure out real behavior for this.
public class NoopMeterProvider implements MeterProvider {
  @Override
  public MeterBuilder meterBuilder(String instrumentationName) {
    return BUILDER_INSTANCE;
  }

  private static final NoopMeterProvider INSTANCE = new NoopMeterProvider();
  private static final MeterBuilder BUILDER_INSTANCE = new NoopMeterBuilder();

  public static MeterProvider getInstance() {
    return INSTANCE;
  }

  private NoopMeterProvider() {}

  private static class NoopMeterBuilder implements MeterBuilder {

    @Override
    public MeterBuilder setSchemaUrl(String schemaUrl) {
      return this;
    }

    @Override
    public MeterBuilder setInstrumentationVersion(String instrumentationVersion) {
      return this;
    }

    @Override
    public Meter build() {
      return NoopMeter.getInstance();
    }
  }
}
