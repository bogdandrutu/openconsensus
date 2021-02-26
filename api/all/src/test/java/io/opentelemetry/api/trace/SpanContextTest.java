/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link SpanContext}. */
class SpanContextTest {
  private static final String FIRST_TRACE_ID = "00000000000000000000000000000061";
  private static final String SECOND_TRACE_ID = "00000000000000300000000000000000";
  private static final String FIRST_SPAN_ID = "0000000000000061";
  private static final String SECOND_SPAN_ID = "3000000000000000";
  private static final TraceState FIRST_TRACE_STATE =
      TraceState.builder().put("foo", "bar").build();
  private static final TraceState SECOND_TRACE_STATE =
      TraceState.builder().put("foo", "baz").build();
  private static final SpanContext first =
      SpanContext.create(FIRST_TRACE_ID, FIRST_SPAN_ID, TraceFlags.getDefault(), FIRST_TRACE_STATE);
  private static final SpanContext second =
      SpanContext.create(
          SECOND_TRACE_ID, SECOND_SPAN_ID, TraceFlags.getSampled(), SECOND_TRACE_STATE);
  private static final SpanContext remote =
      SpanContext.createFromRemoteParent(
          SECOND_TRACE_ID, SECOND_SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());

  @Test
  void invalidSpanContext() {
    assertThat(SpanContext.getInvalid().getTraceId()).isEqualTo(TraceId.getInvalid());
    assertThat(SpanContext.getInvalid().getSpanId()).isEqualTo(SpanId.getInvalid());
    assertThat(SpanContext.getInvalid().getTraceFlags()).isEqualTo(TraceFlags.getDefault());
  }

  @Test
  void isValid() {
    assertThat(SpanContext.getInvalid().isValid()).isFalse();
    assertThat(
            SpanContext.create(
                    FIRST_TRACE_ID,
                    SpanId.getInvalid(),
                    TraceFlags.getDefault(),
                    TraceState.getDefault())
                .isValid())
        .isFalse();
    assertThat(
            SpanContext.create(
                    TraceId.getInvalid(),
                    FIRST_SPAN_ID,
                    TraceFlags.getDefault(),
                    TraceState.getDefault())
                .isValid())
        .isFalse();
    assertThat(first.isValid()).isTrue();
    assertThat(second.isValid()).isTrue();
    assertThat(
            SpanContext.create(
                    FIRST_TRACE_ID,
                    FIRST_SPAN_ID,
                    TraceFlags.fromHex("abc", 3),
                    TraceState.getDefault())
                .isValid())
        .isFalse();
  }

  @Test
  void getTraceId() {
    assertThat(first.getTraceId()).isEqualTo(FIRST_TRACE_ID);
    assertThat(second.getTraceId()).isEqualTo(SECOND_TRACE_ID);
  }

  @Test
  void getSpanId() {
    assertThat(first.getSpanId()).isEqualTo(FIRST_SPAN_ID);
    assertThat(second.getSpanId()).isEqualTo(SECOND_SPAN_ID);
  }

  @Test
  void getTraceFlags() {
    assertThat(first.getTraceFlags()).isEqualTo(TraceFlags.getDefault());
    assertThat(second.getTraceFlags()).isEqualTo(TraceFlags.getSampled());
  }

  @Test
  void getTraceState() {
    assertThat(first.getTraceState()).isEqualTo(FIRST_TRACE_STATE);
    assertThat(second.getTraceState()).isEqualTo(SECOND_TRACE_STATE);
  }

  @Test
  void isRemote() {
    assertThat(first.isRemote()).isFalse();
    assertThat(second.isRemote()).isFalse();
    assertThat(remote.isRemote()).isTrue();
  }
}
