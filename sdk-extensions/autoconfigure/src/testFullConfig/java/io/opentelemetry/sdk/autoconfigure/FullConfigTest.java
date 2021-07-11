/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.server.logging.LoggingService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracePropagator;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.InstrumentationLibraryMetrics;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("InterruptedExceptionSwallowed")
class FullConfigTest {

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
                          try {
                            RequestHeaders headers =
                                ServiceRequestContext.current().request().headers();
                            assertThat(headers.get("cat")).isEqualTo("meow");
                            assertThat(headers.get("dog")).isEqualTo("bark");
                          } catch (Throwable t) {
                            responseObserver.onError(t);
                            return;
                          }
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
                          try {
                            RequestHeaders headers =
                                ServiceRequestContext.current().request().headers();
                            assertThat(headers.get("cat")).isEqualTo("meow");
                            assertThat(headers.get("dog")).isEqualTo("bark");
                          } catch (Throwable t) {
                            responseObserver.onError(t);
                            return;
                          }
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
          sb.decorator(LoggingService.newDecorator());
        }
      };

  @BeforeEach
  void setUp() {
    otlpTraceRequests.clear();
    otlpMetricsRequests.clear();
  }

  @Test
  void configures() throws Exception {
    String endpoint = "http://localhost:" + server.httpPort();
    System.setProperty("otel.exporter.otlp.endpoint", endpoint);
    System.setProperty("otel.exporter.otlp.timeout", "10000");

    Collection<String> fields =
        GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator().fields();
    List<String> keys = new ArrayList<>();
    keys.addAll(W3CTraceContextPropagator.getInstance().fields());
    keys.addAll(W3CBaggagePropagator.getInstance().fields());
    keys.addAll(B3Propagator.injectingSingleHeader().fields());
    keys.addAll(B3Propagator.injectingMultiHeaders().fields());
    keys.addAll(JaegerPropagator.getInstance().fields());
    keys.addAll(OtTracePropagator.getInstance().fields());
    keys.addAll(AwsXrayPropagator.getInstance().fields());
    // Added by TestPropagatorProvider
    keys.add("test");
    assertThat(fields).containsExactlyInAnyOrderElementsOf(keys);

    GlobalOpenTelemetry.get()
        .getTracer("test")
        .spanBuilder("test")
        .startSpan()
        .setAttribute("cat", "meow")
        .setAttribute("dog", "bark")
        .end();

    await()
        .untilAsserted(
            () -> {
              assertThat(otlpTraceRequests).hasSize(1);

              // Not well defined how many metric exports would have happened by now, check that
              // any
              // did. The metrics will be BatchSpanProcessor metrics.
              assertThat(otlpMetricsRequests).isNotEmpty();
            });

    ExportTraceServiceRequest traceRequest = otlpTraceRequests.take();
    assertThat(traceRequest.getResourceSpans(0).getResource().getAttributesList())
        .contains(
            KeyValue.newBuilder()
                .setKey("service.name")
                .setValue(AnyValue.newBuilder().setStringValue("test").build())
                .build(),
            KeyValue.newBuilder()
                .setKey("cat")
                .setValue(AnyValue.newBuilder().setStringValue("meow").build())
                .build());
    io.opentelemetry.proto.trace.v1.Span span =
        traceRequest.getResourceSpans(0).getInstrumentationLibrarySpans(0).getSpans(0);
    // Dog dropped by attribute limit.
    assertThat(span.getAttributesList())
        .containsExactlyInAnyOrder(
            KeyValue.newBuilder()
                .setKey("configured")
                .setValue(AnyValue.newBuilder().setBoolValue(true).build())
                .build(),
            KeyValue.newBuilder()
                .setKey("cat")
                .setValue(AnyValue.newBuilder().setStringValue("meow").build())
                .build());

    ExportMetricsServiceRequest metricRequest = otlpMetricsRequests.take();
    assertThat(metricRequest.getResourceMetrics(0).getResource().getAttributesList())
        .contains(
            KeyValue.newBuilder()
                .setKey("service.name")
                .setValue(AnyValue.newBuilder().setStringValue("test").build())
                .build(),
            KeyValue.newBuilder()
                .setKey("cat")
                .setValue(AnyValue.newBuilder().setStringValue("meow").build())
                .build());
    for (ResourceMetrics resourceMetrics : metricRequest.getResourceMetricsList()) {
      for (InstrumentationLibraryMetrics instrumentationLibraryMetrics :
          resourceMetrics.getInstrumentationLibraryMetricsList()) {
        for (Metric metric : instrumentationLibraryMetrics.getMetricsList()) {
          assertThat(getFirstDataPointLabels(metric))
              .contains(
                  KeyValue.newBuilder()
                      .setKey("configured")
                      .setValue(AnyValue.newBuilder().setBoolValue(true).build())
                      .build());
        }
      }
    }
  }

  private static List<KeyValue> getFirstDataPointLabels(Metric metric) {
    switch (metric.getDataCase()) {
      case GAUGE:
        return metric.getGauge().getDataPoints(0).getAttributesList();
      case SUM:
        return metric.getSum().getDataPoints(0).getAttributesList();
      case HISTOGRAM:
        return metric.getHistogram().getDataPoints(0).getAttributesList();
      case SUMMARY:
        return metric.getSummary().getDataPoints(0).getAttributesList();
      default:
        throw new IllegalArgumentException(
            "Unrecognized metric data case: " + metric.getDataCase().name());
    }
  }
}
