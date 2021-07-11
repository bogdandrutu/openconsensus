/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.metrics.MetricAssertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.metrics.view.InstrumentSelectionCriteria;
import io.opentelemetry.sdk.metrics.view.View;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.time.TestClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SdkMeterProvider}. */
public class SdkMeterProviderTest {
  private final TestClock testClock = TestClock.create();
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private final SdkMeterProviderBuilder sdkMeterProviderBuilder =
      SdkMeterProvider.builder().setClock(testClock).setResource(RESOURCE);

  // Consistent way of getting/naming our Meter.
  private Meter sdkMeter(MeterProvider provider) {
    return provider.meterBuilder(getClass().getName()).build();
  }

  @Test
  @SuppressWarnings("unchecked")
  void sdkMeterProvider_supportsMultipleCollectorsDelta() {
    // Note: we use a view to do delta aggregation, but any view ALWAYS uses double-precision right
    // now.
    SdkMeterProvider meterProvider =
        sdkMeterProviderBuilder
            .registerView(
                View.builder()
                    .setSelection(
                        InstrumentSelectionCriteria.builder().setInstrumentName("testSum").build())
                    .asSum()
                    .withDeltaAggregation()
                    .build())
            .build();
    MetricProducer collector1 = meterProvider.newMetricProducer();
    MetricProducer collector2 = meterProvider.newMetricProducer();
    final LongCounter counter = sdkMeter(meterProvider).counterBuilder("testSum").build();
    final long startTime = testClock.now();

    counter.add(1L);
    testClock.advance(Duration.ofSeconds(1));

    assertThat(collector1.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasName("testSum")
                    .hasDoubleSum()
                    .isDelta()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(startTime)
                                .hasEpochNanos(testClock.now())
                                .hasValue(1)));
    long collectorOneTimeOne = testClock.now();

    counter.add(1L);
    testClock.advance(Duration.ofSeconds(1));

    // Make sure collector 2 sees the value collector 1 saw
    assertThat(collector2.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasName("testSum")
                    .hasDoubleSum()
                    .isDelta()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(startTime)
                                .hasEpochNanos(testClock.now())
                                .hasValue(2)));

    // Make sure Collector 1 sees the same point as 2, when it collects.
    assertThat(collector1.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasName("testSum")
                    .hasDoubleSum()
                    .isDelta()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(collectorOneTimeOne)
                                .hasEpochNanos(testClock.now())
                                .hasValue(1)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void sdkMeterProvider_supportsMultipleCollectorsCumulative() {
    SdkMeterProvider meterProvider = sdkMeterProviderBuilder.build();
    MetricProducer collector1 = meterProvider.newMetricProducer();
    MetricProducer collector2 = meterProvider.newMetricProducer();
    final LongCounter counter = sdkMeter(meterProvider).counterBuilder("testSum").build();
    final long startTime = testClock.now();

    counter.add(1L);
    testClock.advance(Duration.ofSeconds(1));

    assertThat(collector1.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasName("testSum")
                    .hasLongSum()
                    .isCumulative()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(startTime)
                                .hasEpochNanos(testClock.now())
                                .hasValue(1)));

    counter.add(1L);
    testClock.advance(Duration.ofSeconds(1));

    // Make sure collector 2 sees the value collector 1 saw
    assertThat(collector2.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasName("testSum")
                    .hasLongSum()
                    .isCumulative()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(startTime)
                                .hasEpochNanos(testClock.now())
                                .hasValue(2)));

    // Make sure Collector 1 sees the same point as 2
    assertThat(collector1.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasName("testSum")
                    .hasLongSum()
                    .isCumulative()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(startTime)
                                .hasEpochNanos(testClock.now())
                                .hasValue(2)));
  }
}
