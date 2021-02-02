/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleValueRecorder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongValueRecorder;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleGaugeData;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.DoubleSumData;
import io.opentelemetry.sdk.metrics.data.DoubleSummaryData;
import io.opentelemetry.sdk.metrics.data.DoubleSummaryPointData;
import io.opentelemetry.sdk.metrics.data.LongGaugeData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.LongSumData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.ValueAtPercentile;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class SdkMeterProviderTest {
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(AttributeKey.stringKey("resource_key"), "resource_value"));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(SdkMeterProviderTest.class.getName(), null);
  private final TestClock testClock = TestClock.create();
  private final SdkMeterProvider sdkMeterProvider =
      SdkMeterProvider.builder().setClock(testClock).setResource(RESOURCE).build();
  private final SdkMeter sdkMeter = sdkMeterProvider.get(SdkMeterProviderTest.class.getName());

  @Test
  void defaultMeterName() {
    assertThat(sdkMeterProvider.get(null)).isSameAs(sdkMeterProvider.get("unknown"));
  }

  @Test
  void collectAllSyncInstruments() {
    LongCounter longCounter = sdkMeter.longCounterBuilder("testLongCounter").build();
    longCounter.add(10, Labels.empty());
    LongUpDownCounter longUpDownCounter =
        sdkMeter.longUpDownCounterBuilder("testLongUpDownCounter").build();
    longUpDownCounter.add(-10, Labels.empty());
    LongValueRecorder longValueRecorder =
        sdkMeter.longValueRecorderBuilder("testLongValueRecorder").build();
    longValueRecorder.record(10, Labels.empty());
    DoubleCounter doubleCounter = sdkMeter.doubleCounterBuilder("testDoubleCounter").build();
    doubleCounter.add(10.1, Labels.empty());
    DoubleUpDownCounter doubleUpDownCounter =
        sdkMeter.doubleUpDownCounterBuilder("testDoubleUpDownCounter").build();
    doubleUpDownCounter.add(-10.1, Labels.empty());
    DoubleValueRecorder doubleValueRecorder =
        sdkMeter.doubleValueRecorderBuilder("testDoubleValueRecorder").build();
    doubleValueRecorder.record(10.1, Labels.empty());

    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactlyInAnyOrder(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now(), testClock.now(), Labels.empty(), 10)))),
            MetricData.createDoubleSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleCounter",
                "",
                "1",
                DoubleSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoublePointData.create(
                            testClock.now(), testClock.now(), Labels.empty(), 10.1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongUpDownCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ false,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now(), testClock.now(), Labels.empty(), -10)))),
            MetricData.createDoubleSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleUpDownCounter",
                "",
                "1",
                DoubleSumData.create(
                    /* isMonotonic= */ false,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoublePointData.create(
                            testClock.now(), testClock.now(), Labels.empty(), -10.1)))),
            MetricData.createDoubleSummary(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongValueRecorder",
                "",
                "1",
                DoubleSummaryData.create(
                    Collections.singletonList(
                        DoubleSummaryPointData.create(
                            testClock.now(),
                            testClock.now(),
                            Labels.empty(),
                            1,
                            10,
                            Arrays.asList(
                                ValueAtPercentile.create(0, 10),
                                ValueAtPercentile.create(100, 10)))))),
            MetricData.createDoubleSummary(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleValueRecorder",
                "",
                "1",
                DoubleSummaryData.create(
                    Collections.singletonList(
                        DoubleSummaryPointData.create(
                            testClock.now(),
                            testClock.now(),
                            Labels.empty(),
                            1,
                            10.1d,
                            Arrays.asList(
                                ValueAtPercentile.create(0, 10.1d),
                                ValueAtPercentile.create(100, 10.1d)))))));
  }

  @Test
  void collectAllSyncInstruments_OverwriteTemporality() {
    sdkMeterProvider.registerView(
        InstrumentSelector.builder().setInstrumentType(InstrumentType.COUNTER).build(),
        AggregatorFactory.sum(false));

    LongCounter longCounter = sdkMeter.longCounterBuilder("testLongCounter").build();
    longCounter.add(10, Labels.empty());
    testClock.advanceNanos(50);

    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactlyInAnyOrder(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 10)))));

    longCounter.add(10, Labels.empty());
    testClock.advanceNanos(50);

    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactlyInAnyOrder(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 10)))));
  }

  @Test
  void collectAllSyncInstruments_DeltaCount() {
    registerViewForAllTypes(
        sdkMeterProvider, AggregatorFactory.count(AggregationTemporality.DELTA));
    LongCounter longCounter = sdkMeter.longCounterBuilder("testLongCounter").build();
    longCounter.add(10, Labels.empty());
    LongUpDownCounter longUpDownCounter =
        sdkMeter.longUpDownCounterBuilder("testLongUpDownCounter").build();
    longUpDownCounter.add(-10, Labels.empty());
    LongValueRecorder longValueRecorder =
        sdkMeter.longValueRecorderBuilder("testLongValueRecorder").build();
    longValueRecorder.record(10, Labels.empty());
    DoubleCounter doubleCounter = sdkMeter.doubleCounterBuilder("testDoubleCounter").build();
    doubleCounter.add(10.1, Labels.empty());
    DoubleUpDownCounter doubleUpDownCounter =
        sdkMeter.doubleUpDownCounterBuilder("testDoubleUpDownCounter").build();
    doubleUpDownCounter.add(-10.1, Labels.empty());
    DoubleValueRecorder doubleValueRecorder =
        sdkMeter.doubleValueRecorderBuilder("testDoubleValueRecorder").build();
    doubleValueRecorder.record(10.1, Labels.empty());

    testClock.advanceNanos(50);

    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactlyInAnyOrder(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongUpDownCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleUpDownCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongValueRecorder",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleValueRecorder",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))));

    testClock.advanceNanos(50);

    longCounter.add(10, Labels.empty());
    longUpDownCounter.add(-10, Labels.empty());
    longValueRecorder.record(10, Labels.empty());
    doubleCounter.add(10.1, Labels.empty());
    doubleUpDownCounter.add(-10.1, Labels.empty());
    doubleValueRecorder.record(10.1, Labels.empty());

    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactlyInAnyOrder(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongUpDownCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleUpDownCounter",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongValueRecorder",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleValueRecorder",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.DELTA,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))));
  }

  @Test
  void collectAllAsyncInstruments() {
    sdkMeter
        .longSumObserverBuilder("testLongSumObserver")
        .setUpdater(longResult -> longResult.accept(10L, Labels.empty()))
        .build();
    sdkMeter
        .longUpDownSumObserverBuilder("testLongUpDownSumObserver")
        .setUpdater(longResult -> longResult.accept(-10L, Labels.empty()))
        .build();
    sdkMeter
        .longValueObserverBuilder("testLongValueObserver")
        .setUpdater(longResult -> longResult.accept(10L, Labels.empty()))
        .build();

    sdkMeter
        .doubleSumObserverBuilder("testDoubleSumObserver")
        .setUpdater(doubleResult -> doubleResult.accept(10.1, Labels.empty()))
        .build();
    sdkMeter
        .doubleUpDownSumObserverBuilder("testDoubleUpDownSumObserver")
        .setUpdater(doubleResult -> doubleResult.accept(-10.1, Labels.empty()))
        .build();
    sdkMeter
        .doubleValueObserverBuilder("testDoubleValueObserver")
        .setUpdater(doubleResult -> doubleResult.accept(10.1, Labels.empty()))
        .build();

    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactlyInAnyOrder(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now(), testClock.now(), Labels.empty(), 10)))),
            MetricData.createDoubleSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleSumObserver",
                "",
                "1",
                DoubleSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoublePointData.create(
                            testClock.now(), testClock.now(), Labels.empty(), 10.1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongUpDownSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ false,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now(), testClock.now(), Labels.empty(), -10)))),
            MetricData.createDoubleSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleUpDownSumObserver",
                "",
                "1",
                DoubleSumData.create(
                    /* isMonotonic= */ false,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoublePointData.create(
                            testClock.now(), testClock.now(), Labels.empty(), -10.1)))),
            MetricData.createLongGauge(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongValueObserver",
                "",
                "1",
                LongGaugeData.create(
                    Collections.singletonList(
                        LongPointData.create(0, testClock.now(), Labels.empty(), 10)))),
            MetricData.createDoubleGauge(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleValueObserver",
                "",
                "1",
                DoubleGaugeData.create(
                    Collections.singletonList(
                        DoublePointData.create(0, testClock.now(), Labels.empty(), 10.1)))));
  }

  @Test
  void collectAllAsyncInstruments_CumulativeCount() {
    registerViewForAllTypes(
        sdkMeterProvider, AggregatorFactory.count(AggregationTemporality.CUMULATIVE));
    sdkMeter
        .longSumObserverBuilder("testLongSumObserver")
        .setUpdater(longResult -> longResult.accept(10L, Labels.empty()))
        .build();
    sdkMeter
        .longUpDownSumObserverBuilder("testLongUpDownSumObserver")
        .setUpdater(longResult -> longResult.accept(-10L, Labels.empty()))
        .build();
    sdkMeter
        .longValueObserverBuilder("testLongValueObserver")
        .setUpdater(longResult -> longResult.accept(10L, Labels.empty()))
        .build();

    sdkMeter
        .doubleSumObserverBuilder("testDoubleSumObserver")
        .setUpdater(doubleResult -> doubleResult.accept(10.1, Labels.empty()))
        .build();
    sdkMeter
        .doubleUpDownSumObserverBuilder("testDoubleUpDownSumObserver")
        .setUpdater(doubleResult -> doubleResult.accept(-10.1, Labels.empty()))
        .build();
    sdkMeter
        .doubleValueObserverBuilder("testDoubleValueObserver")
        .setUpdater(doubleResult -> doubleResult.accept(10.1, Labels.empty()))
        .build();

    testClock.advanceNanos(50);

    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactlyInAnyOrder(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongUpDownSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleUpDownSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongValueObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleValueObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 50, testClock.now(), Labels.empty(), 1)))));

    testClock.advanceNanos(50);

    assertThat(sdkMeterProvider.collectAllMetrics())
        .containsExactlyInAnyOrder(
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 100, testClock.now(), Labels.empty(), 2)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 100, testClock.now(), Labels.empty(), 2)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongUpDownSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 100, testClock.now(), Labels.empty(), 2)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleUpDownSumObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 100, testClock.now(), Labels.empty(), 2)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testLongValueObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 100, testClock.now(), Labels.empty(), 2)))),
            MetricData.createLongSum(
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                "testDoubleValueObserver",
                "",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            testClock.now() - 100, testClock.now(), Labels.empty(), 2)))));
  }

  private static void registerViewForAllTypes(
      SdkMeterProvider meterProvider, AggregatorFactory factory) {
    for (InstrumentType instrumentType : InstrumentType.values()) {
      meterProvider.registerView(
          InstrumentSelector.builder().setInstrumentType(instrumentType).build(), factory);
    }
  }
}
