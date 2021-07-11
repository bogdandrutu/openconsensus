/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.resources.Resource;

@SuppressWarnings("ImmutableEnumChecker")
public enum TestSdk {
  API_ONLY(
      new SdkBuilder() {
        @Override
        Meter build() {
          return MeterProvider.noop().meterBuilder("io.opentelemetry.sdk.metrics").build();
        }
      }),
  SDK(
      new SdkBuilder() {
        @Override
        Meter build() {
          return SdkMeterProvider.builder()
              .setResource(Resource.empty())
              .setClock(Clock.getDefault())
              .build()
              .meterBuilder("io.opentelemetry.sdk.metrics")
              .build();
        }
      });

  private final SdkBuilder sdkBuilder;

  TestSdk(SdkBuilder sdkBuilder) {
    this.sdkBuilder = sdkBuilder;
  }

  public Meter getMeter() {
    return sdkBuilder.build();
  }

  private abstract static class SdkBuilder {
    abstract Meter build();
  }
}
