/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import org.junit.jupiter.api.Test;

public class ExemplarSamplerTest {
  private static final String TRACE_ID = "ff000000000000000000000000000041";
  private static final String SPAN_ID = "ff00000000000041";

  @Test
  void never_NeverReturnsTrue() {
    assertThat(
            ExemplarSampler.NEVER.shouldSample(
                LongMeasurement.createNoContext(1, Attributes.empty())))
        .isFalse();
  }

  @Test
  void withSampledTrace_ReturnsFalseOnNoContext() {
    assertThat(
            ExemplarSampler.WITH_SAMPLED_TRACES.shouldSample(
                LongMeasurement.createNoContext(1, Attributes.empty())))
        .isFalse();
  }

  @Test
  void withSampledTrace_sampleWithTrace() {
    final Context context =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.createFromRemoteParent(
                        TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault())));
    assertThat(
            ExemplarSampler.WITH_SAMPLED_TRACES.shouldSample(
                LongMeasurement.create(1, Attributes.empty(), context)))
        .isTrue();
  }

  @Test
  void withSampledTrace_notSampleUnsampledTrace() {
    final Context context =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.createFromRemoteParent(
                        TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault())));
    assertThat(
            ExemplarSampler.WITH_SAMPLED_TRACES.shouldSample(
                LongMeasurement.create(1, Attributes.empty(), context)))
        .isFalse();
  }
}
