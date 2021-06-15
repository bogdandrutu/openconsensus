/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ConfigurableSpanExporterTest {

  @Test
  void configuration() {
    DefaultConfigProperties config =
        DefaultConfigProperties.createForTest(ImmutableMap.of("test.option", "true"));
    SpanExporter spanExporter = SpanExporterConfiguration.configureExporter("testExporter", config);

    assertThat(spanExporter)
        .isInstanceOf(TestConfigurableSpanExporterProvider.TestSpanExporter.class)
        .extracting("config")
        .isSameAs(config);
  }

  @Test
  void exporterNotFound() {
    assertThatThrownBy(
            () ->
                SpanExporterConfiguration.configureExporter(
                    "catExporter", DefaultConfigProperties.createForTest(Collections.emptyMap())))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("catExporter");
  }

  @Test
  void configureSpanProcessor_simpleSpanProcessor() {
    String exporterName = "logging";
    Map<String, String> propMap = Collections.singletonMap("otel.traces.exporter", exporterName);
    SpanExporter exporter = new LoggingSpanExporter();
    DefaultConfigProperties properties = DefaultConfigProperties.createForTest(propMap);

    assertThat(
            TracerProviderConfiguration.configureSpanProcessor(properties, exporter, exporterName))
        .isInstanceOf(SimpleSpanProcessor.class);
  }

  @Test
  void configureSpanProcessor_batchSpanProcessor() {
    String exporterName = "zipkin";
    Map<String, String> propMap = Collections.singletonMap("otel.traces.exporter", exporterName);
    SpanExporter exporter = ZipkinSpanExporter.builder().build();
    DefaultConfigProperties properties = DefaultConfigProperties.createForTest(propMap);

    assertThat(
            TracerProviderConfiguration.configureSpanProcessor(properties, exporter, exporterName))
        .isInstanceOf(BatchSpanProcessor.class);
  }
}
