/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.metrics.instrument.Measurement;

/**
 * An interface that determines if a synchronous measurement should be sampled into exemplars.
 *
 * <p>Note: This is an experimental "API" and not part of the specification.
 */
@FunctionalInterface
public interface ExemplarSampler {
  /** Returns true if a specific measurement should be sampled. */
  boolean shouldSample(Measurement measurement);

  /** Never samples exemplars. */
  public static ExemplarSampler NEVER = (measurement) -> false;

  /** Sample measurements that were recorded during a sampled trace. */
  public static ExemplarSampler WITH_SAMPLED_TRACES =
      (measurement) -> {
        final SpanContext spanContext = Span.fromContext(measurement.getContext()).getSpanContext();
        return spanContext.isValid() && spanContext.isSampled();
      };
}
