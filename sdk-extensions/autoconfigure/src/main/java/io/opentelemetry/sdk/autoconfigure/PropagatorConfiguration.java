/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

final class PropagatorConfiguration {

  static ContextPropagators configurePropagators(ConfigProperties config) {
    Map<String, TextMapPropagator> spiPropagators =
        StreamSupport.stream(
                ServiceLoader.load(ConfigurablePropagatorProvider.class).spliterator(), false)
            .collect(
                Collectors.toMap(
                    ConfigurablePropagatorProvider::getName,
                    configurablePropagatorProvider ->
                        configurablePropagatorProvider.getPropagator(config)));

    Set<TextMapPropagator> propagators = new LinkedHashSet<>();
    List<String> requestedPropagators = config.getCommaSeparatedValues("otel.propagators");
    if (requestedPropagators.isEmpty()) {
      requestedPropagators = Arrays.asList("tracecontext", "baggage");
    }
    for (String propagatorName : requestedPropagators) {
      propagators.add(getPropagator(propagatorName, spiPropagators));
    }

    return ContextPropagators.create(TextMapPropagator.composite(propagators));
  }

  private static TextMapPropagator getPropagator(
      String name, Map<String, TextMapPropagator> spiPropagators) {
    if (name.equals("tracecontext")) {
      return W3CTraceContextPropagator.getInstance();
    }
    if (name.equals("baggage")) {
      return W3CBaggagePropagator.getInstance();
    }

    TextMapPropagator spiPropagator = spiPropagators.get(name);
    if (spiPropagator != null) {
      return spiPropagator;
    }
    throw new ConfigurationException(
        "Unrecognized value for otel.propagators: "
            + name
            + ". Make sure the artifact including the propagator is on the classpath.");
  }

  private PropagatorConfiguration() {}
}
