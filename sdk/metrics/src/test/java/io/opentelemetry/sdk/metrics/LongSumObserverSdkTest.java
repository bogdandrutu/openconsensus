/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.metrics.MetricAssertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.time.TestClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LongSumObserverSdk}. */
class LongSumObserverSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(LongSumObserverSdkTest.class.getName(), null);
  private final TestClock testClock = TestClock.create();
  private final SdkMeterProviderBuilder sdkMeterProviderBuilder =
      SdkMeterProvider.builder().setClock(testClock).setResource(RESOURCE);

  // Consistent way of getting/naming our Meter.
  private Meter sdkMeter(MeterProvider provider) {
    return provider.meterBuilder(getClass().getName()).build();
  }

  @Test
  void collectMetrics_NoRecords() {
    SdkMeterProvider sdkMeterProvider = sdkMeterProviderBuilder.build();
    MetricProducer collector = sdkMeterProvider.newMetricProducer();
    sdkMeter(sdkMeterProvider)
        .counterBuilder("testObserver")
        .setDescription("My own LongSumObserver")
        .setUnit("ms")
        .buildWithCallback(result -> {});
    assertThat(collector.collectAllMetrics()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void collectMetrics_WithOneRecord() {
    SdkMeterProvider sdkMeterProvider = sdkMeterProviderBuilder.build();
    MetricProducer collector = sdkMeterProvider.newMetricProducer();
    sdkMeter(sdkMeterProvider)
        .counterBuilder("testObserver")
        .buildWithCallback(result -> result.observe(12, Attributes.of(stringKey("k"), "v")));
    testClock.advance(Duration.ofNanos(SECOND_NANOS));
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testObserver")
                    .hasLongSum()
                    .isMonotonic()
                    .isCumulative()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(testClock.now() - SECOND_NANOS)
                                .hasEpochNanos(testClock.now())
                                .hasValue(12)
                                .attributes()
                                .hasSize(1)
                                .containsEntry("k", "v")));
    testClock.advance(Duration.ofNanos(SECOND_NANOS));
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testObserver")
                    .hasLongSum()
                    .isMonotonic()
                    .isCumulative()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(testClock.now() - 2 * SECOND_NANOS)
                                .hasEpochNanos(testClock.now())
                                .hasValue(12)
                                .attributes()
                                .hasSize(1)
                                .containsEntry("k", "v")));
  }
}
