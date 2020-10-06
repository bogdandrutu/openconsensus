/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.common.Attributes;
import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.DoubleSumObserver;
import io.opentelemetry.metrics.DoubleUpDownSumObserver;
import io.opentelemetry.metrics.DoubleValueObserver;
import io.opentelemetry.metrics.LongSumObserver;
import io.opentelemetry.metrics.LongUpDownSumObserver;
import io.opentelemetry.metrics.LongValueObserver;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.MillisClock;
import io.opentelemetry.sdk.internal.MonotonicClock;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricData.DoublePoint;
import io.opentelemetry.sdk.metrics.data.MetricData.LongPoint;
import io.opentelemetry.sdk.metrics.data.MetricData.SummaryPoint;
import io.opentelemetry.sdk.metrics.data.MetricData.ValueAtPercentile;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BatchRecorderSdk}. */
class BatchObserverSdkTest {
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create("io.opentelemetry.sdk.metrics.BatchObserverSdkTest", null);
  private static final TestClock testClock = TestClock.create();
  private static final Labels labelSet = Labels.of("key", "value");
  private final MeterProviderSharedState meterProviderSharedState =
      MeterProviderSharedState.create(testClock, RESOURCE);
  private final MeterSdk testSdk =
      new MeterSdk(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO, new ViewRegistry());

  @Test
  void batchObserver_nullFunction() {
    BatchObserverSdk batchObserver = testSdk.newBatchObserver("key");
    assertThat(batchObserver.collectAll()).isEqualTo(Collections.emptyList());
  }

  @Test
  void batchObserverCanCreateInstruments() {
    BatchObserverSdk batchObserver = testSdk.newBatchObserver("test");

    batchObserver.longValueObserverBuilder("longValueObserver").build();
    batchObserver.longValueObserverBuilder("negativeLongValueObserver").build();
    batchObserver.doubleValueObserverBuilder("doubleValueObserver").build();
    batchObserver.doubleValueObserverBuilder("negativeDoubleValueObserver").build();

    batchObserver.longUpDownSumObserverBuilder("longUpDownObserver").build();
    batchObserver.longUpDownSumObserverBuilder("negativeLongUpDownObserver").build();
    batchObserver.doubleUpDownSumObserverBuilder("doubleUpDownObserver").build();
    batchObserver.doubleUpDownSumObserverBuilder("negativeDoubleUpDownObserver").build();

    batchObserver.longSumObserverBuilder("longSumObserver").build();
    batchObserver.longSumObserverBuilder("negativeLongSumObserver").build();
    batchObserver.doubleSumObserverBuilder("doubleSumObserver").build();
    batchObserver.doubleSumObserverBuilder("negativeDoubleSumObserver").build();
  }

  @Test
  void batchObserverRecord() {
    BatchObserverSdk batchObserver = testSdk.newBatchObserver("observe");

    LongValueObserver longValueObserver =
        batchObserver.longValueObserverBuilder("longValueObserver").build();
    LongValueObserver negativeLongValueObserver =
        batchObserver.longValueObserverBuilder("negativeLongValueObserver").build();
    DoubleValueObserver doubleValueObserver =
        batchObserver.doubleValueObserverBuilder("doubleValueObserver").build();
    DoubleValueObserver negativeDoubleValueObserver =
        batchObserver.doubleValueObserverBuilder("negativeDoubleValueObserver").build();

    LongUpDownSumObserver longUpDownObserver =
        batchObserver.longUpDownSumObserverBuilder("longUpDownObserver").build();
    LongUpDownSumObserver negativeLongUpDownObserver =
        batchObserver.longUpDownSumObserverBuilder("negativeLongUpDownObserver").build();
    DoubleUpDownSumObserver doubleUpDownObserver =
        batchObserver.doubleUpDownSumObserverBuilder("doubleUpDownObserver").build();
    DoubleUpDownSumObserver negativeDoubleUpDownObserver =
        batchObserver.doubleUpDownSumObserverBuilder("negativeDoubleUpDownObserver").build();

    LongSumObserver longSumObserver =
        batchObserver.longSumObserverBuilder("longSumObserver").build();
    LongSumObserver negativeLongSumObserver =
        batchObserver.longSumObserverBuilder("negativeLongSumObserver").build();
    DoubleSumObserver doubleSumObserver =
        batchObserver.doubleSumObserverBuilder("doubleSumObserver").build();
    DoubleSumObserver negativeDoubleSumObserver =
        batchObserver.doubleSumObserverBuilder("negativeDoubleSumObserver").build();

    batchObserver.setFunction(
        result -> {
          result.observe(
              labelSet,
              longValueObserver.observation(44L),
              negativeLongValueObserver.observation(-44L),
              doubleValueObserver.observation(77.556d),
              negativeDoubleValueObserver.observation(-77.556d),
              longUpDownObserver.observation(44L),
              negativeLongUpDownObserver.observation(-44L),
              doubleUpDownObserver.observation(77.556d),
              negativeDoubleUpDownObserver.observation(-77.556d),
              longSumObserver.observation(44L),
              negativeLongSumObserver.observation(-44L),
              doubleSumObserver.observation(77.556d),
              negativeDoubleSumObserver.observation(-77.556d));
        });

    List<MetricData> output = batchObserver.collectAll();
    MetricData[] expected = new MetricData[12];
    expected[0] = create("longValueObserver", 44L, MetricData.Type.SUMMARY);
    expected[1] = create("negativeLongValueObserver", -44L, MetricData.Type.SUMMARY);
    expected[2] = create("doubleValueObserver", 77.556d, MetricData.Type.SUMMARY);
    expected[3] = create("negativeDoubleValueObserver", -77.556d, MetricData.Type.SUMMARY);

    expected[4] = create("longUpDownObserver", 44L, MetricData.Type.NON_MONOTONIC_LONG);
    expected[5] = create("negativeLongUpDownObserver", -44L, MetricData.Type.NON_MONOTONIC_LONG);
    expected[6] = create("doubleUpDownObserver", 77.556d, MetricData.Type.NON_MONOTONIC_DOUBLE);
    expected[7] =
        create("negativeDoubleUpDownObserver", -77.556d, MetricData.Type.NON_MONOTONIC_DOUBLE);

    expected[8] = create("longSumObserver", 44L, MetricData.Type.MONOTONIC_LONG);
    expected[9] = create("negativeLongSumObserver", -44L, MetricData.Type.MONOTONIC_LONG);
    expected[10] = create("doubleSumObserver", 77.556d, MetricData.Type.MONOTONIC_DOUBLE);
    expected[11] = create("negativeDoubleSumObserver", -77.556d, MetricData.Type.MONOTONIC_DOUBLE);

    assertThat(output).containsExactly(expected);
  }

  @Test
  void batchObserver_assureSameExportTimestamp() {

    MeterProviderSharedState meterProviderSharedState =
        MeterProviderSharedState.create(MonotonicClock.create(MillisClock.getInstance()), RESOURCE);
    final MeterSdk testSdk =
        new MeterSdk(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO, new ViewRegistry());

    BatchObserverSdk batchObserver = testSdk.newBatchObserver("observe");

    LongValueObserver longValueObserver =
        batchObserver.longValueObserverBuilder("longValueObserver").build();
    LongValueObserver negativeLongValueObserver =
        batchObserver.longValueObserverBuilder("negativeLongValueObserver").build();
    DoubleValueObserver doubleValueObserver =
        batchObserver.doubleValueObserverBuilder("doubleValueObserver").build();
    DoubleValueObserver negativeDoubleValueObserver =
        batchObserver.doubleValueObserverBuilder("negativeDoubleValueObserver").build();

    LongUpDownSumObserver longUpDownObserver =
        batchObserver.longUpDownSumObserverBuilder("longUpDownObserver").build();
    LongUpDownSumObserver negativeLongUpDownObserver =
        batchObserver.longUpDownSumObserverBuilder("negativeLongUpDownObserver").build();
    DoubleUpDownSumObserver doubleUpDownObserver =
        batchObserver.doubleUpDownSumObserverBuilder("doubleUpDownObserver").build();
    DoubleUpDownSumObserver negativeDoubleUpDownObserver =
        batchObserver.doubleUpDownSumObserverBuilder("negativeDoubleUpDownObserver").build();

    LongSumObserver longSumObserver =
        batchObserver.longSumObserverBuilder("longSumObserver").build();
    LongSumObserver negativeLongSumObserver =
        batchObserver.longSumObserverBuilder("negativeLongSumObserver").build();
    DoubleSumObserver doubleSumObserver =
        batchObserver.doubleSumObserverBuilder("doubleSumObserver").build();
    DoubleSumObserver negativeDoubleSumObserver =
        batchObserver.doubleSumObserverBuilder("negativeDoubleSumObserver").build();

    batchObserver.setFunction(
        result -> {
          result.observe(
              labelSet,
              longValueObserver.observation(44L),
              negativeLongValueObserver.observation(-44L),
              doubleValueObserver.observation(77.556d),
              negativeDoubleValueObserver.observation(-77.556d),
              longUpDownObserver.observation(44L),
              negativeLongUpDownObserver.observation(-44L),
              doubleUpDownObserver.observation(77.556d),
              negativeDoubleUpDownObserver.observation(-77.556d),
              longSumObserver.observation(44L),
              negativeLongSumObserver.observation(-44L),
              doubleSumObserver.observation(77.556d),
              negativeDoubleSumObserver.observation(-77.556d));
        });

    List<MetricData> output = batchObserver.collectAll();
    assertThat(output.size()).isEqualTo(12);
    MetricData.Point first = extractPoint(output.get(0));
    long startEpoch = first.getStartEpochNanos();
    long epoch = first.getEpochNanos();
    for (int i = 1; i < output.size(); i++) {
      MetricData.Point point = extractPoint(output.get(i));
      assertThat(point.getStartEpochNanos()).isEqualTo(startEpoch);
      assertThat(point.getEpochNanos()).isEqualTo(epoch);
    }
  }

  private static MetricData.Point extractPoint(MetricData data) {
    return data.getPoints().toArray(new MetricData.Point[0])[0];
  }

  private static MetricData create(String name, long value, MetricData.Type type) {
    MetricData.Point point =
        MetricData.Type.SUMMARY.equals(type)
            ? SummaryPoint.create(
                testClock.now(),
                testClock.now(),
                labelSet,
                1,
                value,
                valueAtPercentiles(value, value))
            : LongPoint.create(testClock.now(), testClock.now(), labelSet, value);
    return MetricData.create(
        RESOURCE,
        INSTRUMENTATION_LIBRARY_INFO,
        name,
        "",
        "1",
        type,
        Collections.singletonList(point));
  }

  private static MetricData create(String name, double value, MetricData.Type type) {
    MetricData.Point point =
        MetricData.Type.SUMMARY.equals(type)
            ? SummaryPoint.create(
                testClock.now(),
                testClock.now(),
                labelSet,
                1,
                value,
                valueAtPercentiles(value, value))
            : DoublePoint.create(testClock.now(), testClock.now(), labelSet, value);
    return MetricData.create(
        RESOURCE,
        INSTRUMENTATION_LIBRARY_INFO,
        name,
        "",
        "1",
        type,
        Collections.singletonList(point));
  }

  private static List<ValueAtPercentile> valueAtPercentiles(double min, double max) {
    return Arrays.asList(ValueAtPercentile.create(0, min), ValueAtPercentile.create(100, max));
  }
}
