/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.otlp.internal;

import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE;
import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA;
import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_UNSPECIFIED;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import io.opentelemetry.api.internal.OtelEncodingUtils;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Exemplar;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Histogram;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.InstrumentationLibraryMetrics;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.metrics.v1.Summary;
import io.opentelemetry.proto.metrics.v1.SummaryDataPoint;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.ThrottlingLogger;
import io.opentelemetry.sdk.metrics.data.DoubleExemplar;
import io.opentelemetry.sdk.metrics.data.DoubleGaugeData;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramData;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramPointData;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.DoubleSumData;
import io.opentelemetry.sdk.metrics.data.LongExemplar;
import io.opentelemetry.sdk.metrics.data.LongGaugeData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.LongSumData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Converter from SDK {@link MetricData} to OTLP {@link ResourceMetrics}. */
public final class MetricAdapter {

  private static final ThrottlingLogger logger =
      new ThrottlingLogger(Logger.getLogger(MetricAdapter.class.getName()));

  /** Converts the provided {@link MetricData} to {@link ResourceMetrics}. */
  public static List<ResourceMetrics> toProtoResourceMetrics(Collection<MetricData> metricData) {
    Map<Resource, Map<InstrumentationLibraryInfo, List<Metric>>> resourceAndLibraryMap =
        groupByResourceAndLibrary(metricData);
    List<ResourceMetrics> resourceMetrics = new ArrayList<>(resourceAndLibraryMap.size());
    for (Map.Entry<Resource, Map<InstrumentationLibraryInfo, List<Metric>>> entryResource :
        resourceAndLibraryMap.entrySet()) {
      List<InstrumentationLibraryMetrics> instrumentationLibraryMetrics =
          new ArrayList<>(entryResource.getValue().size());
      for (Map.Entry<InstrumentationLibraryInfo, List<Metric>> entryLibrary :
          entryResource.getValue().entrySet()) {
        instrumentationLibraryMetrics.add(buildInstrumentationLibraryMetrics(entryLibrary));
      }
      resourceMetrics.add(
          buildResourceMetrics(entryResource.getKey(), instrumentationLibraryMetrics));
    }
    return resourceMetrics;
  }

  private static ResourceMetrics buildResourceMetrics(
      Resource resource, List<InstrumentationLibraryMetrics> instrumentationLibraryMetrics) {
    ResourceMetrics.Builder resourceMetricsBuilder =
        ResourceMetrics.newBuilder()
            .setResource(ResourceAdapter.toProtoResource(resource))
            .addAllInstrumentationLibraryMetrics(instrumentationLibraryMetrics);
    String schemaUrl = resource.getSchemaUrl();
    if (schemaUrl != null) {
      resourceMetricsBuilder.setSchemaUrl(schemaUrl);
    }
    return resourceMetricsBuilder.build();
  }

  private static InstrumentationLibraryMetrics buildInstrumentationLibraryMetrics(
      Map.Entry<InstrumentationLibraryInfo, List<Metric>> entryLibrary) {
    InstrumentationLibraryMetrics.Builder metricsBuilder =
        InstrumentationLibraryMetrics.newBuilder()
            .setInstrumentationLibrary(
                CommonAdapter.toProtoInstrumentationLibrary(entryLibrary.getKey()))
            .addAllMetrics(entryLibrary.getValue());
    if (entryLibrary.getKey().getSchemaUrl() != null) {
      metricsBuilder.setSchemaUrl(entryLibrary.getKey().getSchemaUrl());
    }
    return metricsBuilder.build();
  }

  private static Map<Resource, Map<InstrumentationLibraryInfo, List<Metric>>>
      groupByResourceAndLibrary(Collection<MetricData> metricDataList) {
    Map<Resource, Map<InstrumentationLibraryInfo, List<Metric>>> result = new HashMap<>();
    for (MetricData metricData : metricDataList) {
      if (metricData.isEmpty()) {
        // If no points available then ignore.
        continue;
      }

      Resource resource = metricData.getResource();
      Map<InstrumentationLibraryInfo, List<Metric>> libraryInfoListMap =
          result.get(metricData.getResource());
      if (libraryInfoListMap == null) {
        libraryInfoListMap = new HashMap<>();
        result.put(resource, libraryInfoListMap);
      }
      List<Metric> metricList =
          libraryInfoListMap.computeIfAbsent(
              metricData.getInstrumentationLibraryInfo(), k -> new ArrayList<>());
      metricList.add(toProtoMetric(metricData));
    }
    return result;
  }

  // fall through comment isn't working for some reason.
  // Aditionally, we have deprecated portions of the metrics data model we're supporting.
  @SuppressWarnings(value = {"fallthrough", "deprecation"})
  static Metric toProtoMetric(MetricData metricData) {
    Metric.Builder builder =
        Metric.newBuilder()
            .setName(metricData.getName())
            .setDescription(metricData.getDescription())
            .setUnit(metricData.getUnit());

    switch (metricData.getType()) {
      case LONG_SUM:
        LongSumData longSumData = metricData.getLongSumData();
        builder.setSum(
            Sum.newBuilder()
                .setIsMonotonic(longSumData.isMonotonic())
                .setAggregationTemporality(
                    mapToTemporality(longSumData.getAggregationTemporality()))
                .addAllDataPoints(toIntDataPoints(longSumData.getPoints()))
                .build());
        break;
      case DOUBLE_SUM:
        DoubleSumData doubleSumData = metricData.getDoubleSumData();
        builder.setSum(
            Sum.newBuilder()
                .setIsMonotonic(doubleSumData.isMonotonic())
                .setAggregationTemporality(
                    mapToTemporality(doubleSumData.getAggregationTemporality()))
                .addAllDataPoints(toDoubleDataPoints(doubleSumData.getPoints()))
                .build());
        break;
      case SUMMARY:
        // WE must use full type because importing triggers the "no deprecated usage" warning,
        // ironically not letting us depreecate our own API.
        io.opentelemetry.sdk.metrics.data.DoubleSummaryData doubleSummaryData =
            metricData.getDoubleSummaryData();
        builder.setSummary(
            Summary.newBuilder()
                .addAllDataPoints(toSummaryDataPoints(doubleSummaryData.getPoints()))
                .build());
        break;
      case LONG_GAUGE:
        LongGaugeData longGaugeData = metricData.getLongGaugeData();
        builder.setGauge(
            Gauge.newBuilder()
                .addAllDataPoints(toIntDataPoints(longGaugeData.getPoints()))
                .build());
        break;
      case DOUBLE_GAUGE:
        DoubleGaugeData doubleGaugeData = metricData.getDoubleGaugeData();
        builder.setGauge(
            Gauge.newBuilder()
                .addAllDataPoints(toDoubleDataPoints(doubleGaugeData.getPoints()))
                .build());
        break;
      case HISTOGRAM:
        DoubleHistogramData doubleHistogramData = metricData.getDoubleHistogramData();
        builder.setHistogram(
            Histogram.newBuilder()
                .setAggregationTemporality(
                    mapToTemporality(doubleHistogramData.getAggregationTemporality()))
                .addAllDataPoints(toHistogramDataPoints(doubleHistogramData.getPoints()))
                .build());
        break;
    }
    return builder.build();
  }

  private static AggregationTemporality mapToTemporality(
      io.opentelemetry.sdk.metrics.data.AggregationTemporality temporality) {
    switch (temporality) {
      case CUMULATIVE:
        return AGGREGATION_TEMPORALITY_CUMULATIVE;
      case DELTA:
        return AGGREGATION_TEMPORALITY_DELTA;
    }
    return AGGREGATION_TEMPORALITY_UNSPECIFIED;
  }

  static List<NumberDataPoint> toIntDataPoints(Collection<LongPointData> points) {
    List<NumberDataPoint> result = new ArrayList<>(points.size());
    for (LongPointData longPoint : points) {
      NumberDataPoint.Builder builder =
          NumberDataPoint.newBuilder()
              .setStartTimeUnixNano(longPoint.getStartEpochNanos())
              .setTimeUnixNano(longPoint.getEpochNanos())
              .setAsInt(longPoint.getValue());
      longPoint
          .getAttributes()
          .forEach(
              (key, value) -> builder.addAttributes(CommonAdapter.toProtoAttribute(key, value)));
      longPoint.getExemplars().forEach(e -> builder.addExemplars(toExemplar(e)));
      result.add(builder.build());
    }
    return result;
  }

  static Collection<NumberDataPoint> toDoubleDataPoints(Collection<DoublePointData> points) {
    List<NumberDataPoint> result = new ArrayList<>(points.size());
    for (DoublePointData doublePoint : points) {
      NumberDataPoint.Builder builder =
          NumberDataPoint.newBuilder()
              .setStartTimeUnixNano(doublePoint.getStartEpochNanos())
              .setTimeUnixNano(doublePoint.getEpochNanos())
              .setAsDouble(doublePoint.getValue());
      doublePoint
          .getAttributes()
          .forEach(
              (key, value) -> builder.addAttributes(CommonAdapter.toProtoAttribute(key, value)));
      doublePoint.getExemplars().forEach(e -> builder.addExemplars(toExemplar(e)));
      result.add(builder.build());
    }
    return result;
  }

  @SuppressWarnings("deprecation")
  // Ironically, we're deprecating our own data model components and have to supress these due to
  // "treat warnigns as errors" in our build.
  static List<SummaryDataPoint> toSummaryDataPoints(
      Collection<io.opentelemetry.sdk.metrics.data.DoubleSummaryPointData> points) {
    List<SummaryDataPoint> result = new ArrayList<>(points.size());
    for (io.opentelemetry.sdk.metrics.data.DoubleSummaryPointData doubleSummaryPoint : points) {
      SummaryDataPoint.Builder builder =
          SummaryDataPoint.newBuilder()
              .setStartTimeUnixNano(doubleSummaryPoint.getStartEpochNanos())
              .setTimeUnixNano(doubleSummaryPoint.getEpochNanos())
              .setCount(doubleSummaryPoint.getCount())
              .setSum(doubleSummaryPoint.getSum());
      doubleSummaryPoint
          .getAttributes()
          .forEach(
              (key, value) -> builder.addAttributes(CommonAdapter.toProtoAttribute(key, value)));
      // Not calling directly addAllQuantileValues because that generates couple of unnecessary
      // allocations if empty list.
      if (!doubleSummaryPoint.getPercentileValues().isEmpty()) {
        for (io.opentelemetry.sdk.metrics.data.ValueAtPercentile valueAtPercentile :
            doubleSummaryPoint.getPercentileValues()) {
          builder.addQuantileValues(
              SummaryDataPoint.ValueAtQuantile.newBuilder()
                  .setQuantile(valueAtPercentile.getPercentile() / 100.0)
                  .setValue(valueAtPercentile.getValue())
                  .build());
        }
      }
      result.add(builder.build());
    }
    return result;
  }

  static Collection<HistogramDataPoint> toHistogramDataPoints(
      Collection<DoubleHistogramPointData> points) {
    List<HistogramDataPoint> result = new ArrayList<>(points.size());
    for (DoubleHistogramPointData doubleHistogramPoint : points) {
      HistogramDataPoint.Builder builder =
          HistogramDataPoint.newBuilder()
              .setStartTimeUnixNano(doubleHistogramPoint.getStartEpochNanos())
              .setTimeUnixNano(doubleHistogramPoint.getEpochNanos())
              .setCount(doubleHistogramPoint.getCount())
              .setSum(doubleHistogramPoint.getSum())
              .addAllBucketCounts(doubleHistogramPoint.getCounts());
      List<Double> boundaries = doubleHistogramPoint.getBoundaries();
      if (!boundaries.isEmpty()) {
        builder.addAllExplicitBounds(boundaries);
      }
      doubleHistogramPoint
          .getAttributes()
          .forEach(
              (key, value) -> builder.addAttributes(CommonAdapter.toProtoAttribute(key, value)));
      doubleHistogramPoint.getExemplars().forEach(e -> builder.addExemplars(toExemplar(e)));
      result.add(builder.build());
    }
    return result;
  }

  static Exemplar toExemplar(io.opentelemetry.sdk.metrics.data.Exemplar exemplar) {
    // TODO - Use a thread local cache for spanid/traceid -> byte conversion.
    Exemplar.Builder builder = Exemplar.newBuilder();
    builder.setTimeUnixNano(exemplar.getEpochNanos());
    if (exemplar.getSpanId() != null) {
      builder.setSpanId(convertSpanId(exemplar.getSpanId()));
    }
    if (exemplar.getTraceId() != null) {
      builder.setTraceId(convertTraceId(exemplar.getTraceId()));
    }
    exemplar
        .getFilteredAttributes()
        .forEach(
            (key, value) ->
                builder.addFilteredAttributes(CommonAdapter.toProtoAttribute(key, value)));
    if (exemplar instanceof LongExemplar) {
      builder.setAsInt(((LongExemplar) exemplar).getValue());
    } else if (exemplar instanceof DoubleExemplar) {
      builder.setAsDouble(((DoubleExemplar) exemplar).getValue());
    } else {
      if (logger.isLoggable(Level.SEVERE)) {
        logger.log(Level.SEVERE, "Unable to convert unknown exemplar type: " + exemplar);
      }
    }
    return builder.build();
  }

  private static ByteString convertTraceId(String id) {
    return UnsafeByteOperations.unsafeWrap(
        OtelEncodingUtils.bytesFromBase16(id, TraceId.getLength()));
  }

  private static ByteString convertSpanId(String id) {
    return UnsafeByteOperations.unsafeWrap(
        OtelEncodingUtils.bytesFromBase16(id, SpanId.getLength()));
  }

  private MetricAdapter() {}
}
