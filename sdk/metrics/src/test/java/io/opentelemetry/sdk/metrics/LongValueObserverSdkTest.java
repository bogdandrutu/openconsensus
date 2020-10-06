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

/** Unit tests for {@link LongValueObserverSdk}. */
class LongValueObserverSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(
          "io.opentelemetry.sdk.metrics.LongValueObserverSdkTest", null);
  private final TestClock testClock = TestClock.create();
  private final MeterProviderSharedState meterProviderSharedState =
      MeterProviderSharedState.create(testClock, RESOURCE);
  private final MeterSdk testSdk =
      new MeterSdk(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO, new ViewRegistry());

  @Test
  void collectMetrics_NoCallback() {
    LongValueObserverSdk longValueObserver =
        testSdk
            .longValueObserverBuilder("testObserver")
            .setDescription("My own LongValueObserver")
            .setUnit("ms")
            .build();
    assertThat(longValueObserver.collectAll()).isEmpty();
  }

  @Test
  void collectMetrics_NoRecords() {
    LongValueObserverSdk longValueObserver =
        testSdk
            .longValueObserverBuilder("testObserver")
            .setDescription("My own LongValueObserver")
            .setUnit("ms")
            .build();
    longValueObserver.setCallback(
        result -> {
          // Do nothing.
        });
    assertThat(longValueObserver.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "My own LongValueObserver",
                "ms",
                MetricData.Type.SUMMARY,
                Collections.emptyList()));
  }

  @Test
  void collectMetrics_WithOneRecord() {
    LongValueObserverSdk longValueObserver =
        testSdk.longValueObserverBuilder("testObserver").build();
    longValueObserver.setCallback(result -> result.observe(12, Labels.of("k", "v")));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(longValueObserver.collectAll())
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
                        12,
                        valueAtPercentiles(12, 12)))));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(longValueObserver.collectAll())
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
                        12,
                        valueAtPercentiles(12, 12)))));
  }

  @Test
  void collectMetrics_Observation() {
    LongValueObserverSdk longValueObserver =
        testSdk
            .longValueObserverBuilder("testObserver")
            .setDescription("My own LongSumObserver")
            .setUnit("ms")
            .build();
    assertThat(longValueObserver.observation(10)).isEqualTo(longValueObserver);
  }

  @Test
  void collectMetrics_WithOneObservation() {
    LongValueObserverSdk longValueObserver =
        testSdk.longValueObserverBuilder("testObserver").build();
    BatchObserverSdk observer = testSdk.newBatchObserver("observer");
    observer.setFunction(
        result -> result.observe(Labels.of("k", "v"), longValueObserver.observation(12)));

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
                        12,
                        valueAtPercentiles(12, 12)))));
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
                        12,
                        valueAtPercentiles(12, 12)))));
  }

  private static List<ValueAtPercentile> valueAtPercentiles(double min, double max) {
    return Arrays.asList(ValueAtPercentile.create(0, min), ValueAtPercentile.create(100, max));
  }
}
