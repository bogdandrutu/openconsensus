/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.context.propagation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A builder for configuring a {@link TextMapPropagator} specifying which propagators should be used
 * for extracting the context and which should be used for injecting the context.
 */
public final class CompositeTextMapPropagatorBuilder {

  private final List<TextMapPropagator> extractors;
  private final List<TextMapPropagator> injectors;

  /**
   * Package protected to disallow direct initialization.
   *
   * @see TextMapPropagator#builder()
   */
  CompositeTextMapPropagatorBuilder() {
    this.extractors = new ArrayList<>();
    this.injectors = new ArrayList<>();
  }

  /** Adds a {@link TextMapPropagator} to be used only when extracting context. */
  public CompositeTextMapPropagatorBuilder addExtractor(TextMapPropagator propagator) {
    Objects.requireNonNull(propagator, "propagator");
    this.extractors.add(propagator);
    return this;
  }

  /** Adds a {@link TextMapPropagator} to be used only when injecting context. */
  public CompositeTextMapPropagatorBuilder addInjector(TextMapPropagator propagator) {
    Objects.requireNonNull(propagator, "propagator");
    this.injectors.add(propagator);
    return this;
  }

  /** Adds a {@link TextMapPropagator} to be used both when extracting and injecting context. */
  public CompositeTextMapPropagatorBuilder addPropagator(TextMapPropagator propagator) {
    Objects.requireNonNull(propagator, "propagator");
    this.injectors.add(propagator);
    this.extractors.add(propagator);
    return this;
  }

  /**
   * Returns the built {@link TextMapPropagator}.
   *
   * @see CompositeTextMapPropagatorBuilder
   */
  public TextMapPropagator build() {
    if (this.injectors.isEmpty() && this.extractors.isEmpty()) {
      return NoopTextMapPropagator.getInstance();
    }

    return new CompositeTextMapPropagator(this.extractors, this.injectors);
  }
}
