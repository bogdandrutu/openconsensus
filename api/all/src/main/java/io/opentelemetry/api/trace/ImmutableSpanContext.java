/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.trace;

import com.google.auto.value.AutoValue;
import javax.annotation.concurrent.Immutable;

@Immutable
@AutoValue
abstract class ImmutableSpanContext implements SpanContext {

  static final SpanContext INVALID =
      createInternal(
          TraceId.getInvalid(),
          SpanId.getInvalid(),
          TraceFlags.getDefault(),
          TraceState.getDefault(),
          /* remote= */ false,
          /* valid= */ false);

  private static AutoValue_ImmutableSpanContext createInternal(
      String traceId,
      String spanId,
      TraceFlags traceFlags,
      TraceState traceState,
      boolean remote,
      boolean valid) {
    return new AutoValue_ImmutableSpanContext(
        traceId, spanId, traceFlags, traceState, remote, valid);
  }

  static SpanContext create(
      String traceIdHex,
      String spanIdHex,
      TraceFlags traceFlags,
      TraceState traceState,
      boolean remote) {
    if (SpanId.isValid(spanIdHex) && TraceId.isValid(traceIdHex) && traceFlags.isValid()) {
      return createInternal(
          traceIdHex, spanIdHex, traceFlags, traceState, remote, /* valid= */ true);
    }
    return createInternal(
        TraceId.getInvalid(),
        SpanId.getInvalid(),
        traceFlags,
        traceState,
        remote,
        /* valid= */ false);
  }

  @Override
  public abstract boolean isValid();
}
