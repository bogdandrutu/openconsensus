/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.metrics.data.DoubleExemplar;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramPointData;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.Exemplar;
import io.opentelemetry.sdk.metrics.data.LongExemplar;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.instrument.DoubleMeasurement;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MetricDataUtils {
  private MetricDataUtils() {}

  static List<LongPointData> toLongPointList(
      Map<Attributes, LongAccumulation> accumulationMap, long startEpochNanos, long epochNanos) {
    List<LongPointData> points = new ArrayList<>(accumulationMap.size());
    accumulationMap.forEach(
        (attributes, accumulation) ->
            points.add(
                LongPointData.create(
                    startEpochNanos,
                    epochNanos,
                    attributes,
                    accumulation.getValue(),
                    // TODO: We should preserve original recording time.
                    toExemplarList(accumulation.getExemplars(), epochNanos, attributes))));
    return points;
  }

  static List<DoublePointData> toDoublePointList(
      Map<Attributes, DoubleAccumulation> accumulationMap, long startEpochNanos, long epochNanos) {
    List<DoublePointData> points = new ArrayList<>(accumulationMap.size());
    accumulationMap.forEach(
        (attributes, accumulation) ->
            points.add(
                DoublePointData.create(
                    startEpochNanos,
                    epochNanos,
                    attributes,
                    accumulation.getValue(),
                    // TODO: We should preserve original recording time.
                    toExemplarList(accumulation.getExemplars(), epochNanos, attributes))));
    return points;
  }

  static List<DoubleHistogramPointData> toDoubleHistogramPointList(
      Map<Attributes, HistogramAccumulation> accumulationMap,
      long startEpochNanos,
      long epochNanos,
      List<Double> boundaries) {
    List<DoubleHistogramPointData> points = new ArrayList<>(accumulationMap.size());
    accumulationMap.forEach(
        (attributes, aggregator) -> {
          List<Long> counts = new ArrayList<>(aggregator.getCounts().length);
          for (long v : aggregator.getCounts()) {
            counts.add(v);
          }
          points.add(
              DoubleHistogramPointData.create(
                  startEpochNanos,
                  epochNanos,
                  attributes,
                  aggregator.getSum(),
                  boundaries,
                  counts,
                  // TODO: We should preserve original recording time.
                  toExemplarList(aggregator.getExemplars(), epochNanos, attributes)));
        });
    return points;
  }

  static List<Exemplar> toExemplarList(
      Iterable<Measurement> measurements, long epochNanos, Attributes aggregatedAttributes) {
    List<Exemplar> exemplars = new ArrayList<>();
    for (Measurement m : measurements) {
      // TODO: Filter attributes not in aggregated set.
      // We tried to implement this but ran afoul of some template-type-hell.
      Attributes filtered = Attributes.empty();
      final SpanContext spanContext = Span.fromContext(m.getContext()).getSpanContext();
      String spanId = spanContext.isValid() ? spanContext.getSpanId() : null;
      String traceId = spanContext.isValid() ? spanContext.getTraceId() : null;
      if (m instanceof LongMeasurement) {
        LongMeasurement measurement = (LongMeasurement) m;
        exemplars.add(
            LongExemplar.create(filtered, epochNanos, spanId, traceId, measurement.getValue()));
      } else {
        DoubleMeasurement measurement = (DoubleMeasurement) m;
        exemplars.add(
            DoubleExemplar.create(filtered, epochNanos, spanId, traceId, measurement.getValue()));
      }
    }
    return exemplars;
  }
}
