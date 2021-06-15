/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.LongSumData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("InterruptedExceptionSwallowed")
class EndpointConfigurationTest {

  private static final BlockingQueue<ExportTraceServiceRequest> otlpTraceRequests =
      new LinkedBlockingDeque<>();
  private static final BlockingQueue<ExportMetricsServiceRequest> otlpMetricsRequests =
      new LinkedBlockingDeque<>();

  @RegisterExtension
  public static final ServerExtension server =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
          sb.service(
              GrpcService.builder()
                  // OTLP spans
                  .addService(
                      new TraceServiceGrpc.TraceServiceImplBase() {
                        @Override
                        public void export(
                            ExportTraceServiceRequest request,
                            StreamObserver<ExportTraceServiceResponse> responseObserver) {
                          otlpTraceRequests.add(request);
                          responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
                          responseObserver.onCompleted();
                        }
                      })
                  // OTLP metrics
                  .addService(
                      new MetricsServiceGrpc.MetricsServiceImplBase() {
                        @Override
                        public void export(
                            ExportMetricsServiceRequest request,
                            StreamObserver<ExportMetricsServiceResponse> responseObserver) {
                          if (request.getResourceMetricsCount() > 0) {
                            otlpMetricsRequests.add(request);
                          }
                          responseObserver.onNext(
                              ExportMetricsServiceResponse.getDefaultInstance());
                          responseObserver.onCompleted();
                        }
                      })
                  .useBlockingTaskExecutor(true)
                  .build());
        }
      };

  @BeforeEach
  void setUp() {
    otlpTraceRequests.clear();
    otlpMetricsRequests.clear();
    GlobalOpenTelemetry.resetForTest();
  }

  @AfterEach
  public void tearDown() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  public void configure() {
    SpanExporter spanExporter =
        SpanExporterConfiguration.configureExporter(
            "otlp",
            DefaultConfigProperties.createForTest(
                ImmutableMap.of(
                    "otel.exporter.otlp.traces.endpoint",
                    "http://localhost:" + server.httpPort())));

    OtlpGrpcMetricExporter metricExporter =
        MetricExporterConfiguration.configureOtlpMetrics(
            DefaultConfigProperties.createForTest(
                ImmutableMap.of(
                    "otel.exporter.otlp.metrics.endpoint",
                    "http://localhost:" + server.httpPort())),
            SdkMeterProvider.builder().build());

    spanExporter.export(
        Lists.newArrayList(
            TestSpanData.builder()
                .setHasEnded(true)
                .setName("name")
                .setStartEpochNanos(MILLISECONDS.toNanos(System.currentTimeMillis()))
                .setEndEpochNanos(MILLISECONDS.toNanos(System.currentTimeMillis()))
                .setKind(SpanKind.SERVER)
                .setStatus(StatusData.error())
                .setTotalRecordedEvents(0)
                .setTotalRecordedLinks(0)
                .build()));

    metricExporter.export(
        Lists.newArrayList(
            MetricData.createLongSum(
                Resource.empty(),
                InstrumentationLibraryInfo.empty(),
                "metric_name",
                "metric_description",
                "ms",
                LongSumData.create(
                    false,
                    AggregationTemporality.CUMULATIVE,
                    Collections.singletonList(
                        LongPointData.create(
                            MILLISECONDS.toNanos(System.currentTimeMillis()),
                            MILLISECONDS.toNanos(System.currentTimeMillis()),
                            Labels.of("key", "value"),
                            10))))));

    await()
        .untilAsserted(
            () -> {
              assertThat(otlpTraceRequests).hasSize(1);
              assertThat(otlpMetricsRequests).hasSize(1);
            });
  }

  @Test
  void configuresGlobal() {
    // Point "otel.exporter.otlp.endpoint" to wrong endpoint
    System.setProperty("otel.exporter.otlp.endpoint", "http://localhost/wrong");

    // Point "otel.exporter.otlp.traces.endpoint" to correct endpoint
    System.setProperty(
        "otel.exporter.otlp.traces.endpoint", "http://localhost:" + server.httpPort());

    // Point "otel.exporter.otlp.metrics.endpoint" to correct endpoint
    System.setProperty(
        "otel.exporter.otlp.metrics.endpoint", "http://localhost:" + server.httpPort());

    System.setProperty("otel.exporter.otlp.timeout", "10000");

    GlobalOpenTelemetry.get().getTracer("test").spanBuilder("test").startSpan().end();

    await()
        .untilAsserted(
            () -> {
              assertThat(otlpTraceRequests).hasSize(1);

              // Not well defined how many metric exports would have happened by now, check that
              // any
              // did. The metrics will be BatchSpanProcessor metrics.
              assertThat(otlpMetricsRequests).isNotEmpty();
            });
  }
}
