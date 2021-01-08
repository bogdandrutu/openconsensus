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
import io.opentelemetry.api.metrics.DoubleValueRecorder;
import io.opentelemetry.api.metrics.DoubleValueRecorder.BoundDoubleValueRecorder;
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

/** Unit tests for {@link DoubleValueRecorderSdk}. */
class DoubleValueRecorderSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(
          "io.opentelemetry.sdk.metrics.DoubleValueRecorderSdkTest", null);
  private final TestClock testClock = TestClock.create();
  private final MeterProviderSharedState meterProviderSharedState =
      MeterProviderSharedState.create(testClock, RESOURCE);
  private final SdkMeter testSdk =
      new SdkMeter(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO);

  @Test
  void record_PreventNullLabels() {
    assertThatThrownBy(
            () -> testSdk.doubleValueRecorderBuilder("testRecorder").build().record(1.0, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("labels");
  }

  @Test
  void bound_PreventNullLabels() {
    assertThatThrownBy(() -> testSdk.doubleValueRecorderBuilder("testRecorder").build().bind(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("labels");
  }

  @Test
  void collectMetrics_NoRecords() {
    DoubleValueRecorderSdk doubleMeasure =
        testSdk
            .doubleValueRecorderBuilder("testRecorder")
            .setDescription("My very own measure")
            .setUnit("ms")
            .build();
    doubleMeasure.bind(Labels.of("key", "value"));
    testClock.advanceNanos(SECOND_NANOS);

    List<MetricData> metricDataList = TestUtils.collectAll(doubleMeasure);
    assertThat(metricDataList).isEmpty();
  }

  @Test
  void collectMetrics_WithOneRecord() {
    DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();
    testClock.advanceNanos(SECOND_NANOS);
    doubleMeasure.record(12.1d, Labels.empty());
    List<MetricData> metricDataList = TestUtils.collectAll(doubleMeasure);
    assertThat(metricDataList)
        .containsExactly(
            MetricData.createDoubleSummary(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testRecorder",
                "",
                "1",
                MetricData.DoubleSummaryData.create(
                    Collections.singletonList(
                        DoubleSummaryPoint.create(
                            testClock.now() - SECOND_NANOS,
                            testClock.now(),
                            Labels.empty(),
                            1,
                            12.1d,
                            valueAtPercentiles(12.1d, 12.1d))))));
  }

  @Test
  void collectMetrics_WithEmptyLabel() {
    DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();
    DoubleValueRecorderSdk doubleMeasure1 =
        testSdk.doubleValueRecorderBuilder("testRecorder1").build();
    testClock.advanceNanos(SECOND_NANOS);
    doubleMeasure.record(12.1d, Labels.empty());
    doubleMeasure1.record(12.1d);

    assertThat(TestUtils.collectAll(doubleMeasure).get(0))
        .usingRecursiveComparison(
            RecursiveComparisonConfiguration.builder().withIgnoredFields("name").build())
        .isEqualTo(TestUtils.collectAll(doubleMeasure1).get(0));
  }

  @Test
  void collectMetrics_WithMultipleCollects() {
    long startTime = testClock.now();
    DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();
    BoundDoubleValueRecorder boundMeasure = doubleMeasure.bind(Labels.of("K", "V"));
    try {
      // Do some records using bounds and direct calls and bindings.
      doubleMeasure.record(12.1d, Labels.empty());
      boundMeasure.record(123.3d);
      doubleMeasure.record(-13.1d, Labels.empty());
      // Advancing time here should not matter.
      testClock.advanceNanos(SECOND_NANOS);
      boundMeasure.record(321.5d);
      doubleMeasure.record(-121.5d, Labels.of("K", "V"));

      long firstCollect = testClock.now();
      List<MetricData> metricDataList = TestUtils.collectAll(doubleMeasure);
      assertThat(metricDataList).hasSize(1);
      MetricData metricData = metricDataList.get(0);
      assertThat(metricData.getDoubleSummaryData().getPoints())
          .containsExactlyInAnyOrder(
              MetricData.DoubleSummaryPoint.create(
                  startTime,
                  firstCollect,
                  Labels.empty(),
                  2,
                  -1.0d,
                  valueAtPercentiles(-13.1d, 12.1d)),
              MetricData.DoubleSummaryPoint.create(
                  startTime,
                  firstCollect,
                  Labels.of("K", "V"),
                  3,
                  323.3d,
                  valueAtPercentiles(-121.5d, 321.5d)));

      // Repeat to prove we don't keep previous values.
      testClock.advanceNanos(SECOND_NANOS);
      boundMeasure.record(222d);
      doubleMeasure.record(17d, Labels.empty());

      long secondCollect = testClock.now();
      metricDataList = TestUtils.collectAll(doubleMeasure);
      assertThat(metricDataList).hasSize(1);
      metricData = metricDataList.get(0);
      assertThat(metricData.getDoubleSummaryData().getPoints())
          .containsExactlyInAnyOrder(
              DoubleSummaryPoint.create(
                  startTime + SECOND_NANOS,
                  secondCollect,
                  Labels.empty(),
                  1,
                  17.0d,
                  valueAtPercentiles(17d, 17d)),
              MetricData.DoubleSummaryPoint.create(
                  startTime + SECOND_NANOS,
                  secondCollect,
                  Labels.of("K", "V"),
                  1,
                  222.0d,
                  valueAtPercentiles(222.0, 222.0d)));
    } finally {
      boundMeasure.unbind();
    }
  }

  @Test
  void stressTest() {
    final DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setInstrument(doubleMeasure).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new DoubleValueRecorderSdkTest.OperationUpdaterDirectCall(doubleMeasure, "K", "V")));
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000, 2, new OperationUpdaterWithBinding(doubleMeasure.bind(Labels.of("K", "V")))));
    }

    stressTestBuilder.build().run();
    List<MetricData> metricDataList = TestUtils.collectAll(doubleMeasure);
    assertThat(metricDataList).hasSize(1);
    assertThat(metricDataList.get(0).getDoubleSummaryData().getPoints())
        .containsExactly(
            MetricData.DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of("K", "V"),
                8_000,
                80_000,
                valueAtPercentiles(9.0, 11.0)));
  }

  @Test
  void stressTest_WithDifferentLabelSet() {
    final String[] keys = {"Key_1", "Key_2", "Key_3", "Key_4"};
    final String[] values = {"Value_1", "Value_2", "Value_3", "Value_4"};
    final DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setInstrument(doubleMeasure).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new DoubleValueRecorderSdkTest.OperationUpdaterDirectCall(
                  doubleMeasure, keys[i], values[i])));

      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new OperationUpdaterWithBinding(doubleMeasure.bind(Labels.of(keys[i], values[i])))));
    }

    stressTestBuilder.build().run();
    List<MetricData> metricDataList = TestUtils.collectAll(doubleMeasure);
    assertThat(metricDataList).hasSize(1);
    assertThat(metricDataList.get(0).getDoubleSummaryData().getPoints())
        .containsExactly(
            MetricData.DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[0], values[0]),
                4_000,
                40_000d,
                valueAtPercentiles(9.0, 11.0)),
            DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[1], values[1]),
                4_000,
                40_000d,
                valueAtPercentiles(9.0, 11.0)),
            MetricData.DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[2], values[2]),
                4_000,
                40_000d,
                valueAtPercentiles(9.0, 11.0)),
            MetricData.DoubleSummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[3], values[3]),
                4_000,
                40_000d,
                valueAtPercentiles(9.0, 11.0)));
  }

  private static class OperationUpdaterWithBinding extends OperationUpdater {
    private final BoundDoubleValueRecorder boundDoubleValueRecorder;

    private OperationUpdaterWithBinding(BoundDoubleValueRecorder boundDoubleValueRecorder) {
      this.boundDoubleValueRecorder = boundDoubleValueRecorder;
    }

    @Override
    void update() {
      boundDoubleValueRecorder.record(11.0);
    }

    @Override
    void cleanup() {
      boundDoubleValueRecorder.unbind();
    }
  }

  private static class OperationUpdaterDirectCall extends OperationUpdater {
    private final DoubleValueRecorder doubleValueRecorder;
    private final String key;
    private final String value;

    private OperationUpdaterDirectCall(
        DoubleValueRecorder doubleValueRecorder, String key, String value) {
      this.doubleValueRecorder = doubleValueRecorder;
      this.key = key;
      this.value = value;
    }

    @Override
    void update() {
      doubleValueRecorder.record(9.0, Labels.of(key, value));
    }

    @Override
    void cleanup() {}
  }

  private static List<ValueAtPercentile> valueAtPercentiles(double min, double max) {
    return Arrays.asList(ValueAtPercentile.create(0, min), ValueAtPercentile.create(100, max));
  }
}
