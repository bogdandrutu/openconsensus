/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure.spi;

import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;

/**
 * A service provider interface (SPI) for performing additional programmatic configuration of a
 * {@link SdkTracerProviderBuilder} during initialization. When using auto-configuration, you should
 * prefer to use system properties or environment variables for configuration, but this may be
 * useful to register components that are not part of the SDK such as custom exporters.
 */
public interface SdkTracerProviderConfigurer {
  /** Configures the {@link SdkTracerProviderBuilder}. */
  void configure(SdkTracerProviderBuilder tracerProviderBuilder, ConfigProperties config);
}
