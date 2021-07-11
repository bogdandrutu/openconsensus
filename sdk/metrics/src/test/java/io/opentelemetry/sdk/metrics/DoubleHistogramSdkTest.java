/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.metrics.MetricAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundDoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.StressTestRunner.OperationUpdater;
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
public class DoubleHistogramSdkTest {
  // TODO - use hint api for these.
  private static final double[] DEFAULT_HISTOGRAM_BOUNDARIES = {0, 100, 1000, 10000};
  private static final AttributeKey<String> FOO_KEY = stringKey("foo");
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(DoubleHistogramSdkTest.class.getName(), null);
  private final TestClock testClock = TestClock.create();
  private final SdkMeterProvider sdkMeterProvider =
      SdkMeterProvider.builder()
          .setClock(testClock)
          .setResource(RESOURCE)
          .setMeasurementProcessor(
              DefaultMeasurementProcessor.builder()
                  .setDefaultHistogramBoundaries(DEFAULT_HISTOGRAM_BOUNDARIES)
                  .build())
          .build();
  private final Meter sdkMeter = sdkMeterProvider.meterBuilder(getClass().getName()).build();
  private final MetricProducer collector = sdkMeterProvider.newMetricProducer();

  @Test
  void record_PreventNullAttributes() {
    assertThatThrownBy(() -> sdkMeter.histogramBuilder("histogram").build().record(1.0, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void bound_PreventNullAttributes() {
    assertThatThrownBy(() -> sdkMeter.histogramBuilder("histogram").build().bind(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void collectMetrics_NoRecords() {
    DoubleHistogram histogram = sdkMeter.histogramBuilder("histogram").build();
    BoundDoubleHistogram bound = histogram.bind(Attributes.of(FOO_KEY, "bar"));
    try {
      assertThat(collector.collectAllMetrics()).isEmpty();
    } finally {
      bound.unbind();
    }
  }

  @Test
  // Note: `satisfiesExactlyInAnyOrder` needs annotation @SafeVarargs ON a final method, thanks Java
  // variance.
  @SuppressWarnings("unchecked")
  void collectMetrics_WithEmptyLabel() {
    DoubleHistogram histogram =
        sdkMeter.histogramBuilder("histogram").setDescription("description").setUnit("ms").build();

    testClock.advance(Duration.ofSeconds(1));
    histogram.record(12d, Attributes.empty());
    histogram.record(12d);
    histogram.record(112d);
    // Note: histograms have lots of arrays/lists so we need to deep-inspect things.
    Collection<MetricData> metrics = collector.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    MetricData metric = metrics.iterator().next();
    assertThat(metric)
        .hasResource(RESOURCE)
        .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
        .hasName("histogram")
        .hasDescription("description")
        .hasUnit("ms")
        .hasDoubleHistogram()
        .isCumulative()
        .points()
        .satisfiesExactly(
            point ->
                assertThat(point)
                    .hasSum(136d)
                    .hasAttributes(Attributes.empty())
                    .hasCount(3)
                    .hasBucketBoundaries(0, 100, 1000, 10000)
                    .hasBucketCounts(0, 2, 1, 0, 0));
  }

  @Test
  // Note: `satisfiesExactlyInAnyOrder` needs annotation @SafeVarargs ON a final method.
  @SuppressWarnings("unchecked")
  void collectMetrics_WithMultipleCollects() {
    long startTime = testClock.now();
    DoubleHistogram histogram = sdkMeter.histogramBuilder("histogram").build();
    BoundDoubleHistogram bound = histogram.bind(Attributes.of(FOO_KEY, "V"));
    histogram.record(12.1d, Attributes.empty());
    bound.record(123.3d);
    histogram.record(21.4d, Attributes.empty());
    // Advancing time here should not matter.
    testClock.advance(Duration.ofSeconds(1));
    bound.record(321.5d);
    histogram.record(111.1d, Attributes.of(FOO_KEY, "V"));

    // Note: histograms have lots of arrays/lists so we need to deep-inspect things.
    Collection<MetricData> metrics = collector.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    MetricData metric = metrics.iterator().next();
    assertThat(metric)
        .hasDoubleHistogram()
        .isCumulative()
        .points()
        .allSatisfy(
            point ->
                assertThat(point)
                    .hasStartEpochNanos(startTime)
                    .hasEpochNanos(testClock.now())
                    .hasBucketBoundaries(0, 100, 1000, 10000))
        .satisfiesExactlyInAnyOrder(
            point ->
                assertThat(point)
                    .hasSum(33.5)
                    .hasCount(2)
                    .hasBucketCounts(0, 2, 0, 0, 0)
                    .hasAttributes(Attributes.empty()),
            point ->
                assertThat(point)
                    .hasSum(555.9)
                    .hasCount(3)
                    .hasBucketCounts(0, 0, 3, 0, 0)
                    .hasAttributes(Attributes.of(FOO_KEY, "V")));
    // Repeat to prove we keep previous values.
    testClock.advance(Duration.ofSeconds(1));
    bound.record(222d);
    histogram.record(11d, Attributes.empty());

    metrics = collector.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    metric = metrics.iterator().next();
    assertThat(metric)
        .hasName("histogram")
        .hasDoubleHistogram()
        .isCumulative()
        .points()
        .allSatisfy(
            point ->
                assertThat(point)
                    .hasStartEpochNanos(startTime)
                    .hasEpochNanos(testClock.now())
                    .hasBucketBoundaries(0, 100, 1000, 10000))
        .satisfiesExactlyInAnyOrder(
            point ->
                assertThat(point)
                    .hasSum(44.5)
                    .hasCount(3)
                    .hasBucketCounts(0, 3, 0, 0, 0)
                    .hasAttributes(Attributes.empty()),
            point ->
                assertThat(point)
                    .hasSum(777.9)
                    .hasCount(4)
                    .hasBucketCounts(0, 0, 4, 0, 0)
                    .hasAttributes(Attributes.of(FOO_KEY, "V")));
  }

  @Test
  // Note: `satisfiesExactlyInAnyOrder` needs annotation @SafeVarargs ON a final method.
  @SuppressWarnings("unchecked")
  void stressTest() {
    DoubleHistogram histogram = sdkMeter.histogramBuilder("histogram").build();
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
    assertThat(metric)
        .hasName("histogram")
        .hasDoubleHistogram()
        .isCumulative()
        .points()
        .satisfiesExactly(
            point ->
                assertThat(point)
                    .hasSum(800_000)
                    .hasCount(8_000)
                    .hasBucketCounts(0, 4_000, 4_000, 0, 0));
  }

  @Test
  void stressTest_WithDifferentAttributeset() {
    final List<AttributeKey<String>> keys =
        Stream.of(stringKey("Key_1"), stringKey("Key_2"), stringKey("Key_3"), stringKey("Key_4"))
            .collect(Collectors.toList());
    final String[] values = {"Value_1", "Value_2", "Value_3", "Value_4"};
    DoubleHistogram histogram = sdkMeter.histogramBuilder("histogram").build();
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
    assertThat(metric)
        .hasName("histogram")
        .hasDoubleHistogram()
        .isCumulative()
        .points()
        .allSatisfy(
            point ->
                assertThat(point)
                    .hasStartEpochNanos(testClock.now())
                    .hasEpochNanos(testClock.now())
                    .hasBucketBoundaries(0, 100, 1000, 10000)
                    .hasSum(400_000)
                    .hasCount(4_000)
                    .hasBucketCounts(0, 2_000, 2_000, 0, 0))
        .extracting(point -> point.getAttributes())
        .containsExactlyInAnyOrder(
            Attributes.of(keys.get(0), values[0]),
            Attributes.of(keys.get(1), values[1]),
            Attributes.of(keys.get(2), values[2]),
            Attributes.of(keys.get(3), values[3]));
  }

  private static class OperationUpdaterWithBinding extends OperationUpdater {
    private final BoundDoubleHistogram bound;

    private OperationUpdaterWithBinding(BoundDoubleHistogram bound) {
      this.bound = bound;
    }

    @Override
    void update() {
      bound.record(90.0);
    }

    @Override
    void cleanup() {
      bound.unbind();
    }
  }

  private static class OperationUpdaterDirectCall extends OperationUpdater {

    private final DoubleHistogram histogram;
    private final AttributeKey<String> key;
    private final String value;

    private OperationUpdaterDirectCall(
        DoubleHistogram histogram, AttributeKey<String> key, String value) {
      this.histogram = histogram;
      this.key = key;
      this.value = value;
    }

    @Override
    void update() {
      histogram.record(110.0, Attributes.of(key, value));
    }

    @Override
    void cleanup() {}
  }
}
