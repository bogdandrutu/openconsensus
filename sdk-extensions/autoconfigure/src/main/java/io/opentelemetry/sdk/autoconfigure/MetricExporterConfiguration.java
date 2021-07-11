/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.prometheus.PrometheusCollector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReaderBuilder;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.prometheus.client.exporter.HTTPServer;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import javax.annotation.Nullable;

final class MetricExporterConfiguration {

  static void configureExporter(
      String name, ConfigProperties config, SdkMeterProvider meterProvider) {
    switch (name) {
      case "otlp":
        configureOtlpMetrics(config, meterProvider);
        return;
      case "prometheus":
        configurePrometheusMetrics(config, meterProvider);
        return;
      case "logging":
        ClasspathUtil.checkClassExists(
            "io.opentelemetry.exporter.logging.LoggingMetricExporter",
            "Logging Metrics Exporter",
            "opentelemetry-exporter-logging");
        configureLoggingMetrics(config, meterProvider);
        return;
      default:
        return;
    }
  }

  private static void configureLoggingMetrics(
      ConfigProperties config, SdkMeterProvider meterProvider) {
    MetricExporter exporter = new LoggingMetricExporter();
    configureIntervalMetricReader(config, meterProvider, exporter);
  }

  // Visible for testing
  @Nullable
  static OtlpGrpcMetricExporter configureOtlpMetrics(
      ConfigProperties config, SdkMeterProvider meterProvider) {
    try {
      ClasspathUtil.checkClassExists(
          "io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter",
          "OTLP Metrics Exporter",
          "opentelemetry-exporter-otlp-metrics");
    } catch (ConfigurationException e) {
      // Squash this for now, until metrics are stable and included in the `exporter-otlp` artifact
      // by default,
      return null;
    }
    OtlpGrpcMetricExporterBuilder builder = OtlpGrpcMetricExporter.builder();

    String endpoint = config.getString("otel.exporter.otlp.metrics.endpoint");
    if (endpoint == null) {
      endpoint = config.getString("otel.exporter.otlp.endpoint");
    }
    if (endpoint != null) {
      builder.setEndpoint(endpoint);
    }

    config.getCommaSeparatedMap("otel.exporter.otlp.headers").forEach(builder::addHeader);

    Duration timeout = config.getDuration("otel.exporter.otlp.timeout");
    if (timeout != null) {
      builder.setTimeout(timeout);
    }

    OtlpGrpcMetricExporter exporter = builder.build();

    configureIntervalMetricReader(config, meterProvider, exporter);

    return exporter;
  }

  private static void configureIntervalMetricReader(
      ConfigProperties config, SdkMeterProvider meterProvider, MetricExporter exporter) {
    IntervalMetricReaderBuilder readerBuilder =
        IntervalMetricReader.builder()
            .setMetricProducers(Collections.singleton(meterProvider.newMetricProducer()))
            .setMetricExporter(exporter);
    Duration exportInterval = config.getDuration("otel.imr.export.interval");
    if (exportInterval != null) {
      readerBuilder.setExportIntervalMillis(exportInterval.toMillis());
    }
    IntervalMetricReader reader = readerBuilder.build().startAndRegisterGlobal();
    Runtime.getRuntime().addShutdownHook(new Thread(reader::shutdown));
  }

  private static void configurePrometheusMetrics(
      ConfigProperties config, SdkMeterProvider meterProvider) {
    ClasspathUtil.checkClassExists(
        "io.opentelemetry.exporter.prometheus.PrometheusCollector",
        "Prometheus Metrics Server",
        "opentelemetry-exporter-prometheus");
    PrometheusCollector.builder()
        .setMetricProducer(meterProvider.newMetricProducer())
        .buildAndRegister();
    Integer port = config.getInt("otel.exporter.prometheus.port");
    if (port == null) {
      port = 9464;
    }
    String host = config.getString("otel.exporter.prometheus.host");
    if (host == null) {
      host = "0.0.0.0";
    }
    final HTTPServer server;
    try {
      server = new HTTPServer(host, port, true);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create Prometheus server", e);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
  }

  private MetricExporterConfiguration() {}
}
