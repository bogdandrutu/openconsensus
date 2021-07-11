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
import io.opentelemetry.api.metrics.BoundLongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.StressTestRunner.OperationUpdater;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.time.TestClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LongUpDownCounterSdk}. */
class LongUpDownCounterSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final AttributeKey<String> FOO_KEY = stringKey("foo");
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(LongUpDownCounterSdkTest.class.getName(), null);
  private final TestClock testClock = TestClock.create();
  private final SdkMeterProvider sdkMeterProvider =
      SdkMeterProvider.builder().setClock(testClock).setResource(RESOURCE).build();
  private final Meter sdkMeter = sdkMeterProvider.meterBuilder(getClass().getName()).build();
  private final MetricProducer collector = sdkMeterProvider.newMetricProducer();

  @Test
  void add_PreventNullAttributes() {
    assertThatThrownBy(() -> sdkMeter.upDownCounterBuilder("testCounter").build().add(1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void bound_PreventNullAttributes() {
    assertThatThrownBy(() -> sdkMeter.upDownCounterBuilder("testUpDownCounter").build().bind(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void collectMetrics_NoRecords() {
    LongUpDownCounter longUpDownCounter =
        sdkMeter.upDownCounterBuilder("testUpDownCounter").build();
    BoundLongUpDownCounter bound = longUpDownCounter.bind(Attributes.of(FOO_KEY, "bar"));
    try {
      assertThat(collector.collectAllMetrics()).isEmpty();
    } finally {
      bound.unbind();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void collectMetrics_WithEmptyLabel() {
    LongUpDownCounter longUpDownCounter =
        sdkMeter
            .upDownCounterBuilder("testUpDownCounter")
            .setDescription("description")
            .setUnit("By")
            .build();
    testClock.advance(Duration.ofNanos(SECOND_NANOS));
    longUpDownCounter.add(12, Attributes.empty());
    longUpDownCounter.add(12);
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testUpDownCounter")
                    .hasDescription("description")
                    .hasUnit("By")
                    .hasLongSum()
                    .isNotMonotonic()
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
    LongUpDownCounter longUpDownCounter =
        sdkMeter.upDownCounterBuilder("testUpDownCounter").build();
    BoundLongUpDownCounter bound = longUpDownCounter.bind(Attributes.of(stringKey("K"), "V"));
    try {
      // Do some records using bounds and direct calls and bindings.
      longUpDownCounter.add(12, Attributes.empty());
      bound.add(123);
      longUpDownCounter.add(21, Attributes.empty());
      // Advancing time here should not matter.
      testClock.advance(Duration.ofNanos(SECOND_NANOS));
      bound.add(321);
      longUpDownCounter.add(111, Attributes.of(stringKey("K"), "V"));
      assertThat(collector.collectAllMetrics())
          .satisfiesExactly(
              metric ->
                  assertThat(metric)
                      .hasResource(RESOURCE)
                      .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                      .hasName("testUpDownCounter")
                      .hasLongSum()
                      .isNotMonotonic()
                      .isCumulative()
                      .points()
                      .allSatisfy(
                          point ->
                              assertThat(point)
                                  .hasStartEpochNanos(startTime)
                                  .hasEpochNanos(testClock.now()))
                      .satisfiesExactlyInAnyOrder(
                          point -> assertThat(point).hasAttributes(Attributes.empty()).hasValue(33),
                          point ->
                              assertThat(point)
                                  .hasAttributes(Attributes.of(stringKey("K"), "V"))
                                  .hasValue(555)));

      // Repeat to prove we keep previous values.
      testClock.advance(Duration.ofNanos(SECOND_NANOS));
      bound.add(222);
      longUpDownCounter.add(11, Attributes.empty());
      assertThat(collector.collectAllMetrics())
          .satisfiesExactly(
              metric ->
                  assertThat(metric)
                      .hasResource(RESOURCE)
                      .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                      .hasName("testUpDownCounter")
                      .hasLongSum()
                      .isNotMonotonic()
                      .isCumulative()
                      .points()
                      .allSatisfy(
                          point ->
                              assertThat(point)
                                  .hasStartEpochNanos(startTime)
                                  .hasEpochNanos(testClock.now()))
                      .satisfiesExactlyInAnyOrder(
                          point -> assertThat(point).hasAttributes(Attributes.empty()).hasValue(44),
                          point ->
                              assertThat(point)
                                  .hasAttributes(Attributes.of(stringKey("K"), "V"))
                                  .hasValue(777)));
    } finally {
      bound.unbind();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void stressTest() {
    final LongUpDownCounter longUpDownCounter =
        sdkMeter.upDownCounterBuilder("testUpDownCounter").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setMeter((SdkMeter) sdkMeter).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000, 1, new OperationUpdaterDirectCall(longUpDownCounter, stringKey("K"), "V")));
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new OperationUpdaterWithBinding(
                  longUpDownCounter.bind(Attributes.of(stringKey("K"), "V")))));
    }

    stressTestBuilder.build().run();
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testUpDownCounter")
                    .hasLongSum()
                    .isCumulative()
                    .isNotMonotonic()
                    .points()
                    .satisfiesExactlyInAnyOrder(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(testClock.now())
                                .hasEpochNanos(testClock.now())
                                .hasValue(160_000)
                                .attributes()
                                .hasSize(1)
                                .containsEntry("K", "V")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void stressTest_WithDifferentLabelSet() {
    final String[] keys = {"Key_1", "Key_2", "Key_3", "Key_4"};
    final String[] values = {"Value_1", "Value_2", "Value_3", "Value_4"};
    final LongUpDownCounter longUpDownCounter =
        sdkMeter.upDownCounterBuilder("testUpDownCounter").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setMeter((SdkMeter) sdkMeter).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new OperationUpdaterDirectCall(longUpDownCounter, stringKey(keys[i]), values[i])));

      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new OperationUpdaterWithBinding(
                  longUpDownCounter.bind(Attributes.of(stringKey(keys[i]), values[i])))));
    }

    stressTestBuilder.build().run();
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testUpDownCounter")
                    .hasLongSum()
                    .isCumulative()
                    .isNotMonotonic()
                    .points()
                    .allSatisfy(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(testClock.now())
                                .hasEpochNanos(testClock.now())
                                .hasValue(20_000))
                    .extracting(point -> point.getAttributes())
                    .containsExactlyInAnyOrder(
                        Attributes.of(stringKey(keys[0]), values[0]),
                        Attributes.of(stringKey(keys[1]), values[1]),
                        Attributes.of(stringKey(keys[2]), values[2]),
                        Attributes.of(stringKey(keys[3]), values[3])));
  }

  private static class OperationUpdaterWithBinding extends OperationUpdater {
    private final BoundLongUpDownCounter boundLongUpDownCounter;

    private OperationUpdaterWithBinding(BoundLongUpDownCounter boundLongUpDownCounter) {
      this.boundLongUpDownCounter = boundLongUpDownCounter;
    }

    @Override
    void update() {
      boundLongUpDownCounter.add(9);
    }

    @Override
    void cleanup() {
      boundLongUpDownCounter.unbind();
    }
  }

  private static class OperationUpdaterDirectCall extends OperationUpdater {

    private final LongUpDownCounter longUpDownCounter;
    private final AttributeKey<String> key;
    private final String value;

    private OperationUpdaterDirectCall(
        LongUpDownCounter longUpDownCounter, AttributeKey<String> key, String value) {
      this.longUpDownCounter = longUpDownCounter;
      this.key = key;
      this.value = value;
    }

    @Override
    void update() {
      longUpDownCounter.add(11, Attributes.of(key, value));
    }

    @Override
    void cleanup() {}
  }
}
