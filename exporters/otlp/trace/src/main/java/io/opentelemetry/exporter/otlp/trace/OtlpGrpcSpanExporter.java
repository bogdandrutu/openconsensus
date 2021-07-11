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
import io.grpc.Status;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.internal.SpanAdapter;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc.TraceServiceFutureStub;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.internal.ThrottlingLogger;
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
  private static final AttributeKey<String> EXPORTER_KEY = AttributeKey.stringKey("exporter");
  private static final AttributeKey<String> SUCCESS_KEY = AttributeKey.stringKey("success");
  private static final String EXPORTER_NAME = OtlpGrpcSpanExporter.class.getSimpleName();
  private static final Attributes EXPORTER_NAME_Attributes =
      Attributes.of(EXPORTER_KEY, EXPORTER_NAME);
  private static final Attributes EXPORT_SUCCESS_Attributes =
      Attributes.of(EXPORTER_KEY, EXPORTER_NAME, SUCCESS_KEY, "true");
  private static final Attributes EXPORT_FAILURE_Attributes =
      Attributes.of(EXPORTER_KEY, EXPORTER_NAME, SUCCESS_KEY, "false");

  private final ThrottlingLogger logger =
      new ThrottlingLogger(Logger.getLogger(OtlpGrpcSpanExporter.class.getName()));

  private final TraceServiceFutureStub traceService;

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
    // TODO: telemetry schema version.
    Meter meter = GlobalMeterProvider.get().meterBuilder("io.opentelemetry.exporters.otlp").build();
    this.spansSeen =
        meter.counterBuilder("spansSeenByExporter").build().bind(EXPORTER_NAME_Attributes);
    LongCounter spansExportedCounter = meter.counterBuilder("spansExportedByExporter").build();
    this.spansExportedSuccess = spansExportedCounter.bind(EXPORT_SUCCESS_Attributes);
    this.spansExportedFailure = spansExportedCounter.bind(EXPORT_FAILURE_Attributes);
    this.managedChannel = channel;
    this.timeoutNanos = timeoutNanos;

    this.traceService = TraceServiceGrpc.newFutureStub(channel);
  }

  /**
   * Submits all the given spans in a single batch to the OpenTelemetry collector.
   *
   * @param spans the list of sampled Spans to be exported.
   * @return the result of the operation
   */
  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    spansSeen.add(spans.size());
    ExportTraceServiceRequest exportTraceServiceRequest =
        ExportTraceServiceRequest.newBuilder()
            .addAllResourceSpans(SpanAdapter.toProtoResourceSpans(spans))
            .build();

    final CompletableResultCode result = new CompletableResultCode();

    TraceServiceFutureStub exporter;
    if (timeoutNanos > 0) {
      exporter = traceService.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS);
    } else {
      exporter = traceService;
    }

    Futures.addCallback(
        exporter.export(exportTraceServiceRequest),
        new FutureCallback<ExportTraceServiceResponse>() {
          @Override
          public void onSuccess(@Nullable ExportTraceServiceResponse response) {
            spansExportedSuccess.add(spans.size());
            result.succeed();
          }

          @Override
          public void onFailure(Throwable t) {
            spansExportedFailure.add(spans.size());
            Status status = Status.fromThrowable(t);
            switch (status.getCode()) {
              case UNIMPLEMENTED:
                logger.log(
                    Level.SEVERE,
                    "Failed to export spans. Server responded with UNIMPLEMENTED. "
                        + "This usually means that your collector is not configured with an otlp "
                        + "receiver in the \"pipelines\" section of the configuration. "
                        + "Full error message: "
                        + t.getMessage());
                break;
              case UNAVAILABLE:
                logger.log(
                    Level.SEVERE,
                    "Failed to export spans. Server is UNAVAILABLE. "
                        + "Make sure your collector is running and reachable from this network. "
                        + "Full error message:"
                        + t.getMessage());
                break;
              default:
                logger.log(
                    Level.WARNING, "Failed to export spans. Error message: " + t.getMessage());
                break;
            }
            if (logger.isLoggable(Level.FINEST)) {
              logger.log(Level.FINEST, "Failed to export spans. Details follow: " + t);
            }
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
    if (managedChannel.isShutdown()) {
      return result.succeed();
    }
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
