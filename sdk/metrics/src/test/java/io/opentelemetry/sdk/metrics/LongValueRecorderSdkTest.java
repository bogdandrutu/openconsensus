/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.api.metrics.LongValueRecorder;
import io.opentelemetry.api.metrics.LongValueRecorder.BoundLongValueRecorder;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.metrics.StressTestRunner.OperationUpdater;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricData.DoubleSummaryPoint;
import io.opentelemetry.sdk.metrics.data.MetricData.ValueAtPercentile;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LongValueRecorderSdk}. */
class LongValueRecorderSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(
          "io.opentelemetry.sdk.metrics.LongValueRecorderSdkTest", null);
  private final TestClock testClock = TestClock.create();
  private final MeterProviderSharedState meterProviderSharedState =
      MeterProviderSharedState.create(testClock, RESOURCE);
  private final SdkMeter testSdk =
      new SdkMeter(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO);

  @Test
  void record_PreventNullLabels() {
    assertThatThrownBy(
            () -> testSdk.longValueRecorderBuilder("testRecorder").build().record(1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("labels");
  }

  @Test
  void bound_PreventNullLabels() {
    assertThatThrownBy(() -> testSdk.longValueRecorderBuilder("testRecorder").build().bind(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("labels");
  }

  @Test
  void collectMetrics_NoRecords() {
    LongValueRecorderSdk longMeasure =
        testSdk
            .longValueRecorderBuilder("testRecorder")
            .setDescription("My own counter")
            .setUnit("ms")
            .build();

    longMeasure.bind(Labels.of("key", "value"));
    testClock.advanceNanos(SECOND_NANOS);

    List<MetricData> metricDataList = TestUtils.collectAll(longMeasure);
    assertThat(metricDataList).isEmpty();
  }

  @Test
  void collectMetrics_WithOneRecord() {
    LongValueRecorderSdk longMeasure =
        testSdk
            .longValueRecorderBuilder("testRecorder")
            .setDescription("My own counter")
            .setUnit("ms")
            .build();
    testClock.advanceNanos(SECOND_NANOS);
    longMeasure.record(12, Labels.empty());
    List<MetricData> metricDataList = TestUtils.collectAll(longMeasure);
    assertThat(metricDataList)
        .containsExactly(
            MetricData.createDoubleSummary(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testRecorder",
                "My own counter",
                "ms",
                MetricData.DoubleSummaryData.create(
                    Collections.singletonList(
                        DoubleSummaryPoint.create(
                            testClock.now() - SECOND_NANOS,
                            testClock.now(),
                            Labels.empty(),
                            1,
                            12,
                            valueAtPercentiles(12, 12))))));
  }

  @Test
  void collectMetrics_WithEmptyLabel() {
    LongValueRecorderSdk longMeasure = testSdk.longValueRecorderBuilder("testRecorder").build();
    LongValueRecorderSdk longMeasure1 = testSdk.longValueRecorderBuilder("testRecorder1").build();
    testClock.advanceNanos(SECOND_NANOS);
    longMeasure.record(12, Labels.empty());
    longMeasure1.record(12);

    assertThat(TestUtils.collectAll(longMeasure).get(0))
        .usingRecursiveComparison(
            RecursiveComparisonConfiguration.builder().withIgnoredFields("name").build())
        .isEqualTo(TestUtils.collectAll(longMeasure1).get(0));
  }

  @Test
  void collectMetrics_WithMultipleCollects() {
    long startTime = testClock.now();
    LongValueRecorderSdk longMeasure = testSdk.longValueRecorderBuilder("testRecorder").build();
    BoundLongValueRecorder boundMeasure = longMeasure.bind(Labels.of("K", "V"));
    try {
      // Do some records using bounds and direct calls and bindings.
      longMeasure.record(12, Labels.empty());
      boundMeasure.record(123);
      longMeasure.record(-14, Labels.empty());
      // Advancing time here should not matter.
      testClock.advanceNanos(SECOND_NANOS);
      boundMeasure.record(321);
      longMeasure.record(-121, Labels.of("K", "V"));

      long firstCollect = testClock.now();
      List<MetricData> metricDataList = TestUtils.collectAll(longMeasure);
      assertThat(metricDataList).hasSize(1);
      MetricData metricData = metricDataList.get(0);
      assertThat(metricData.getDoubleSummaryData().getPoints())
          .containsExactlyInAnyOrder(
              DoubleSummaryPoint.create(
                  startTime, firstCollect, Labels.empty(), 2, -2, valueAtPercentiles(-14, 12)),
              MetricData.DoubleSummaryPoint.create(
                  startTime,
                  firstCollect,
                  Labels.of("K", "V"),
                  3,
                  323,
                  valueAtPercentiles(-121, 321)));

      // Repeat to prove we don't keep previous values.
      testClock.advanceNanos(SECOND_NANOS);
      boundMeasure.record(222);
      longMeasure.record(17, Labels.empty());

      long secondCollect = testClock.now();
      metricDataList = TestUtils.collectAll(longMeasure);
      assertThat(metricDataList).hasSize(1);
      metricData = metricDataList.get(0);
      assertThat(metricData.getDoubleSummaryData().getPoints())
          .containsExactlyInAnyOrder(
              DoubleSummaryPoint.create(
                  startTime + SECOND_NANOS,
                  secondCollect,
                  Labels.empty(),
                  1,
                  17,
                  valueAtPercentiles(17, 17)),
              DoubleSummaryPoint.create(
                  startTime + SECOND_NANOS,
                  secondCollect,
                  Labels.of("K", "V"),
                  1,
                  222,
                  valueAtPercentiles(222, 222)));
    } finally {
      boundMeasure.unbind();
    }
  }

  @Test
  void stressTest() {
    final LongValueRecorderSdk longMeasure =
        testSdk.longValueRecorderBuilder("testRecorder").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setInstrument(longMeasure).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new LongValueRecorderSdkTest.OperationUpdaterDirectCall(longMeasure, "K", "V")));
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new LongValueRecorderSdkTest.OperationUpdaterWithBinding(
                  longMeasure.bind(Labels.of("K", "V")))));
    }

    stressTestBuilder.build().run();
    List<MetricData> metricDataList = TestUtils.collectAll(longMeasure);
    assertThat(metricDataList).hasSize(1);
    assertThat(metricDataList.get(0).getDoubleSummaryData().getPoints())
        .containsExactly(
            DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of("K", "V"),
                16_000,
                160_000,
                valueAtPercentiles(9, 11)));
  }

  @Test
  void stressTest_WithDifferentLabelSet() {
    final String[] keys = {"Key_1", "Key_2", "Key_3", "Key_4"};
    final String[] values = {"Value_1", "Value_2", "Value_3", "Value_4"};
    final LongValueRecorderSdk longMeasure =
        testSdk.longValueRecorderBuilder("testRecorder").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setInstrument(longMeasure).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new LongValueRecorderSdkTest.OperationUpdaterDirectCall(
                  longMeasure, keys[i], values[i])));

      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new LongValueRecorderSdkTest.OperationUpdaterWithBinding(
                  longMeasure.bind(Labels.of(keys[i], values[i])))));
    }

    stressTestBuilder.build().run();
    List<MetricData> metricDataList = TestUtils.collectAll(longMeasure);
    assertThat(metricDataList).hasSize(1);
    assertThat(metricDataList.get(0).getDoubleSummaryData().getPoints())
        .containsExactly(
            DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[0], values[0]),
                2_000,
                20_000,
                valueAtPercentiles(9, 11)),
            MetricData.DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[1], values[1]),
                2_000,
                20_000,
                valueAtPercentiles(9, 11)),
            DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[2], values[2]),
                2_000,
                20_000,
                valueAtPercentiles(9, 11)),
            DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[3], values[3]),
                2_000,
                20_000,
                valueAtPercentiles(9, 11)));
  }

  private static class OperationUpdaterWithBinding extends OperationUpdater {
    private final BoundLongValueRecorder boundLongValueRecorder;

    private OperationUpdaterWithBinding(BoundLongValueRecorder boundLongValueRecorder) {
      this.boundLongValueRecorder = boundLongValueRecorder;
    }

    @Override
    void update() {
      boundLongValueRecorder.record(9);
    }

    @Override
    void cleanup() {
      boundLongValueRecorder.unbind();
    }
  }

  private static class OperationUpdaterDirectCall extends OperationUpdater {

    private final LongValueRecorder longValueRecorder;
    private final String key;
    private final String value;

    private OperationUpdaterDirectCall(
        LongValueRecorder longValueRecorder, String key, String value) {
      this.longValueRecorder = longValueRecorder;
      this.key = key;
      this.value = value;
    }

    @Override
    void update() {
      longValueRecorder.record(11, Labels.of(key, value));
    }

    @Override
    void cleanup() {}
  }

  private static List<ValueAtPercentile> valueAtPercentiles(double min, double max) {
    return Arrays.asList(ValueAtPercentile.create(0, min), ValueAtPercentile.create(100, max));
  }
}
