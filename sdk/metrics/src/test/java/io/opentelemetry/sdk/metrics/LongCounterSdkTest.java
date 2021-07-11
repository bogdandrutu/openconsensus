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
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.StressTestRunner.OperationUpdater;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.time.TestClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SdkLongCounter}. */
class LongCounterSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final AttributeKey<String> FOO_KEY = stringKey("foo");
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(LongCounterSdkTest.class.getName(), null);
  private final TestClock testClock = TestClock.create();
  private final SdkMeterProvider sdkMeterProvider =
      SdkMeterProvider.builder().setClock(testClock).setResource(RESOURCE).build();
  private final Meter sdkMeter = sdkMeterProvider.meterBuilder(getClass().getName()).build();
  private final MetricProducer collector = sdkMeterProvider.newMetricProducer();

  @Test
  void add_PreventNullAttributes() {
    assertThatThrownBy(() -> sdkMeter.counterBuilder("testCounter").build().add(1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void bound_PreventNullAttributes() {
    assertThatThrownBy(() -> sdkMeter.counterBuilder("testCounter").build().bind(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Null attributes");
  }

  @Test
  void collectMetrics_NoRecords() {
    LongCounter longCounter = sdkMeter.counterBuilder("testCounter").build();
    BoundLongCounter bound = longCounter.bind(Attributes.of(FOO_KEY, "bar"));
    try {
      assertThat(collector.collectAllMetrics()).isEmpty();
    } finally {
      bound.unbind();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void collectMetrics_WithEmptyLabels() {
    LongCounter longCounter =
        sdkMeter.counterBuilder("testCounter").setDescription("description").setUnit("By").build();
    testClock.advance(Duration.ofNanos(SECOND_NANOS));
    longCounter.add(12, Attributes.empty());
    longCounter.add(12);
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testCounter")
                    .hasDescription("description")
                    .hasUnit("By")
                    .hasLongSum()
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
    LongCounter longCounter = sdkMeter.counterBuilder("testCounter").build();
    BoundLongCounter bound = longCounter.bind(Attributes.of(FOO_KEY, "V"));
    try {
      // Do some records using bounds and direct calls and bindings.
      longCounter.add(12, Attributes.empty());
      bound.add(123);
      longCounter.add(21, Attributes.empty());
      // Advancing time here should not matter.
      testClock.advance(Duration.ofNanos(SECOND_NANOS));
      bound.add(321);
      longCounter.add(111, Attributes.of(FOO_KEY, "V"));
      assertThat(collector.collectAllMetrics())
          .satisfiesExactly(
              metric ->
                  assertThat(metric)
                      .hasResource(RESOURCE)
                      .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                      .hasName("testCounter")
                      .hasLongSum()
                      .isMonotonic()
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
                                  .hasAttributes(Attributes.of(FOO_KEY, "V"))
                                  .hasValue(555)));

      // Repeat to prove we keep previous values.
      testClock.advance(Duration.ofNanos(SECOND_NANOS));
      bound.add(222);
      longCounter.add(11, Attributes.empty());
      assertThat(collector.collectAllMetrics())
          .satisfiesExactly(
              metric ->
                  assertThat(metric)
                      .hasResource(RESOURCE)
                      .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                      .hasName("testCounter")
                      .hasLongSum()
                      .isMonotonic()
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
                                  .hasAttributes(Attributes.of(FOO_KEY, "V"))
                                  .hasValue(777)));
    } finally {
      bound.unbind();
    }
  }

  @Test
  void longCounterAdd_MonotonicityCheck() {
    LongCounter longCounter = sdkMeter.counterBuilder("testCounter").build();

    assertThatThrownBy(() -> longCounter.add(-45, Attributes.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void boundLongCounterAdd_MonotonicityCheck() {
    LongCounter longCounter = sdkMeter.counterBuilder("testCounter").build();

    assertThatThrownBy(() -> longCounter.bind(Attributes.empty()).add(-9))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void stressTest() {
    final LongCounter longCounter = sdkMeter.counterBuilder("testCounter").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setMeter((SdkMeter) sdkMeter).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000, 1, new OperationUpdaterDirectCall(longCounter, FOO_KEY, "V")));
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new OperationUpdaterWithBinding(longCounter.bind(Attributes.of(FOO_KEY, "V")))));
    }

    stressTestBuilder.build().run();
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testCounter")
                    .hasLongSum()
                    .isCumulative()
                    .isMonotonic()
                    .points()
                    .satisfiesExactlyInAnyOrder(
                        point ->
                            assertThat(point)
                                .hasStartEpochNanos(testClock.now())
                                .hasEpochNanos(testClock.now())
                                .hasValue(160_000)
                                .attributes()
                                .hasSize(1)
                                .containsEntry(FOO_KEY, "V")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void stressTest_WithDifferentLabelSet() {
    final String[] keys = {"Key_1", "Key_2", "Key_3", "Key_4"};
    final String[] values = {"Value_1", "Value_2", "Value_3", "Value_4"};
    final LongCounter longCounter = sdkMeter.counterBuilder("testCounter").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setMeter((SdkMeter) sdkMeter).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new OperationUpdaterDirectCall(longCounter, stringKey(keys[i]), values[i])));

      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new OperationUpdaterWithBinding(
                  longCounter.bind(Attributes.of(stringKey(keys[i]), values[i])))));
    }

    stressTestBuilder.build().run();
    assertThat(collector.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasResource(RESOURCE)
                    .hasInstrumentationLibrary(INSTRUMENTATION_LIBRARY_INFO)
                    .hasName("testCounter")
                    .hasLongSum()
                    .isCumulative()
                    .isMonotonic()
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
    private final BoundLongCounter boundLongCounter;

    private OperationUpdaterWithBinding(BoundLongCounter boundLongCounter) {
      this.boundLongCounter = boundLongCounter;
    }

    @Override
    void update() {
      boundLongCounter.add(9);
    }

    @Override
    void cleanup() {
      boundLongCounter.unbind();
    }
  }

  private static class OperationUpdaterDirectCall extends OperationUpdater {

    private final LongCounter longCounter;
    private final AttributeKey<String> key;
    private final String value;

    private OperationUpdaterDirectCall(
        LongCounter longCounter, AttributeKey<String> key, String value) {
      this.longCounter = longCounter;
      this.key = key;
      this.value = value;
    }

    @Override
    void update() {
      longCounter.add(11, Attributes.of(key, value));
    }

    @Override
    void cleanup() {}
  }
}
