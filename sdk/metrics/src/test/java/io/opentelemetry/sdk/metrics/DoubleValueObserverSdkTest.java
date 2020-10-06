/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.common.Attributes;
import io.opentelemetry.common.Labels;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricData.SummaryPoint;
import io.opentelemetry.sdk.metrics.data.MetricData.ValueAtPercentile;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DoubleValueObserverSdk}. */
class DoubleValueObserverSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(
          "io.opentelemetry.sdk.metrics.DoubleValueObserverSdkTest", null);
  private final TestClock testClock = TestClock.create();
  private final MeterProviderSharedState meterProviderSharedState =
      MeterProviderSharedState.create(testClock, RESOURCE);
  private final MeterSdk testSdk =
      new MeterSdk(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO, new ViewRegistry());

  @Test
  void collectMetrics_NoCallback() {
    DoubleValueObserverSdk doubleValueObserver =
        testSdk
            .doubleValueObserverBuilder("testObserver")
            .setDescription("My own DoubleValueObserver")
            .setUnit("ms")
            .build();
    assertThat(doubleValueObserver.collectAll()).isEmpty();
  }

  @Test
  void collectMetrics_NoRecords() {
    DoubleValueObserverSdk doubleValueObserver =
        testSdk
            .doubleValueObserverBuilder("testObserver")
            .setDescription("My own DoubleValueObserver")
            .setUnit("ms")
            .build();
    doubleValueObserver.setCallback(
        result -> {
          // Do nothing.
        });
    assertThat(doubleValueObserver.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "My own DoubleValueObserver",
                "ms",
                MetricData.Type.SUMMARY,
                Collections.emptyList()));
  }

  @Test
  void collectMetrics_WithOneRecord() {
    DoubleValueObserverSdk doubleValueObserver =
        testSdk.doubleValueObserverBuilder("testObserver").build();
    doubleValueObserver.setCallback(result -> result.observe(12.1d, Labels.of("k", "v")));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(doubleValueObserver.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.Type.SUMMARY,
                Collections.singletonList(
                    SummaryPoint.create(
                        testClock.now() - SECOND_NANOS,
                        testClock.now(),
                        Labels.of("k", "v"),
                        1,
                        12.1d,
                        valueAtPercentiles(12.1d, 12.1d)))));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(doubleValueObserver.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.Type.SUMMARY,
                Collections.singletonList(
                    SummaryPoint.create(
                        testClock.now() - SECOND_NANOS,
                        testClock.now(),
                        Labels.of("k", "v"),
                        1,
                        12.1d,
                        valueAtPercentiles(12.1d, 12.1d)))));
  }

  @Test
  void collectMetrics_Observation() {
    DoubleValueObserverSdk doubleValueObserver =
        testSdk
            .doubleValueObserverBuilder("testObserver")
            .setDescription("My own LongSumObserver")
            .setUnit("ms")
            .build();
    assertThat(doubleValueObserver.observation(10)).isEqualTo(doubleValueObserver);
  }

  @Test
  void collectMetrics_WithOneObservation() {
    DoubleValueObserverSdk doubleValueObserver =
        testSdk.doubleValueObserverBuilder("testObserver").build();
    BatchObserverSdk observer = testSdk.newBatchObserver("observer");
    observer.setFunction(
        result -> result.observe(Labels.of("k", "v"), doubleValueObserver.observation(12.1d)));

    testClock.advanceNanos(SECOND_NANOS);
    assertThat(observer.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.Type.SUMMARY,
                Collections.singletonList(
                    SummaryPoint.create(
                        testClock.now() - SECOND_NANOS,
                        testClock.now(),
                        Labels.of("k", "v"),
                        1,
                        12.1d,
                        valueAtPercentiles(12.1d, 12.1d)))));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(observer.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.Type.SUMMARY,
                Collections.singletonList(
                    SummaryPoint.create(
                        testClock.now() - SECOND_NANOS,
                        testClock.now(),
                        Labels.of("k", "v"),
                        1,
                        12.1d,
                        valueAtPercentiles(12.1d, 12.1d)))));
  }

  private static List<ValueAtPercentile> valueAtPercentiles(double min, double max) {
    return Arrays.asList(ValueAtPercentile.create(0, min), ValueAtPercentile.create(100, max));
  }
}
