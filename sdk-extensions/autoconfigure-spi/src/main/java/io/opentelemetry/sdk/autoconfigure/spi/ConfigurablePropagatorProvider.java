/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure.spi;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;

/**
 * A service provider interface (SPI) for providing additional propagators that can be used with the
 * autoconfigured SDK. If the {@code otel.propagators} property contains a value equal to what is
 * returned by {@link #getName()}, the propagator returned by {@link
 * #getPropagator(ConfigProperties)} will be enabled and available as part of {@link
 * OpenTelemetry#getPropagators()}.
 */
public interface ConfigurablePropagatorProvider {
  /**
   * Returns a {@link TextMapPropagator} that can be registered to OpenTelemetry by providing the
   * property value specified by {@link #getName()}.
   */
  TextMapPropagator getPropagator(ConfigProperties config);

  /**
   * Returns the name of this propagator, which can be specified with the {@code otel.propagators}
   * property to enable it. If the name is the same as any other defined propagator name, it is
   * undefined which will be used.
   */
  String getName();
}
