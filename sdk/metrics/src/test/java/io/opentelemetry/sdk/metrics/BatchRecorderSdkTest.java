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
import io.opentelemetry.api.metrics.BatchRecorder;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricData.DoublePoint;
import io.opentelemetry.sdk.metrics.data.MetricData.LongPoint;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BatchRecorderSdk}. */
class BatchRecorderSdkTest {
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create("io.opentelemetry.sdk.metrics.BatchRecorderSdkTest", null);
  private final TestClock testClock = TestClock.create();
  private final MeterProviderSharedState meterProviderSharedState =
      MeterProviderSharedState.create(testClock, RESOURCE);
  private final SdkMeter testSdk =
      new SdkMeter(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO);

  @Test
  void batchRecorder_badLabelSet() {
    assertThatThrownBy(() -> testSdk.newBatchRecorder("key").record())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("key/value");
  }

  @Test
  void batchRecorder() {
    DoubleCounterSdk doubleCounter = testSdk.doubleCounterBuilder("testDoubleCounter").build();
    LongCounterSdk longCounter = testSdk.longCounterBuilder("testLongCounter").build();
    DoubleUpDownCounterSdk doubleUpDownCounter =
        testSdk.doubleUpDownCounterBuilder("testDoubleUpDownCounter").build();
    LongUpDownCounterSdk longUpDownCounter =
        testSdk.longUpDownCounterBuilder("testLongUpDownCounter").build();
    DoubleValueRecorderSdk doubleValueRecorder =
        testSdk.doubleValueRecorderBuilder("testDoubleValueRecorder").build();
    LongValueRecorderSdk longValueRecorder =
        testSdk.longValueRecorderBuilder("testLongValueRecorder").build();
    Labels labelSet = Labels.of("key", "value");

    BatchRecorder batchRecorder = testSdk.newBatchRecorder("key", "value");

    batchRecorder
        .put(longCounter, 12)
        .put(doubleUpDownCounter, -12.1d)
        .put(longUpDownCounter, -12)
        .put(doubleCounter, 12.1d)
        .put(doubleCounter, 12.1d)
        .put(longValueRecorder, 13)
        .put(doubleValueRecorder, 13.1d);

    // until record() is called, nothing should be recorded.
    Collection<MetricData> preRecord = testSdk.collectAll();
    preRecord.forEach(metricData -> assertThat(metricData.isEmpty()).isTrue());

    batchRecorder.record();

    assertBatchRecordings(
        doubleCounter,
        longCounter,
        doubleUpDownCounter,
        longUpDownCounter,
        doubleValueRecorder,
        longValueRecorder,
        labelSet,
        /* shouldHaveDeltas=*/ true);

    // a second record, with no recordings added should not change any of the values.
    batchRecorder.record();
    assertBatchRecordings(
        doubleCounter,
        longCounter,
        doubleUpDownCounter,
        longUpDownCounter,
        doubleValueRecorder,
        longValueRecorder,
        labelSet,
        /* shouldHaveDeltas=*/ false);
  }

  private void assertBatchRecordings(
      DoubleCounterSdk doubleCounter,
      LongCounterSdk longCounter,
      DoubleUpDownCounterSdk doubleUpDownCounter,
      LongUpDownCounterSdk longUpDownCounter,
      DoubleValueRecorderSdk doubleValueRecorder,
      LongValueRecorderSdk longValueRecorder,
      Labels labelSet,
      boolean shouldHaveDeltas) {
    assertThat(TestUtils.collectAll(doubleCounter))
        .containsExactly(
            MetricData.createDoubleSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleCounter",
                "",
                "1",
                MetricData.DoubleSumData.create(
                    /* isMonotonic= */ true,
                    MetricData.AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoublePoint.create(testClock.now(), testClock.now(), labelSet, 24.2d)))));
    assertThat(TestUtils.collectAll(longCounter))
        .containsExactly(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongCounter",
                "",
                "1",
                MetricData.LongSumData.create(
                    /* isMonotonic= */ true,
                    MetricData.AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPoint.create(testClock.now(), testClock.now(), labelSet, 12)))));
    assertThat(TestUtils.collectAll(doubleUpDownCounter))
        .containsExactly(
            MetricData.createDoubleSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleUpDownCounter",
                "",
                "1",
                MetricData.DoubleSumData.create(
                    /* isMonotonic= */ false,
                    MetricData.AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoublePoint.create(testClock.now(), testClock.now(), labelSet, -12.1d)))));
    assertThat(TestUtils.collectAll(longUpDownCounter))
        .containsExactly(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongUpDownCounter",
                "",
                "1",
                MetricData.LongSumData.create(
                    /* isMonotonic= */ false,
                    MetricData.AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPoint.create(testClock.now(), testClock.now(), labelSet, -12)))));

    if (shouldHaveDeltas) {
      assertThat(TestUtils.collectAll(doubleValueRecorder))
          .containsExactly(
              MetricData.createDoubleSummary(
                  RESOURCE,
                  INSTRUMENTATION_LIBRARY_INFO,
                  "testDoubleValueRecorder",
                  "",
                  "1",
                  MetricData.DoubleSummaryData.create(
                      Collections.singletonList(
                          MetricData.DoubleSummaryPoint.create(
                              testClock.now(),
                              testClock.now(),
                              labelSet,
                              1,
                              13.1d,
                              Arrays.asList(
                                  MetricData.ValueAtPercentile.create(0.0, 13.1),
                                  MetricData.ValueAtPercentile.create(100.0, 13.1)))))));
    } else {
      assertThat(TestUtils.collectAll(doubleValueRecorder)).isEmpty();
    }

    if (shouldHaveDeltas) {
      assertThat(TestUtils.collectAll(longValueRecorder))
          .containsExactly(
              MetricData.createDoubleSummary(
                  RESOURCE,
                  INSTRUMENTATION_LIBRARY_INFO,
                  "testLongValueRecorder",
                  "",
                  "1",
                  MetricData.DoubleSummaryData.create(
                      Collections.singletonList(
                          MetricData.DoubleSummaryPoint.create(
                              testClock.now(),
                              testClock.now(),
                              labelSet,
                              1,
                              13,
                              Arrays.asList(
                                  MetricData.ValueAtPercentile.create(0.0, 13),
                                  MetricData.ValueAtPercentile.create(100.0, 13)))))));
    } else {
      assertThat(TestUtils.collectAll(longValueRecorder)).isEmpty();
    }
  }
}
