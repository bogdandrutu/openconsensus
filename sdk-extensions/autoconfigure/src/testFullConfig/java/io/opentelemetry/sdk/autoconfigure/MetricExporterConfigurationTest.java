/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MetricExporterConfigurationTest {
  // Timeout difficult to test using real exports so just check implementation detail here.
  @Test
  void configureOtlpTimeout() {
    OtlpGrpcMetricExporter exporter =
        MetricExporterConfiguration.configureOtlpMetrics(
            DefaultConfigProperties.createForTest(
                ImmutableMap.of(
                    "otel.exporter.otlp.timeout", "10ms",
                    "otel.imr.export.interval", "5s")),
            SdkMeterProvider.builder().build());
    try {
      assertThat(exporter)
          .isInstanceOfSatisfying(
              OtlpGrpcMetricExporter.class,
              otlp ->
                  assertThat(otlp)
                      .extracting("timeoutNanos")
                      .isEqualTo(TimeUnit.MILLISECONDS.toNanos(10L)));
    } finally {
      exporter.shutdown();
    }
  }
}
