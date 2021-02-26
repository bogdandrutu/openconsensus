/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.trace;

import io.opentelemetry.api.internal.OtelEncodingUtils;
import javax.annotation.concurrent.Immutable;

/**
 * A class that represents a span context. A span context contains the state that must propagate to
 * child {@link Span}s and across process boundaries. It contains the identifiers (a {@link TraceId
 * trace_id} and {@link SpanId span_id}) associated with the {@link Span} and a set of options
 * (currently only whether the context is sampled or not), as well as the {@link TraceState
 * traceState} and the {@link boolean remote} flag.
 *
 * <p>Implementations of this interface *must* be immutable and have well-defined value-based
 * equals/hashCode implementations. If an implementation does not strictly conform to these
 * requirements, behavior of the OpenTelemetry APIs and default SDK cannot be guaranteed. It is
 * strongly suggested that you use the implementation that is provided here via {@link
 * #create(String, String, TraceFlags, TraceState)} or {@link #createFromRemoteParent(String,
 * String, TraceFlags, TraceState)}.
 */
@Immutable
public interface SpanContext {

  /**
   * Returns the invalid {@code SpanContext} that can be used for no-op operations.
   *
   * @return the invalid {@code SpanContext}.
   */
  static SpanContext getInvalid() {
    return ImmutableSpanContext.INVALID;
  }

  /**
   * Creates a new {@code SpanContext} with the given identifiers and options.
   *
   * <p>If the traceId or the spanId are invalid (ie. do not conform to the requirements for
   * hexadecimal ids of the appropriate lengths), both will be replaced with the standard "invalid"
   * versions (i.e. all '0's). See {@link SpanId#isValid(CharSequence)} and {@link
   * TraceId#isValid(CharSequence)} for details.
   *
   * @param traceIdHex the trace identifier of the {@code SpanContext}.
   * @param spanIdHex the span identifier of the {@code SpanContext}.
   * @param traceFlags the trace flags of the {@code SpanContext}.
   * @param traceState the trace state for the {@code SpanContext}.
   * @return a new {@code SpanContext} with the given identifiers and options.
   */
  static SpanContext create(
      String traceIdHex, String spanIdHex, TraceFlags traceFlags, TraceState traceState) {
    return ImmutableSpanContext.create(
        traceIdHex, spanIdHex, traceFlags, traceState, /* remote=*/ false);
  }

  /**
   * Creates a new {@code SpanContext} that was propagated from a remote parent, with the given
   * identifiers and options.
   *
   * <p>If the traceId or the spanId are invalid (ie. do not conform to the requirements for
   * hexadecimal ids of the appropriate lengths), both will be replaced with the standard "invalid"
   * versions (i.e. all '0's). See {@link SpanId#isValid(CharSequence)} and {@link
   * TraceId#isValid(CharSequence)} for details.
   *
   * @param traceIdHex the trace identifier of the {@code SpanContext}.
   * @param spanIdHex the span identifier of the {@code SpanContext}.
   * @param traceFlags the trace flags of the {@code SpanContext}.
   * @param traceState the trace state for the {@code SpanContext}.
   * @return a new {@code SpanContext} with the given identifiers and options.
   */
  static SpanContext createFromRemoteParent(
      String traceIdHex, String spanIdHex, TraceFlags traceFlags, TraceState traceState) {
    return ImmutableSpanContext.create(
        traceIdHex, spanIdHex, traceFlags, traceState, /* remote=*/ true);
  }

  /**
   * Returns the trace identifier associated with this {@link SpanContext} as 32 character lowercase
   * hex String.
   *
   * @return the trace identifier associated with this {@link SpanContext} as lowercase hex.
   */
  String getTraceId();

  /**
   * Returns the trace identifier associated with this {@link SpanContext} as 16-byte array.
   *
   * @return the trace identifier associated with this {@link SpanContext} as 16-byte array.
   */
  default byte[] getTraceIdBytes() {
    return OtelEncodingUtils.bytesFromBase16(getTraceId(), TraceId.getLength());
  }

  /**
   * Returns the span identifier associated with this {@link SpanContext} as 16 character lowercase
   * hex String.
   *
   * @return the span identifier associated with this {@link SpanContext} as 16 character lowercase
   *     hex (base16) String.
   */
  String getSpanId();

  /**
   * Returns the span identifier associated with this {@link SpanContext} as 8-byte array.
   *
   * @return the span identifier associated with this {@link SpanContext} as 8-byte array.
   */
  default byte[] getSpanIdBytes() {
    return OtelEncodingUtils.bytesFromBase16(getSpanId(), SpanId.getLength());
  }

  /** Whether the span in this context is sampled. */
  default boolean isSampled() {
    return getTraceFlags().isSampled();
  }

  /**
   * Returns the trace flags associated with this {@link SpanContext}.
   *
   * @return the trace flags associated with this {@link SpanContext}.
   */
  TraceFlags getTraceFlags();

  /**
   * Returns the {@code TraceState} associated with this {@code SpanContext}.
   *
   * @return the {@code TraceState} associated with this {@code SpanContext}.
   */
  TraceState getTraceState();

  /**
   * Returns {@code true} if this {@code SpanContext} is valid.
   *
   * @return {@code true} if this {@code SpanContext} is valid.
   */
  default boolean isValid() {
    return TraceId.isValid(getTraceId())
        && SpanId.isValid(getSpanId())
        && getTraceFlags().isValid();
  }

  /**
   * Returns {@code true} if the {@code SpanContext} was propagated from a remote parent.
   *
   * @return {@code true} if the {@code SpanContext} was propagated from a remote parent.
   */
  boolean isRemote();
}
