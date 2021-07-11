/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundLongHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.StressTestRunner.OperationUpdater;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramData;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.time.TestClock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DoubleHistogramSdk}. */
public class LongHistogramSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final AttributeKey<String> FOO_KEY = stringKey("foo");
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(LongHistogramSdkTest.class.getName(), null);
  private final TestClock testClock = TestClock.create();
  private final SdkMeterProvider sdkMeterProvider =
      SdkMeterProvider.builder()
          .setClock(testClock)
          .setResource(RESOURCE)
          .setMeasurementProcessor(
              DefaultMeasurementProcessor.builder()
                  // Force consistent histogram defaults for testing.
                  .setDefaultHistogramBoundaries(new double[] {0, 100, 1000, 10000})
                  .build())
          .build();
  private final Meter sdkMeter = sdkMeterProvider.meterBuilder(getClass().getName()).build();
  private final MetricProducer collector = sdkMeterProvider.newMetricProducer();

  // TODO: Figure out how to set these on the instrument!
  // private static final double[] DEFAULT_HISTOGRAM_BOUNDARIES = {0, 100, 1000, 10000};

  @Test
  void record_PreventNullAttributes() {
    assertThatThrownBy(
            () -> sdkMeter.histogramBuilder("histogram").ofLongs().build().record(1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void bound_PreventNullAttributes() {
    assertThatThrownBy(() -> sdkMeter.histogramBuilder("histogram").ofLongs().build().bind(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void collectMetrics_NoRecords() {
    LongHistogram histogram = sdkMeter.histogramBuilder("histogram").ofLongs().build();
    BoundLongHistogram bound = histogram.bind(Attributes.of(FOO_KEY, "bar"));
    try {
      assertThat(collector.collectAllMetrics()).isEmpty();
    } finally {
      bound.unbind();
    }
  }

  @Test
  void collectMetrics_WithEmptyLabel() {
    LongHistogram histogram =
        sdkMeter
            .histogramBuilder("histogram")
            .ofLongs()
            .setDescription("description")
            .setUnit("ms")
            .build();

    testClock.advance(Duration.ofNanos(SECOND_NANOS));
    histogram.record(12, Attributes.empty());
    histogram.record(12);
    histogram.record(112);
    // Note: histograms have lots of arrays/lists so we need to deep-inspect things.
    Collection<MetricData> metrics = collector.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    MetricData metric = metrics.iterator().next();
    assertThat(metric.getResource()).isEqualTo(RESOURCE);
    assertThat(metric.getInstrumentationLibraryInfo()).isEqualTo(INSTRUMENTATION_LIBRARY_INFO);
    assertThat(metric.getName()).isEqualTo("histogram");
    assertThat(metric.getDescription()).isEqualTo("description");
    assertThat(metric.getUnit()).isEqualTo("ms");
    DoubleHistogramData histogramData = metric.getDoubleHistogramData();
    assertThat(histogramData.getAggregationTemporality())
        .isEqualTo(AggregationTemporality.CUMULATIVE);
    Collection<DoubleHistogramPointData> points = histogramData.getPoints();
    assertThat(points).hasSize(1);
    DoubleHistogramPointData histogramPoint = points.iterator().next();
    assertThat(histogramPoint.getStartEpochNanos()).isEqualTo(testClock.now() - SECOND_NANOS);
    assertThat(histogramPoint.getEpochNanos()).isEqualTo(testClock.now());
    assertThat(histogramPoint.getAttributes()).isEqualTo(Attributes.empty());
    assertThat(histogramPoint.getSum()).isEqualTo(136d);
    assertThat(histogramPoint.getCount()).isEqualTo(3);
    assertThat(histogramPoint.getBoundaries()).containsExactly(0d, 100d, 1000d, 10000d);
    // buckets: [-Inf, 0], [0, 100], [100, 1000], [1000, 10000], [10000, Inf]
    assertThat(histogramPoint.getCounts()).containsExactly(0L, 2L, 1L, 0L, 0L);
  }

  @Test
  void collectMetrics_WithMultipleCollects() {
    long startTime = testClock.now();
    LongHistogram histogram = sdkMeter.histogramBuilder("histogram").ofLongs().build();
    BoundLongHistogram bound = histogram.bind(Attributes.of(FOO_KEY, "V"));
    histogram.record(12, Attributes.empty());
    bound.record(123);
    histogram.record(21, Attributes.empty());
    // Advancing time here should not matter.
    testClock.advance(Duration.ofNanos(SECOND_NANOS));
    bound.record(321);
    histogram.record(111, Attributes.of(FOO_KEY, "V"));

    // Note: histograms have lots of arrays/lists so we need to deep-inspect things.
    Collection<MetricData> metrics = collector.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    MetricData metric = metrics.iterator().next();
    assertThat(metric.getName()).isEqualTo("histogram");
    DoubleHistogramData histogramData = metric.getDoubleHistogramData();
    assertThat(histogramData.getAggregationTemporality())
        .isEqualTo(AggregationTemporality.CUMULATIVE);
    Collection<DoubleHistogramPointData> points = histogramData.getPoints();
    assertThat(points).hasSize(2);
    // TODO - check points.
    for (DoubleHistogramPointData point : points) {
      assertThat(point.getStartEpochNanos()).isEqualTo(startTime);
      assertThat(point.getEpochNanos()).isEqualTo(testClock.now());
      assertThat(point.getBoundaries()).containsExactly(0d, 100d, 1000d, 10000d);
      if (point.getAttributes().equals(Attributes.empty())) {
        assertThat(point.getSum()).isEqualTo(33d);
        assertThat(point.getCount()).isEqualTo(2);
        assertThat(point.getCounts()).containsExactly(0L, 2L, 0L, 0L, 0L);
      } else if (point.getAttributes().equals(Attributes.of(FOO_KEY, "V"))) {
        assertThat(point.getSum()).isEqualTo(555d);
        assertThat(point.getCount()).isEqualTo(3);
        assertThat(point.getCounts()).containsExactly(0L, 0L, 3L, 0L, 0L);
      } else {
        throw new RuntimeException("Unexpected histogram point: " + point);
      }
    }
    // Repeat to prove we keep previous values.
    testClock.advance(Duration.ofNanos(SECOND_NANOS));
    bound.record(222);
    histogram.record(11, Attributes.empty());

    metrics = collector.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    metric = metrics.iterator().next();
    assertThat(metric.getName()).isEqualTo("histogram");
    histogramData = metric.getDoubleHistogramData();
    assertThat(histogramData.getAggregationTemporality())
        .isEqualTo(AggregationTemporality.CUMULATIVE);
    points = histogramData.getPoints();
    assertThat(points).hasSize(2);
    for (DoubleHistogramPointData point : points) {
      assertThat(point.getStartEpochNanos()).isEqualTo(startTime);
      assertThat(point.getEpochNanos()).isEqualTo(testClock.now());
      assertThat(point.getBoundaries()).containsExactly(0d, 100d, 1000d, 10000d);
      if (point.getAttributes().equals(Attributes.empty())) {
        assertThat(point.getSum()).isEqualTo(44d);
        assertThat(point.getCount()).isEqualTo(3);
        assertThat(point.getCounts()).containsExactly(0L, 3L, 0L, 0L, 0L);
      } else if (point.getAttributes().equals(Attributes.of(FOO_KEY, "V"))) {
        assertThat(point.getSum()).isEqualTo(777d);
        assertThat(point.getCount()).isEqualTo(4);
        assertThat(point.getCounts()).containsExactly(0L, 0L, 4L, 0L, 0L);
      } else {
        throw new RuntimeException("Unexpected histogram point: " + point);
      }
    }
  }

  @Test
  void stressTest() {
    LongHistogram histogram = sdkMeter.histogramBuilder("histogram").ofLongs().build();
    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setMeter((SdkMeter) sdkMeter).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000, 2, new OperationUpdaterDirectCall(histogram, FOO_KEY, "V")));
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new OperationUpdaterWithBinding(histogram.bind(Attributes.of(FOO_KEY, "V")))));
    }
    stressTestBuilder.build().run();
    Collection<MetricData> metrics = collector.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    MetricData metric = metrics.iterator().next();
    assertThat(metric.getName()).isEqualTo("histogram");
    DoubleHistogramData histogramData = metric.getDoubleHistogramData();
    assertThat(histogramData.getAggregationTemporality())
        .isEqualTo(AggregationTemporality.CUMULATIVE);
    Collection<DoubleHistogramPointData> points = histogramData.getPoints();
    assertThat(points).hasSize(1);
    DoubleHistogramPointData point = points.iterator().next();
    assertThat(point.getSum()).isEqualTo(800_000d);
    assertThat(point.getCount()).isEqualTo(8_000);
    assertThat(point.getCounts()).containsExactly(0L, 4_000L, 4_000L, 0L, 0L);
  }

  @Test
  void stressTest_WithDifferentAttributeset() {
    final List<AttributeKey<String>> keys =
        Stream.of(stringKey("Key_1"), stringKey("Key_2"), stringKey("Key_3"), stringKey("Key_4"))
            .collect(Collectors.toList());
    final String[] values = {"Value_1", "Value_2", "Value_3", "Value_4"};
    LongHistogram histogram = sdkMeter.histogramBuilder("histogram").ofLongs().build();
    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setMeter((SdkMeter) sdkMeter).setCollectionIntervalMs(100);
    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000, 1, new OperationUpdaterDirectCall(histogram, keys.get(i), values[i])));

      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new OperationUpdaterWithBinding(
                  histogram.bind(Attributes.of(keys.get(i), values[i])))));
    }
    stressTestBuilder.build().run();
    Collection<MetricData> metrics = collector.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    MetricData metric = metrics.iterator().next();
    assertThat(metric.getName()).isEqualTo("histogram");
    DoubleHistogramData histogramData = metric.getDoubleHistogramData();
    assertThat(histogramData.getAggregationTemporality())
        .isEqualTo(AggregationTemporality.CUMULATIVE);
    Collection<DoubleHistogramPointData> points = histogramData.getPoints();
    assertThat(points).hasSize(4);
    for (DoubleHistogramPointData point : points) {
      assertThat(point.getStartEpochNanos()).isEqualTo(testClock.now());
      assertThat(point.getEpochNanos()).isEqualTo(testClock.now());
      assertThat(point.getBoundaries()).containsExactly(0d, 100d, 1000d, 10000d);
      assertThat(point.getSum()).isEqualTo(400_000);
      assertThat(point.getCount()).isEqualTo(4_000);
      assertThat(point.getCounts()).containsExactly(0L, 2_000L, 2_000L, 0L, 0L);
      // TODO: verify each attribute key exists
    }
  }

  private static class OperationUpdaterWithBinding extends OperationUpdater {
    private final BoundLongHistogram bound;

    private OperationUpdaterWithBinding(BoundLongHistogram bound) {
      this.bound = bound;
    }

    @Override
    void update() {
      bound.record(90);
    }

    @Override
    void cleanup() {
      bound.unbind();
    }
  }

  private static class OperationUpdaterDirectCall extends OperationUpdater {

    private final LongHistogram histogram;
    private final AttributeKey<String> key;
    private final String value;

    private OperationUpdaterDirectCall(
        LongHistogram histogram, AttributeKey<String> key, String value) {
      this.histogram = histogram;
      this.key = key;
      this.value = value;
    }

    @Override
    void update() {
      histogram.record(110, Attributes.of(key, value));
    }

    @Override
    void cleanup() {}
  }
}
