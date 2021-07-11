/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.logging;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramData;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramPointData;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.DoubleSumData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.LongSumData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the {@link LoggingMetricExporter}. */
class LoggingMetricExporterTest {

  LoggingMetricExporter exporter;

  private static final AttributeKey<String> A = stringKey("a");
  private static final AttributeKey<String> C = stringKey("c");
  private static final AttributeKey<String> X = stringKey("x");
  private static final AttributeKey<String> Z = stringKey("z");

  @BeforeEach
  void setUp() {
    exporter = new LoggingMetricExporter();
  }

  @AfterEach
  void tearDown() {
    exporter.shutdown();
  }

  @Test
  void testExport() {
    long nowEpochNanos = System.currentTimeMillis() * 1000 * 1000;
    Resource resource = Resource.create(Attributes.of(stringKey("host"), "localhost"));
    InstrumentationLibraryInfo instrumentationLibraryInfo =
        InstrumentationLibraryInfo.create("manualInstrumentation", "1.0");
    exporter.export(
        Arrays.asList(
            MetricData.createDoubleHistogram(
                resource,
                instrumentationLibraryInfo,
                "measureOne",
                "A summarized test measure",
                "ms",
                DoubleHistogramData.create(
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoubleHistogramPointData.create(
                            nowEpochNanos,
                            nowEpochNanos + 245,
                            Attributes.of(A, "b", C, "d"),
                            1010,
                            Arrays.asList(0d),
                            Arrays.asList(0L, 2L))))),
            MetricData.createLongSum(
                resource,
                instrumentationLibraryInfo,
                "counterOne",
                "A simple counter",
                "one",
                LongSumData.create(
                    true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            nowEpochNanos,
                            nowEpochNanos + 245,
                            Attributes.of(Z, "y", X, "w"),
                            1010)))),
            MetricData.createDoubleSum(
                resource,
                instrumentationLibraryInfo,
                "observedValue",
                "an observer gauge",
                "kb",
                DoubleSumData.create(
                    true,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        DoublePointData.create(
                            nowEpochNanos,
                            nowEpochNanos + 245,
                            Attributes.of(A, "2", C, "4"),
                            33.7767))))));
  }

  @Test
  void testFlush() {
    final AtomicBoolean flushed = new AtomicBoolean(false);
    Logger.getLogger(LoggingMetricExporter.class.getName())
        .addHandler(
            new StreamHandler(new PrintStream(new ByteArrayOutputStream()), new SimpleFormatter()) {
              @Override
              public synchronized void flush() {
                flushed.set(true);
              }
            });
    exporter.flush();
    assertThat(flushed.get()).isTrue();
  }
}
