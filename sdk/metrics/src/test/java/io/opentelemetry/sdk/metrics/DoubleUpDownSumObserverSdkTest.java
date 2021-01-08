/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricData.DoublePoint;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DoubleUpDownSumObserverSdk}. */
class DoubleUpDownSumObserverSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(
          "io.opentelemetry.sdk.metrics.DoubleUpDownSumObserverSdkTest", null);
  private final TestClock testClock = TestClock.create();
  private final MeterProviderSharedState meterProviderSharedState =
      MeterProviderSharedState.create(testClock, RESOURCE);
  private final SdkMeter testSdk =
      new SdkMeter(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO);

  @Test
  void collectMetrics_NoCallback() {
    DoubleUpDownSumObserverSdk doubleUpDownSumObserver =
        testSdk
            .doubleUpDownSumObserverBuilder("testObserver")
            .setDescription("My own DoubleUpDownSumObserver")
            .setUnit("ms")
            .build();
    assertThat(TestUtils.collectAll(doubleUpDownSumObserver)).isEmpty();
  }

  @Test
  void collectMetrics_NoRecords() {
    DoubleUpDownSumObserverSdk doubleUpDownSumObserver =
        testSdk
            .doubleUpDownSumObserverBuilder("testObserver")
            .setDescription("My own DoubleUpDownSumObserver")
            .setUnit("ms")
            .setUpdater(result -> {})
            .build();
    assertThat(TestUtils.collectAll(doubleUpDownSumObserver)).isEmpty();
  }

  @Test
  void collectMetrics_WithOneRecord() {
    DoubleUpDownSumObserverSdk doubleUpDownSumObserver =
        testSdk
            .doubleUpDownSumObserverBuilder("testObserver")
            .setUpdater(result -> result.observe(12.1d, Labels.of("k", "v")))
            .build();
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(TestUtils.collectAll(doubleUpDownSumObserver))
        .containsExactly(
            MetricData.createDoubleSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.DoubleSumData.create(
                    /* isMonotonic= */ false,
                    MetricData.AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoublePoint.create(
                            testClock.now() - SECOND_NANOS,
                            testClock.now(),
                            Labels.of("k", "v"),
                            12.1d)))));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(TestUtils.collectAll(doubleUpDownSumObserver))
        .containsExactly(
            MetricData.createDoubleSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.DoubleSumData.create(
                    /* isMonotonic= */ false,
                    MetricData.AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoublePoint.create(
                            testClock.now() - 2 * SECOND_NANOS,
                            testClock.now(),
                            Labels.of("k", "v"),
                            12.1d)))));
  }
}
