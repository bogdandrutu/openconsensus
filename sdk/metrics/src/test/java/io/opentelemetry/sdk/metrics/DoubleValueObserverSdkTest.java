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
import io.opentelemetry.sdk.metrics.data.DoubleGaugeData;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DoubleValueObserverSdk}. */
class DoubleValueObserverSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(DoubleValueObserverSdkTest.class.getName(), null);
  private final TestClock testClock = TestClock.create();
  private final SdkMeterProvider sdkMeterProvider =
      SdkMeterProvider.builder().setClock(testClock).setResource(RESOURCE).build();
  private final SdkMeter sdkMeter = sdkMeterProvider.get(getClass().getName());

  @Test
  void collectMetrics_NoCallback() {
    sdkMeter
        .doubleValueObserverBuilder("testObserver")
        .setDescription("My own DoubleValueObserver")
        .setUnit("ms")
        .build();
    assertThat(sdkMeterProvider.collectAllMetrics()).isEmpty();
  }

  @Test
  void collectMetrics_NoRecords() {
    sdkMeter
        .doubleValueObserverBuilder("testObserver")
        .setDescription("My own DoubleValueObserver")
        .setUnit("ms")
        .setUpdater(result -> {})
        .build();
    assertThat(sdkMeterProvider.collectAllMetrics()).isEmpty();
  }

  @Test
  void collectMetrics_WithOneRecord() {
    sdkMeter
        .doubleValueObserverBuilder("testObserver")
        .setDescription("My own DoubleValueObserver")
        .setUnit("ms")
        .setUpdater(result -> result.accept(12.1d, Labels.of("k", "v")))
        .build();
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactly(
            MetricData.createDoubleGauge(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "My own DoubleValueObserver",
                "ms",
                DoubleGaugeData.create(
                    Collections.singletonList(
                        DoublePointData.create(0, testClock.now(), Labels.of("k", "v"), 12.1d)))));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactly(
            MetricData.createDoubleGauge(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "My own DoubleValueObserver",
                "ms",
                DoubleGaugeData.create(
                    Collections.singletonList(
                        DoublePointData.create(0, testClock.now(), Labels.of("k", "v"), 12.1d)))));
  }
}
