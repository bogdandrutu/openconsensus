/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class PrometheusTest {

  @Test
  void prometheusExporter() throws Exception {
    int port = 9464;
    // Just use prometheus standard port if it's available
    try (ServerSocket unused = new ServerSocket(port)) {
      // Port available
    } catch (IOException e) {
      // Otherwise use a random port. There's a small race if another process takes it before we
      // initialize. Consider adding retries to this test if it flakes, presumably it never will on
      // CI since there's no prometheus there blocking the well-known port.
      try (ServerSocket socket2 = new ServerSocket(0)) {
        port = socket2.getLocalPort();
      }
    }
    System.setProperty("otel.exporter.prometheus.host", "127.0.0.1");
    System.setProperty("otel.exporter.prometheus.port", String.valueOf(port));
    OpenTelemetrySdkAutoConfiguration.initialize();

    GlobalMeterProvider.get()
        .get("test")
        .gaugeBuilder("test")
        .ofLongs()
        .buildWithCallback(result -> result.observe(2, Attributes.empty()));

    WebClient client = WebClient.of("http://127.0.0.1:" + port);
    AggregatedHttpResponse response = client.get("/metrics").aggregate().join();
    assertThat(response.contentUtf8()).contains("test 2.0");
  }
}
