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
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.LongSumData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LongUpDownSumObserverSdk}. */
class LongUpDownSumObserverSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(LongUpDownSumObserverSdkTest.class.getName(), null);
  private final TestClock testClock = TestClock.create();
  private final SdkMeterProvider sdkMeterProvider =
      SdkMeterProvider.builder().setClock(testClock).setResource(RESOURCE).build();
  private final SdkMeter sdkMeter = sdkMeterProvider.get(getClass().getName());

  @Test
  void collectMetrics_NoCallback() {
    sdkMeter
        .longUpDownSumObserverBuilder("testObserver")
        .setDescription("My own LongUpDownSumObserver")
        .setUnit("ms")
        .build();
    assertThat(sdkMeterProvider.collectAllMetrics()).isEmpty();
  }

  @Test
  void collectMetrics_NoRecords() {
    sdkMeter
        .longUpDownSumObserverBuilder("testObserver")
        .setDescription("My own LongUpDownSumObserver")
        .setUnit("ms")
        .setUpdater(result -> {})
        .build();
    assertThat(sdkMeterProvider.collectAllMetrics()).isEmpty();
  }

  @Test
  void collectMetrics_WithOneRecord() {
    sdkMeter
        .longUpDownSumObserverBuilder("testObserver")
        .setUpdater(result -> result.accept(12L, Labels.of("k", "v")))
        .build();
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactly(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ false,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - SECOND_NANOS,
                            testClock.now(),
                            Labels.of("k", "v"),
                            12)))));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactly(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ false,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 2 * SECOND_NANOS,
                            testClock.now(),
                            Labels.of("k", "v"),
                            12)))));
  }
}
