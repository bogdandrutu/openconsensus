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
  private final SdkMeter testSdk =
      new SdkMeter(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO);

  @Test
  void collectMetrics_NoCallback() {
    DoubleValueObserverSdk doubleValueObserver =
        testSdk
            .doubleValueObserverBuilder("testObserver")
            .setDescription("My own DoubleValueObserver")
            .setUnit("ms")
            .build();
    assertThat(TestUtils.collectAll(doubleValueObserver)).isEmpty();
  }

  @Test
  void collectMetrics_NoRecords() {
    DoubleValueObserverSdk doubleValueObserver =
        testSdk
            .doubleValueObserverBuilder("testObserver")
            .setDescription("My own DoubleValueObserver")
            .setUnit("ms")
            .setUpdater(result -> {})
            .build();
    assertThat(TestUtils.collectAll(doubleValueObserver)).isEmpty();
  }

  @Test
  void collectMetrics_WithOneRecord() {
    DoubleValueObserverSdk doubleValueObserver =
        testSdk
            .doubleValueObserverBuilder("testObserver")
            .setDescription("My own DoubleValueObserver")
            .setUnit("ms")
            .setUpdater(result -> result.observe(12.1d, Labels.of("k", "v")))
            .build();
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(TestUtils.collectAll(doubleValueObserver))
        .containsExactly(
            MetricData.createDoubleGauge(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "My own DoubleValueObserver",
                "ms",
                MetricData.DoubleGaugeData.create(
                    Collections.singletonList(
                        DoublePoint.create(
                            testClock.now() - SECOND_NANOS,
                            testClock.now(),
                            Labels.of("k", "v"),
                            12.1d)))));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(TestUtils.collectAll(doubleValueObserver))
        .containsExactly(
            MetricData.createDoubleGauge(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "My own DoubleValueObserver",
                "ms",
                MetricData.DoubleGaugeData.create(
                    Collections.singletonList(
                        DoublePoint.create(
                            testClock.now() - SECOND_NANOS,
                            testClock.now(),
                            Labels.of("k", "v"),
                            12.1d)))));
  }
}
