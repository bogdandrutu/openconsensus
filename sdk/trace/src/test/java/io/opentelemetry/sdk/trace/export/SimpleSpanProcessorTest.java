/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.TestUtils;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorTest.WaitingSpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for {@link SimpleSpanProcessor}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimpleSpanProcessorTest {
  private static final String SPAN_NAME = "MySpanName";
  @Mock private ReadableSpan readableSpan;
  @Mock private ReadWriteSpan readWriteSpan;
  @Mock private SpanExporter spanExporter;
  @Mock private Sampler mockSampler;
  private static final SpanContext SAMPLED_SPAN_CONTEXT =
      SpanContext.create(
          TraceId.getInvalid(),
          SpanId.getInvalid(),
          TraceFlags.getSampled(),
          TraceState.getDefault());
  private static final SpanContext NOT_SAMPLED_SPAN_CONTEXT = SpanContext.getInvalid();

  private SpanProcessor simpleSampledSpansProcessor;

  @BeforeEach
  void setUp() {
    simpleSampledSpansProcessor = SimpleSpanProcessor.create(spanExporter);
    when(spanExporter.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
  }

  @Test
  void createNull() {
    assertThatThrownBy(() -> SimpleSpanProcessor.create(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("exporter");
  }

  @Test
  void onStartSync() {
    simpleSampledSpansProcessor.onStart(Context.root(), readWriteSpan);
    verifyNoInteractions(spanExporter);
  }

  @Test
  void onEndSync_SampledSpan() {
    SpanData spanData = TestUtils.makeBasicSpan();
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData()).thenReturn(spanData);
    simpleSampledSpansProcessor.onEnd(readableSpan);
    verify(spanExporter).export(Collections.singletonList(spanData));
  }

  @Test
  void onEndSync_NotSampledSpan() {
    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    simpleSampledSpansProcessor.onEnd(readableSpan);
    verifyNoInteractions(spanExporter);
  }

  @Test
  void onEndSync_OnlySampled_NotSampledSpan() {
    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData())
        .thenReturn(TestUtils.makeBasicSpan())
        .thenThrow(new RuntimeException());
    SpanProcessor simpleSpanProcessor = SimpleSpanProcessor.create(spanExporter);
    simpleSpanProcessor.onEnd(readableSpan);
    verifyNoInteractions(spanExporter);
  }

  @Test
  void onEndSync_OnlySampled_SampledSpan() {
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData())
        .thenReturn(TestUtils.makeBasicSpan())
        .thenThrow(new RuntimeException());
    SpanProcessor simpleSpanProcessor = SimpleSpanProcessor.create(spanExporter);
    simpleSpanProcessor.onEnd(readableSpan);
    verify(spanExporter).export(Collections.singletonList(TestUtils.makeBasicSpan()));
  }

  @Test
  void tracerSdk_NotSampled_Span() {
    WaitingSpanExporter waitingSpanExporter =
        new WaitingSpanExporter(1, CompletableResultCode.ofSuccess());

    SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(waitingSpanExporter))
            .setSampler(mockSampler)
            .build();

    when(mockSampler.shouldSample(any(), any(), any(), any(), any(), anyList()))
        .thenReturn(SamplingResult.drop());

    try {
      Tracer tracer = sdkTracerProvider.get(getClass().getName());
      tracer.spanBuilder(SPAN_NAME).startSpan();
      tracer.spanBuilder(SPAN_NAME).startSpan();

      when(mockSampler.shouldSample(any(), any(), any(), any(), any(), anyList()))
          .thenReturn(SamplingResult.recordAndSample());
      Span span = tracer.spanBuilder(SPAN_NAME).startSpan();
      span.end();

      // Spans are recorded and exported in the same order as they are ended, we test that a non
      // sampled span is not exported by creating and ending a sampled span after a non sampled span
      // and checking that the first exported span is the sampled span (the non sampled did not get
      // exported).
      List<SpanData> exported = waitingSpanExporter.waitForExport();
      // Need to check this because otherwise the variable span1 is unused, other option is to not
      // have a span1 variable.
      assertThat(exported).containsExactly(((ReadableSpan) span).toSpanData());
    } finally {
      sdkTracerProvider.shutdown();
    }
  }

  @Test
  void tracerSdk_NotSampled_RecordingEventsSpan() {
    // TODO(bdrutu): Fix this when Sampler return RECORD_ONLY option.
    /*
    tracer.addSpanProcessor(
        BatchSpanProcessor.builder(waitingSpanExporter)
            .setScheduleDelayMillis(MAX_SCHEDULE_DELAY_MILLIS)
            .reportOnlySampled(false)
            .build());

    io.opentelemetry.trace.Span span =
        tracer
            .spanBuilder("FOO")
            .setSampler(Samplers.neverSample())
            .startSpanWithSampler();
    span.end();

    List<SpanData> exported = waitingSpanExporter.waitForExport(1);
    assertThat(exported).containsExactly(((ReadableSpan) span).toSpanData());
    */
  }

  @Test
  void onEndSync_ExporterReturnError() {
    SpanData spanData = TestUtils.makeBasicSpan();
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData()).thenReturn(spanData);
    simpleSampledSpansProcessor.onEnd(readableSpan);
    // Try again, now will no longer return error.
    simpleSampledSpansProcessor.onEnd(readableSpan);
    verify(spanExporter, times(2)).export(Collections.singletonList(spanData));
  }

  @Test
  void forceFlush() {
    CompletableResultCode export1 = new CompletableResultCode();
    CompletableResultCode export2 = new CompletableResultCode();

    when(spanExporter.export(any())).thenReturn(export1, export2);

    SpanData spanData = TestUtils.makeBasicSpan();
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData()).thenReturn(spanData);

    simpleSampledSpansProcessor.onEnd(readableSpan);
    simpleSampledSpansProcessor.onEnd(readableSpan);

    verify(spanExporter, times(2)).export(Collections.singletonList(spanData));

    CompletableResultCode flush = simpleSampledSpansProcessor.forceFlush();
    assertThat(flush.isDone()).isFalse();

    export1.succeed();
    assertThat(flush.isDone()).isFalse();

    export2.succeed();
    assertThat(flush.isDone()).isTrue();
    assertThat(flush.isSuccess()).isTrue();
  }

  @Test
  void shutdown() {
    CompletableResultCode export1 = new CompletableResultCode();
    CompletableResultCode export2 = new CompletableResultCode();

    when(spanExporter.export(any())).thenReturn(export1, export2);

    SpanData spanData = TestUtils.makeBasicSpan();
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData()).thenReturn(spanData);

    simpleSampledSpansProcessor.onEnd(readableSpan);
    simpleSampledSpansProcessor.onEnd(readableSpan);

    verify(spanExporter, times(2)).export(Collections.singletonList(spanData));

    CompletableResultCode shutdown = simpleSampledSpansProcessor.shutdown();
    assertThat(shutdown.isDone()).isFalse();

    export1.succeed();
    assertThat(shutdown.isDone()).isFalse();
    verify(spanExporter, never()).shutdown();

    export2.succeed();
    assertThat(shutdown.isDone()).isTrue();
    assertThat(shutdown.isSuccess()).isTrue();
    verify(spanExporter).shutdown();
  }

  @Test
  void close() {
    simpleSampledSpansProcessor.close();
    verify(spanExporter).shutdown();
  }
}
