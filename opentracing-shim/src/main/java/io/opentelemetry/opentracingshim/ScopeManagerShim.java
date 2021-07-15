/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opentracingshim;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

final class ScopeManagerShim extends BaseShimObject implements ScopeManager {
  private static final ContextKey<SpanShim> SPAN_SHIM_KEY =
      ContextKey.named("opentracing-shim-key");

  public ScopeManagerShim(TelemetryInfo telemetryInfo) {
    super(telemetryInfo);
  }

  @Override
  @SuppressWarnings("ReturnMissingNullable")
  public Span activeSpan() {
    Context context = Context.current();
    io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.fromContext(context);
    SpanShim spanShim = context.get(SPAN_SHIM_KEY);

    // As OpenTracing simply returns null when no active instance is available,
    // we need to do map an invalid OpenTelemetry span to null here.
    if (!span.getSpanContext().isValid()) {
      return null;
    }

    // If there's a SpanShim for the active Span, simply return it.
    if (spanShim != null && spanShim.getSpan() == span) {
      return spanShim;
    }

    // Span was activated from outside the Shim layer unfortunately.
    return new SpanShim(telemetryInfo(), span);
  }

  @Override
  @SuppressWarnings("MustBeClosedChecker")
  public Scope activate(Span span) {
    io.opentelemetry.api.trace.Span actualSpan = getActualSpan(span);
    Context context = Context.current().with(actualSpan).with(SPAN_SHIM_KEY, (SpanShim) span);
    return new ScopeShim(context.makeCurrent());
  }

  static io.opentelemetry.api.trace.Span getActualSpan(Span span) {
    if (!(span instanceof SpanShim)) {
      throw new IllegalArgumentException("span is not a valid SpanShim object");
    }

    return ((SpanShim) span).getSpan();
  }
}
