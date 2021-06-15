/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SpanExporterConfigurationTest {

  // Timeout difficult to test using real exports so just check implementation detail here.
  @Test
  void configureOtlpTimeout() {
    SpanExporter exporter =
        SpanExporterConfiguration.configureExporter(
            "otlp",
            DefaultConfigProperties.createForTest(
                Collections.singletonMap("otel.exporter.otlp.timeout", "10")));
    try {
      assertThat(exporter)
          .isInstanceOfSatisfying(
              OtlpGrpcSpanExporter.class,
              otlp ->
                  assertThat(otlp)
                      .extracting("timeoutNanos")
                      .isEqualTo(TimeUnit.MILLISECONDS.toNanos(10L)));
    } finally {
      exporter.shutdown();
    }
  }

  // Timeout difficult to test using real exports so just check implementation detail here.
  @Test
  void configureJaegerTimeout() {
    SpanExporter exporter =
        SpanExporterConfiguration.configureExporter(
            "jaeger",
            DefaultConfigProperties.createForTest(
                Collections.singletonMap("otel.exporter.jaeger.timeout", "10")));
    try {
      assertThat(exporter)
          .isInstanceOfSatisfying(
              JaegerGrpcSpanExporter.class,
              jaeger ->
                  assertThat(jaeger)
                      .extracting("timeoutNanos")
                      .isEqualTo(TimeUnit.MILLISECONDS.toNanos(10L)));
    } finally {
      exporter.shutdown();
    }
  }

  // Timeout difficult to test using real exports so just check that things don't blow up.
  @Test
  void configureZipkinTimeout() {
    SpanExporter exporter =
        SpanExporterConfiguration.configureExporter(
            "zipkin",
            DefaultConfigProperties.createForTest(
                Collections.singletonMap("otel.exporter.zipkin.timeout", "5s")));
    try {
      assertThat(exporter).isNotNull();
    } finally {
      exporter.shutdown();
    }
  }
}
