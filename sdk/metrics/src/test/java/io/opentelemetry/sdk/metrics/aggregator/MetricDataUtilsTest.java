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
import io.opentelemetry.sdk.metrics.data.DoubleExemplar;
import io.opentelemetry.sdk.metrics.data.Exemplar;
import io.opentelemetry.sdk.metrics.data.LongExemplar;
import io.opentelemetry.sdk.metrics.instrument.DoubleMeasurement;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MetricDataUtilsTest {
  private static final String TRACE_ID = "ff000000000000000000000000000041";
  private static final String SPAN_ID = "ff00000000000041";

  @Test
  void toExemplarList_convertsLongWithTraces() {
    List<Measurement> list = new ArrayList<>();
    final Context context =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.createFromRemoteParent(
                        TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault())));
    list.add(LongMeasurement.create(1, Attributes.empty(), context));

    Collection<Exemplar> exemplars = MetricDataUtils.toExemplarList(list, 1L, Attributes.empty());
    assertThat(exemplars).hasSize(1);
    Exemplar exemplar = exemplars.iterator().next();
    assertThat(exemplar.getFilteredAttributes()).isEqualTo(Attributes.empty());
    assertThat(exemplar.getEpochNanos()).isEqualTo(1L);
    assertThat(exemplar.getTraceId()).isEqualTo(TRACE_ID);
    assertThat(exemplar.getSpanId()).isEqualTo(SPAN_ID);
    assertThat(((LongExemplar) exemplar).getValue()).isEqualTo(1L);
  }

  @Test
  void toExemplarList_convertsDoubleWithTraces() {
    List<Measurement> list = new ArrayList<>();
    final Context context =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.createFromRemoteParent(
                        TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault())));
    list.add(DoubleMeasurement.create(1d, Attributes.empty(), context));

    Collection<Exemplar> exemplars = MetricDataUtils.toExemplarList(list, 1L, Attributes.empty());
    assertThat(exemplars).hasSize(1);
    Exemplar exemplar = exemplars.iterator().next();
    assertThat(exemplar.getFilteredAttributes()).isEqualTo(Attributes.empty());
    assertThat(exemplar.getEpochNanos()).isEqualTo(1L);
    assertThat(exemplar.getTraceId()).isEqualTo(TRACE_ID);
    assertThat(exemplar.getSpanId()).isEqualTo(SPAN_ID);
    assertThat(((DoubleExemplar) exemplar).getValue()).isEqualTo(1d);
  }
}
