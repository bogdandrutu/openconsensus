/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.metrics.MetricAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundDoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.StressTestRunner.OperationUpdater;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.time.TestClock;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DoubleCounterSdk}. */
class DoubleCounterSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final AttributeKey<String> FOO_KEY = AttributeKey.stringKey("foo");
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(DoubleCounterSdkTest.class.getName(), null);
  private final TestClock testClock = TestClock.create();
  private final SdkMeterProvider sdkMeterProvider =
      SdkMeterProvider.builder().setClock(testClock).setResource(RESOURCE).build();
  private final Meter sdkMeter = sdkMeterProvider.meterBuilder(getClass().getName()).build();
  private final MetricProducer collector = sdkMeterProvider.newMetricProducer();

  @Test
  void add_PreventNullAttributes() {
    assertThatThrownBy(
            () -> sdkMeter.counterBuilder("testCounter").ofDoubles().build().add(1.0, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void bound_PreventNullAttributes() {
    assertThatThrownBy(() -> sdkMeter.counterBuilder("testCounter").ofDoubles().build().bind(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void collectMetrics_NoRecords() {
    DoubleCounter doubleCounter = sdkMeter.counterBuilder("testCounter").ofDoubles().build();
    BoundDoubleCounter bound = doubleCounter.bind(Attributes.of(FOO_KEY, "bar"));
    try {
      assertThat(collector.collectAllMetrics()).isEmpty();
    } finally {
      bound.unbind();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void collectMetrics_WithEmptyLabel() {
    DoubleCounter doubleCounter =
        sdkMeter
            .counterBuilder("testCounter")
            .ofDoubles()
            .setDescription("description")
            .setUnit("ms")
            .build();
    testClock.advance(Duration.ofNanos(SECOND_NANOS));
    doubleCounter.add(12d, Attributes.empty());
    doubleCounter.add(12d);
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testCounter")
                    .hasDescription("description")
                    .hasUnit("ms")
                    .hasDoubleSum()
                    .isMonotonic()
                    .isCumulative()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(testClock.now() - SECOND_NANOS)
                                .hasEpochNanos(testClock.now())
                                .hasAttributes(Attributes.empty())
                                .hasValue(24)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void collectMetrics_WithMultipleCollects() {
    long startTime = testClock.now();
    DoubleCounter doubleCounter = sdkMeter.counterBuilder("testCounter").ofDoubles().build();
    BoundDoubleCounter bound = doubleCounter.bind(Attributes.of(stringKey("K"), "V"));
    try {
      // Do some records using bounds and direct calls and bindings.
      doubleCounter.add(12.1d, Attributes.empty());
      bound.add(123.3d);
      doubleCounter.add(21.4d, Attributes.empty());
      // Advancing time here should not matter.
      testClock.advance(Duration.ofNanos(SECOND_NANOS));
      bound.add(321.5d);
      doubleCounter.add(111.1d, Attributes.of(stringKey("K"), "V"));
      assertThat(collector.collectAllMetrics())
          .satisfiesExactly(
              metric ->
                  assertThat(metric)
                      .hasResource(RESOURCE)
                      .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                      .hasName("testCounter")
                      .hasDescription("")
                      .hasUnit("1")
                      .hasDoubleSum()
                      .isMonotonic()
                      .isCumulative()
                      .points()
                      .allSatisfy(
                          point ->
                              assertThat(point)
                                  .hasStartEpochNanos(startTime)
                                  .hasEpochNanos(testClock.now()))
                      .satisfiesExactlyInAnyOrder(
                          point ->
                              assertThat(point).hasAttributes(Attributes.empty()).hasValue(33.5),
                          point ->
                              assertThat(point)
                                  .hasValue(555.9)
                                  .attributes()
                                  .hasSize(1)
                                  .containsEntry("K", "V")));

      // Repeat to prove we keep previous values.
      testClock.advance(Duration.ofNanos(SECOND_NANOS));
      bound.add(222d);
      doubleCounter.add(11d, Attributes.empty());
      assertThat(collector.collectAllMetrics())
          .satisfiesExactly(
              metric ->
                  assertThat(metric)
                      .hasDoubleSum()
                      .isCumulative()
                      .points()
                      .allSatisfy(
                          point ->
                              assertThat(point)
                                  .hasStartEpochNanos(startTime)
                                  .hasEpochNanos(testClock.now()))
                      .satisfiesExactlyInAnyOrder(
                          point ->
                              assertThat(point).hasAttributes(Attributes.empty()).hasValue(44.5),
                          point ->
                              assertThat(point)
                                  .hasAttributes(Attributes.of(stringKey("K"), "V"))
                                  .hasValue(777.9)));
    } finally {
      bound.unbind();
    }
  }

  @Test
  void doubleCounterAdd_Monotonicity() {
    DoubleCounter doubleCounter = sdkMeter.counterBuilder("testCounter").ofDoubles().build();

    assertThatThrownBy(() -> doubleCounter.add(-45.77d, Attributes.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void boundDoubleCounterAdd_Monotonicity() {
    DoubleCounter doubleCounter = sdkMeter.counterBuilder("testCounter").ofDoubles().build();

    assertThatThrownBy(() -> doubleCounter.bind(Attributes.empty()).add(-9.3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void stressTest() {
    final DoubleCounter doubleCounter = sdkMeter.counterBuilder("testCounter").ofDoubles().build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setMeter((SdkMeter) sdkMeter).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000, 2, new OperationUpdaterDirectCall(doubleCounter, stringKey("K"), "V")));
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new OperationUpdaterWithBinding(
                  doubleCounter.bind(Attributes.of(stringKey("K"), "V")))));
    }

    stressTestBuilder.build().run();
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testCounter")
                    .hasDoubleSum()
                    .isCumulative()
                    .isMonotonic()
                    .points()
                    .satisfiesExactlyInAnyOrder(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(testClock.now())
                                .hasEpochNanos(testClock.now())
                                .hasValue(80_000)
                                .attributes()
                                .hasSize(1)
                                .containsEntry("K", "V")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void stressTest_WithDifferentAttributeset() {
    final List<AttributeKey<String>> keys =
        Stream.of(stringKey("Key_1"), stringKey("Key_2"), stringKey("Key_3"), stringKey("Key_4"))
            .collect(Collectors.toList());
    final String[] values = {"Value_1", "Value_2", "Value_3", "Value_4"};
    final DoubleCounter doubleCounter = sdkMeter.counterBuilder("testCounter").ofDoubles().build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setMeter((SdkMeter) sdkMeter).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000, 1, new OperationUpdaterDirectCall(doubleCounter, keys.get(i), values[i])));

      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new OperationUpdaterWithBinding(
                  doubleCounter.bind(Attributes.of(keys.get(i), values[i])))));
    }

    stressTestBuilder.build().run();

    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasDoubleSum()
                    .isCumulative()
                    .isMonotonic()
                    .points()
                    .allSatisfy(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(testClock.now())
                                .hasEpochNanos(testClock.now())
                                .hasValue(40_000))
                    .extracting(point -> point.getAttributes())
                    .containsExactlyInAnyOrder(
                        Attributes.of(keys.get(0), values[0]),
                        Attributes.of(keys.get(1), values[1]),
                        Attributes.of(keys.get(2), values[2]),
                        Attributes.of(keys.get(3), values[3])));
  }

  private static class OperationUpdaterWithBinding extends OperationUpdater {
    private final BoundDoubleCounter boundDoubleCounter;

    private OperationUpdaterWithBinding(BoundDoubleCounter boundDoubleCounter) {
      this.boundDoubleCounter = boundDoubleCounter;
    }

    @Override
    void update() {
      boundDoubleCounter.add(9.0);
    }

    @Override
    void cleanup() {
      boundDoubleCounter.unbind();
    }
  }

  private static class OperationUpdaterDirectCall extends OperationUpdater {

    private final DoubleCounter doubleCounter;
    private final AttributeKey<String> key;
    private final String value;

    private OperationUpdaterDirectCall(
        DoubleCounter doubleCounter, AttributeKey<String> key, String value) {
      this.doubleCounter = doubleCounter;
      this.key = key;
      this.value = value;
    }

    @Override
    void update() {
      doubleCounter.add(11.0, Attributes.of(key, value));
    }

    @Override
    void cleanup() {}
  }
}
