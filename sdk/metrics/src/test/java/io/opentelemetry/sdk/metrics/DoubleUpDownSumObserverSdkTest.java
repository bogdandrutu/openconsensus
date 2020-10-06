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
  private final MeterSdk testSdk =
      new MeterSdk(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO, new ViewRegistry());

  @Test
  void collectMetrics_NoCallback() {
    DoubleUpDownSumObserverSdk doubleUpDownSumObserver =
        testSdk
            .doubleUpDownSumObserverBuilder("testObserver")
            .setDescription("My very own DoubleUpDownSumObserver")
            .setUnit("ms")
            .build();
    assertThat(doubleUpDownSumObserver.collectAll()).isEmpty();
  }

  @Test
  void collectMetrics_NoRecords() {
    DoubleUpDownSumObserverSdk doubleUpDownSumObserver =
        testSdk
            .doubleUpDownSumObserverBuilder("testObserver")
            .setDescription("My own DoubleUpDownSumObserver")
            .setUnit("ms")
            .build();
    doubleUpDownSumObserver.setCallback(
        result -> {
          // Do nothing.
        });
    assertThat(doubleUpDownSumObserver.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "My own DoubleUpDownSumObserver",
                "ms",
                MetricData.Type.NON_MONOTONIC_DOUBLE,
                Collections.emptyList()));
  }

  @Test
  void collectMetrics_WithOneRecord() {
    DoubleUpDownSumObserverSdk doubleUpDownSumObserver =
        testSdk.doubleUpDownSumObserverBuilder("testObserver").build();
    doubleUpDownSumObserver.setCallback(result -> result.observe(12.1d, Labels.of("k", "v")));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(doubleUpDownSumObserver.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.Type.NON_MONOTONIC_DOUBLE,
                Collections.singletonList(
                    DoublePoint.create(
                        testClock.now() - SECOND_NANOS,
                        testClock.now(),
                        Labels.of("k", "v"),
                        12.1d))));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(doubleUpDownSumObserver.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.Type.NON_MONOTONIC_DOUBLE,
                Collections.singletonList(
                    DoublePoint.create(
                        testClock.now() - 2 * SECOND_NANOS,
                        testClock.now(),
                        Labels.of("k", "v"),
                        12.1d))));
  }

  @Test
  void collectMetrics_Observation() {
    DoubleUpDownSumObserverSdk doubleUpDownSumObserver =
        testSdk
            .doubleUpDownSumObserverBuilder("testObserver")
            .setDescription("My own LongSumObserver")
            .setUnit("ms")
            .build();
    assertThat(doubleUpDownSumObserver.observation(10.1d)).isEqualTo(doubleUpDownSumObserver);
  }

  @Test
  void collectMetrics_WithOneObservation() {
    DoubleUpDownSumObserverSdk doubleUpDownSumObserver =
        testSdk.doubleUpDownSumObserverBuilder("testObserver").build();
    BatchObserverSdk observer = testSdk.newBatchObserver("observer");
    observer.setFunction(
        result -> result.observe(Labels.of("k", "v"), doubleUpDownSumObserver.observation(12.1d)));

    testClock.advanceNanos(SECOND_NANOS);
    assertThat(observer.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.Type.NON_MONOTONIC_DOUBLE,
                Collections.singletonList(
                    DoublePoint.create(
                        testClock.now() - SECOND_NANOS,
                        testClock.now(),
                        Labels.of("k", "v"),
                        12.1d))));
    testClock.advanceNanos(SECOND_NANOS);
    assertThat(observer.collectAll())
        .containsExactly(
            MetricData.create(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testObserver",
                "",
                "1",
                MetricData.Type.NON_MONOTONIC_DOUBLE,
                Collections.singletonList(
                    DoublePoint.create(
                        testClock.now() - SECOND_NANOS,
                        testClock.now(),
                        Labels.of("k", "v"),
                        12.1d))));
  }
}
