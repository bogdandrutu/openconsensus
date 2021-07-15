/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opentracingshim;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.BooleanTag;
import io.opentracing.tag.IntTag;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tag;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TracerShimTest {

  @RegisterExtension public OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  TracerShim tracerShim;
  Tracer tracer;

  @BeforeEach
  void setUp() {
    tracer = otelTesting.getOpenTelemetry().getTracer("opentracingshim");
    tracerShim =
        new TracerShim(new TelemetryInfo(tracer, OpenTracingPropagators.builder().build()));
  }

  @Test
  void defaultTracer() {
    assertThat(tracerShim.buildSpan("one")).isNotNull();
    assertThat(tracerShim.scopeManager()).isNotNull();
    assertThat(tracerShim.activeSpan()).isNull();
    assertThat(tracerShim.scopeManager().activeSpan()).isNull();
  }

  @Test
  void activateSpan() {
    Span otSpan = tracerShim.buildSpan("one").start();
    io.opentelemetry.api.trace.Span span = ((SpanShim) otSpan).getSpan();

    assertThat(tracerShim.activeSpan()).isNull();
    assertThat(tracerShim.scopeManager().activeSpan()).isNull();

    try (Scope scope = tracerShim.activateSpan(otSpan)) {
      assertThat(tracerShim.activeSpan()).isNotNull();
      assertThat(tracerShim.scopeManager().activeSpan()).isNotNull();
      assertThat(((SpanShim) tracerShim.activeSpan()).getSpan()).isEqualTo(span);
      assertThat(((SpanShim) tracerShim.scopeManager().activeSpan()).getSpan()).isEqualTo(span);
    }

    assertThat(tracerShim.activeSpan()).isNull();
    assertThat(tracerShim.scopeManager().activeSpan()).isNull();
  }

  @Test
  void activateSpan_withoutShim() {
    // Create and activate a Span without the Shim layer, in order to verify
    // we keep on working as expected (even if not as efficiently).
    io.opentelemetry.api.trace.Span span =
        otelTesting.getOpenTelemetry().getTracer("opentracingshim").spanBuilder("one").startSpan();
    try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
      assertThat(tracerShim.activeSpan()).isNotNull();
      assertThat(tracerShim.scopeManager().activeSpan()).isNotNull();
      assertThat(((SpanShim) tracerShim.activeSpan()).getSpan()).isEqualTo(span);
      assertThat(((SpanShim) tracerShim.scopeManager().activeSpan()).getSpan()).isEqualTo(span);
    }

    assertThat(tracerShim.activeSpan()).isNull();
    assertThat(tracerShim.scopeManager().activeSpan()).isNull();
  }

  @Test
  void extract_nullContext() {
    SpanContext result =
        tracerShim.extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(Collections.emptyMap()));
    assertThat(result).isNull();
  }

  @Test
  void inject_nullContext() {
    Map<String, String> map = new HashMap<>();
    tracerShim.inject(null, Format.Builtin.TEXT_MAP, new TextMapAdapter(map));
    assertThat(map).isEmpty();
  }

  @Test
  void inject_textMap() {
    Map<String, String> map = new HashMap<>();
    CustomTextMapPropagator textMapPropagator = new CustomTextMapPropagator();
    CustomTextMapPropagator httpHeadersPropagator = new CustomTextMapPropagator();
    TelemetryInfo telemetryInfo =
        new TelemetryInfo(
            tracer,
            OpenTracingPropagators.builder()
                .setTextMap(textMapPropagator)
                .setHttpHeaders(httpHeadersPropagator)
                .build());
    tracerShim = new TracerShim(telemetryInfo);
    io.opentelemetry.api.trace.Span span = telemetryInfo.tracer().spanBuilder("span").startSpan();
    SpanContext context = new SpanShim(telemetryInfo, span).context();

    tracerShim.inject(context, Format.Builtin.TEXT_MAP, new TextMapAdapter(map));
    assertThat(textMapPropagator.isInjected()).isTrue();
    assertThat(httpHeadersPropagator.isInjected()).isFalse();
  }

  @Test
  void inject_httpHeaders() {
    Map<String, String> map = new HashMap<>();
    CustomTextMapPropagator textMapPropagator = new CustomTextMapPropagator();
    CustomTextMapPropagator httpHeadersPropagator = new CustomTextMapPropagator();
    TelemetryInfo telemetryInfo =
        new TelemetryInfo(
            tracer,
            OpenTracingPropagators.builder()
                .setTextMap(textMapPropagator)
                .setHttpHeaders(httpHeadersPropagator)
                .build());
    tracerShim = new TracerShim(telemetryInfo);
    io.opentelemetry.api.trace.Span span = telemetryInfo.tracer().spanBuilder("span").startSpan();
    SpanContext context = new SpanShim(telemetryInfo, span).context();

    tracerShim.inject(context, Format.Builtin.HTTP_HEADERS, new TextMapAdapter(map));
    assertThat(textMapPropagator.isInjected()).isFalse();
    assertThat(httpHeadersPropagator.isInjected()).isTrue();
  }

  @Test
  void extract_textMap() {
    Map<String, String> map = new HashMap<>();
    CustomTextMapPropagator textMapPropagator = new CustomTextMapPropagator();
    CustomTextMapPropagator httpHeadersPropagator = new CustomTextMapPropagator();
    tracerShim =
        new TracerShim(
            new TelemetryInfo(
                tracer,
                OpenTracingPropagators.builder()
                    .setTextMap(textMapPropagator)
                    .setHttpHeaders(httpHeadersPropagator)
                    .build()));

    tracerShim.extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(map));
    assertThat(textMapPropagator.isExtracted()).isTrue();
    assertThat(httpHeadersPropagator.isExtracted()).isFalse();
  }

  @Test
  void extract_httpHeaders() {
    Map<String, String> map = new HashMap<>();
    CustomTextMapPropagator textMapPropagator = new CustomTextMapPropagator();
    CustomTextMapPropagator httpHeadersPropagator = new CustomTextMapPropagator();
    tracerShim =
        new TracerShim(
            new TelemetryInfo(
                tracer,
                OpenTracingPropagators.builder()
                    .setTextMap(textMapPropagator)
                    .setHttpHeaders(httpHeadersPropagator)
                    .build()));

    tracerShim.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(map));
    assertThat(textMapPropagator.isExtracted()).isFalse();
    assertThat(httpHeadersPropagator.isExtracted()).isTrue();
  }

  @Test
  void close() {
    tracerShim.close();
    Span otSpan = tracerShim.buildSpan(null).start();
    io.opentelemetry.api.trace.Span span = ((SpanShim) otSpan).getSpan();
    assertThat(span.getSpanContext().isValid()).isFalse();
  }

  @Test
  void doesNotCrash() {
    Span span =
        tracerShim
            .buildSpan("test")
            .asChildOf((Span) null)
            .asChildOf((SpanContext) null)
            .addReference(null, null)
            .addReference("parent", tracerShim.buildSpan("parent").start().context())
            .ignoreActiveSpan()
            .withTag((Tag<?>) null, null)
            .withTag("foo", (String) null)
            .withTag("bar", false)
            .withTag("cat", (Number) null)
            .withTag("dog", 0.0f)
            .withTag("bear", 10)
            .withTag(new StringTag("string"), "string")
            .withTag(new BooleanTag("boolean"), false)
            .withTag(new IntTag("int"), 10)
            .start();

    span.setTag((Tag<?>) null, null)
        .setTag("foo", (String) null)
        .setTag("bar", false)
        .setTag("cat", (Number) null)
        .setTag("dog", 0.0f)
        .setTag("bear", 10)
        .setTag(new StringTag("string"), "string")
        .setTag(new BooleanTag("boolean"), false)
        .setTag(new IntTag("int"), 10)
        .log(10, new HashMap<>())
        .log(20, "foo")
        .setBaggageItem(null, null)
        .setOperationName("name")
        .setTag(Tags.ERROR.getKey(), "true");

    assertThat(((SpanShim) span).getSpan().isRecording()).isTrue();
  }

  @Test
  void noopDoesNotCrash() {
    tracerShim.close();
    Span span =
        tracerShim
            .buildSpan("test")
            .asChildOf((Span) null)
            .asChildOf((SpanContext) null)
            .addReference(null, null)
            .ignoreActiveSpan()
            .withTag((Tag<?>) null, null)
            .withTag("foo", (String) null)
            .withTag("bar", false)
            .withTag("cat", (Number) null)
            .withStartTimestamp(0)
            .start();

    assertThat(((SpanShim) span).getSpan().isRecording()).isFalse();
  }
}
