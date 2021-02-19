/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.otlp.trace;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/** Exports spans using OTLP via gRPC, using OpenTelemetry's protobuf model. */
@ThreadSafe
public final class OtlpGrpcSpanExporter implements SpanExporter {

  private static final Logger logger = Logger.getLogger(OtlpGrpcSpanExporter.class.getName());
  private static final String EXPORTER_NAME = OtlpGrpcSpanExporter.class.getSimpleName();

  private static final Labels EXPORTER_NAME_LABELS = Labels.of("exporter", EXPORTER_NAME);
  private static final Labels EXPORT_SUCCESS_LABELS =
      Labels.of("exporter", EXPORTER_NAME, "success", "true");
  private static final Labels EXPORT_FAILURE_LABELS =
      Labels.of("exporter", EXPORTER_NAME, "success", "false");

  private final TraceServiceStub traceService;

  private final ManagedChannel managedChannel;
  private final long timeoutNanos;
  private final BoundLongCounter spansSeen;
  private final BoundLongCounter spansExportedSuccess;
  private final BoundLongCounter spansExportedFailure;

  /**
   * Creates a new OTLP gRPC Span Reporter with the given name, using the given channel.
   *
   * @param channel the channel to use when communicating with the OpenTelemetry Collector.
   * @param timeoutNanos max waiting time for the collector to process each span batch. When set to
   *     0 or to a negative value, the exporter will wait indefinitely.
   */
  OtlpGrpcSpanExporter(ManagedChannel channel, long timeoutNanos) {
    Meter meter = GlobalMetricsProvider.getMeter("io.opentelemetry.exporters.otlp");
    this.spansSeen =
        meter.longCounterBuilder("spansSeenByExporter").build().bind(EXPORTER_NAME_LABELS);
    LongCounter spansExportedCounter = meter.longCounterBuilder("spansExportedByExporter").build();
    this.spansExportedSuccess = spansExportedCounter.bind(EXPORT_SUCCESS_LABELS);
    this.spansExportedFailure = spansExportedCounter.bind(EXPORT_FAILURE_LABELS);
    this.managedChannel = channel;
    this.timeoutNanos = timeoutNanos;

    this.traceService = TraceServiceStub.newStub(channel);
  }

  /**
   * Submits all the given spans in a single batch to the OpenTelemetry collector.
   *
   * @param spans the list of sampled Spans to be exported.
   * @return the result of the operation
   */
  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    TraceMarshaler.RequestMarshaler request = TraceMarshaler.RequestMarshaler.create(spans);

    final CompletableResultCode result = new CompletableResultCode();

    TraceServiceStub exporter;
    if (timeoutNanos > 0) {
      exporter = traceService.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS);
    } else {
      exporter = traceService;
    }

    Futures.addCallback(
        exporter.export(request),
        new FutureCallback<ExportTraceServiceResponse>() {
          @Override
          public void onSuccess(@Nullable ExportTraceServiceResponse response) {
            spansExportedSuccess.add(spans.size());
            result.succeed();
          }

          @Override
          public void onFailure(Throwable t) {
            spansExportedFailure.add(spans.size());
            logger.log(Level.WARNING, "Failed to export spans. Error message: " + t.getMessage());
            logger.log(Level.FINEST, "Failed to export spans. Details follow: " + t);
            result.fail();
          }
        },
        MoreExecutors.directExecutor());
    return result;
  }

  /**
   * The OTLP exporter does not batch spans, so this method will immediately return with success.
   *
   * @return always Success
   */
  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  /**
   * Returns a new builder instance for this exporter.
   *
   * @return a new builder instance for this exporter.
   */
  public static OtlpGrpcSpanExporterBuilder builder() {
    return new OtlpGrpcSpanExporterBuilder();
  }

  /**
   * Returns a new {@link OtlpGrpcSpanExporter} reading the configuration values from the
   * environment and from system properties. System properties override values defined in the
   * environment. If a configuration value is missing, it uses the default value.
   *
   * @return a new {@link OtlpGrpcSpanExporter} instance.
   */
  public static OtlpGrpcSpanExporter getDefault() {
    return builder().build();
  }

  /**
   * Initiates an orderly shutdown in which preexisting calls continue but new calls are immediately
   * cancelled.
   */
  @Override
  public CompletableResultCode shutdown() {
    final CompletableResultCode result = new CompletableResultCode();
    managedChannel.notifyWhenStateChanged(ConnectivityState.SHUTDOWN, result::succeed);
    managedChannel.shutdown();
    this.spansSeen.unbind();
    this.spansExportedSuccess.unbind();
    this.spansExportedFailure.unbind();
    return result;
  }

  // Visible for testing
  long getTimeoutNanos() {
    return timeoutNanos;
  }
}
