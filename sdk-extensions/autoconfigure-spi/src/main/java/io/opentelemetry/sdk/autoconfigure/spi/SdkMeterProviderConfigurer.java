/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure.spi;

import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;

/**
 * A service provider interface (SPI) for performing additional programmatic configuration of a
 * {@link SdkMeterProviderBuilder} during initialization. When using auto-configuration, you should
 * prefer to use system properties or environment variables for configuration, but this may be
 * useful to register components that are not part of the SDK such as registering views.
 *
 * @since 1.1.0
 */
public interface SdkMeterProviderConfigurer {
  /** Configures the {@link SdkMeterProviderBuilder}. */
  void configure(SdkMeterProviderBuilder meterProviderBuilder, ConfigProperties config);
}
