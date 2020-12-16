/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.spi;

import io.opentelemetry.sdk.metrics.MeterSdkProvider;
import io.opentelemetry.spi.metrics.MeterProviderFactory;

/** {@code MeterProvider} provider implementation for {@link MeterProviderFactory}. */
public final class MeterProviderFactorySdk implements MeterProviderFactory {

  @Override
  public MeterSdkProvider create() {
    return MeterSdkProvider.builder().build();
  }
}
