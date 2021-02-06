/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.extension.trace.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanIdHex;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceIdHex;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class OtTracerPropagatorTest {

  private static final String TRACE_ID = "ff000000000000000000000000000041";
  private static final String TRACE_ID_RIGHT_PART = "0000000000000041";
  private static final String SHORT_TRACE_ID = "ff00000000000000";
  private static final String SHORT_TRACE_ID_FULL = "0000000000000000ff00000000000000";
  private static final String SPAN_ID = "ff00000000000041";
  private static final Setter<Map<String, String>> setter = Map::put;
  private static final Getter<Map<String, String>> getter =
      new Getter<Map<String, String>>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Nullable
        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier.get(key);
        }
      };
  private final OtTracerPropagator propagator = OtTracerPropagator.getInstance();

  private static SpanContext getSpanContext(Context context) {
    return Span.fromContext(context).getSpanContext();
  }

  private static Context withSpanContext(SpanContext spanContext, Context context) {
    return context.with(Span.wrap(spanContext));
  }

  @Test
  void inject_invalidContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    propagator.inject(
        withSpanContext(
            SpanContext.create(
                TraceIdHex.getInvalid(),
                SpanIdHex.getInvalid(),
                TraceFlags.getSampled(),
                TraceState.builder().set("foo", "bar").build()),
            Context.current()),
        carrier,
        setter);
    assertThat(carrier).isEmpty();
  }

  @Test
  void inject_SampledContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    propagator.inject(
        withSpanContext(
            SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()),
            Context.current()),
        carrier,
        setter);
    assertThat(carrier).containsEntry(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID_RIGHT_PART);
    assertThat(carrier).containsEntry(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    assertThat(carrier).containsEntry(OtTracerPropagator.SAMPLED_HEADER, "true");
  }

  @Test
  void inject_SampledContext_nullCarrierUsage() {
    final Map<String, String> carrier = new LinkedHashMap<>();
    propagator.inject(
        withSpanContext(
            SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()),
            Context.current()),
        null,
        (Setter<Map<String, String>>) (ignored, key, value) -> carrier.put(key, value));
    assertThat(carrier).containsEntry(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID_RIGHT_PART);
    assertThat(carrier).containsEntry(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    assertThat(carrier).containsEntry(OtTracerPropagator.SAMPLED_HEADER, "true");
  }

  @Test
  void inject_NotSampledContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    propagator.inject(
        withSpanContext(
            SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault()),
            Context.current()),
        carrier,
        setter);
    assertThat(carrier).containsEntry(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID_RIGHT_PART);
    assertThat(carrier).containsEntry(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    assertThat(carrier).containsEntry(OtTracerPropagator.SAMPLED_HEADER, "false");
  }

  @Test
  void inject_Baggage() {
    Map<String, String> carrier = new LinkedHashMap<>();
    Baggage baggage = Baggage.builder().put("foo", "bar").put("key", "value").build();
    propagator.inject(
        withSpanContext(
            SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()),
            Context.current().with(baggage)),
        carrier,
        setter);
    assertThat(carrier).containsEntry(OtTracerPropagator.PREFIX_BAGGAGE_HEADER + "foo", "bar");
    assertThat(carrier).containsEntry(OtTracerPropagator.PREFIX_BAGGAGE_HEADER + "key", "value");
  }

  @Test
  void inject_Baggage_InvalidContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    Baggage baggage = Baggage.builder().put("foo", "bar").put("key", "value").build();
    propagator.inject(
        withSpanContext(
            SpanContext.create(
                TraceIdHex.getInvalid(),
                SpanIdHex.getInvalid(),
                TraceFlags.getSampled(),
                TraceState.getDefault()),
            Context.current().with(baggage)),
        carrier,
        setter);
    assertThat(carrier).isEmpty();
  }

  @Test
  void extract_Nothing() {
    // Context remains untouched.
    assertThat(
            propagator.extract(Context.current(), Collections.<String, String>emptyMap(), getter))
        .isSameAs(Context.current());
  }

  @Test
  void extract_SampledContext_Int() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID);
    carrier.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    carrier.put(OtTracerPropagator.SAMPLED_HEADER, Common.TRUE_INT);

    assertThat(getSpanContext(propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
  }

  @Test
  void extract_SampledContext_Bool() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID);
    carrier.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    carrier.put(OtTracerPropagator.SAMPLED_HEADER, "true");

    assertThat(getSpanContext(propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
  }

  @Test
  void extract_NotSampledContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID);
    carrier.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    carrier.put(OtTracerPropagator.SAMPLED_HEADER, Common.FALSE_INT);

    assertThat(getSpanContext(propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault()));
  }

  @Test
  void extract_SampledContext_Int_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(OtTracerPropagator.TRACE_ID_HEADER, SHORT_TRACE_ID);
    carrier.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    carrier.put(OtTracerPropagator.SAMPLED_HEADER, Common.TRUE_INT);

    assertThat(getSpanContext(propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID_FULL, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
  }

  @Test
  void extract_SampledContext_Bool_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(OtTracerPropagator.TRACE_ID_HEADER, SHORT_TRACE_ID);
    carrier.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    carrier.put(OtTracerPropagator.SAMPLED_HEADER, "true");

    assertThat(getSpanContext(propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID_FULL, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
  }

  @Test
  void extract_NotSampledContext_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(OtTracerPropagator.TRACE_ID_HEADER, SHORT_TRACE_ID);
    carrier.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    carrier.put(OtTracerPropagator.SAMPLED_HEADER, Common.FALSE_INT);

    assertThat(getSpanContext(propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID_FULL, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault()));
  }

  @Test
  void extract_InvalidTraceId() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(OtTracerPropagator.TRACE_ID_HEADER, "abcdefghijklmnopabcdefghijklmnop");
    invalidHeaders.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    invalidHeaders.put(OtTracerPropagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidTraceId_Size() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID + "00");
    invalidHeaders.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    invalidHeaders.put(OtTracerPropagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidSpanId() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID);
    invalidHeaders.put(OtTracerPropagator.SPAN_ID_HEADER, "abcdefghijklmnop");
    invalidHeaders.put(OtTracerPropagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidSpanId_Size() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID);
    invalidHeaders.put(OtTracerPropagator.SPAN_ID_HEADER, "abcdefghijklmnop" + "00");
    invalidHeaders.put(OtTracerPropagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_emptyCarrier() {
    Map<String, String> emptyHeaders = new HashMap<>();
    assertThat(getSpanContext(propagator.extract(Context.current(), emptyHeaders, getter)))
        .isEqualTo(SpanContext.getInvalid());
  }

  @Test
  void extract_Baggage() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(OtTracerPropagator.TRACE_ID_HEADER, TRACE_ID);
    carrier.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    carrier.put(OtTracerPropagator.SAMPLED_HEADER, Common.TRUE_INT);
    carrier.put(OtTracerPropagator.PREFIX_BAGGAGE_HEADER + "foo", "bar");
    carrier.put(OtTracerPropagator.PREFIX_BAGGAGE_HEADER + "key", "value");

    Context context = propagator.extract(Context.current(), carrier, getter);

    Baggage expectedBaggage = Baggage.builder().put("foo", "bar").put("key", "value").build();
    assertThat(Baggage.fromContext(context)).isEqualTo(expectedBaggage);
  }

  @Test
  void extract_Baggage_InvalidContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(OtTracerPropagator.TRACE_ID_HEADER, TraceIdHex.getInvalid());
    carrier.put(OtTracerPropagator.SPAN_ID_HEADER, SPAN_ID);
    carrier.put(OtTracerPropagator.SAMPLED_HEADER, Common.TRUE_INT);
    carrier.put(OtTracerPropagator.PREFIX_BAGGAGE_HEADER + "foo", "bar");
    carrier.put(OtTracerPropagator.PREFIX_BAGGAGE_HEADER + "key", "value");

    Context context = propagator.extract(Context.current(), carrier, getter);

    assertThat(Baggage.fromContext(context).isEmpty()).isTrue();
  }
}
