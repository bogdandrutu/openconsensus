/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.extension.trace.propagation;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;

/**
 * A {@link ConfigurablePropagatorProvider} which allows enabling the {@linkplain
 * B3Propagator#injectingMultiHeaders() B3-multi propagator} with the propagator name {@code
 * b3multi}.
 */
public final class B3MultiConfigurablePropagator implements ConfigurablePropagatorProvider {
  @Override
  public TextMapPropagator getPropagator(ConfigProperties config) {
    return B3Propagator.injectingMultiHeaders();
  }

  @Override
  public String getName() {
    return "b3multi";
  }
}
