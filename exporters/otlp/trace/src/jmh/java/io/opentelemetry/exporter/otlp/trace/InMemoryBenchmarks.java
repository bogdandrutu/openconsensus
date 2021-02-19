/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.otlp.trace;

import com.google.common.io.Closer;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.sdk.extension.otproto.SpanAdapter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class InMemoryBenchmarks {

  private final Closer closer = Closer.create();
  TraceServiceStub customStub;
  TraceServiceGrpc.TraceServiceFutureStub protoStub;

  @Setup
  public void setup() throws IOException {
    String serverName = InProcessServerBuilder.generateName();
    ManagedChannel inProcessChannel =
        InProcessChannelBuilder.forName(serverName).directExecutor().build();

    Server server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new FakeCollector())
            .build()
            .start();

    customStub = TraceServiceStub.newStub(inProcessChannel);
    protoStub = TraceServiceGrpc.newFutureStub(inProcessChannel);

    closer.register(server::shutdownNow);
    closer.register(inProcessChannel::shutdownNow);
  }

  @TearDown
  public void tearDown() throws Exception {
    closer.close();
  }

  @Benchmark
  @Threads(1)
  public ExportTraceServiceResponse marshalProto() throws ExecutionException, InterruptedException {
    ExportTraceServiceRequest protoRequest =
        ExportTraceServiceRequest.newBuilder()
            .addAllResourceSpans(
                SpanAdapter.toProtoResourceSpans(RequestMarshalState.generateSpanData(16)))
            .build();
    return protoStub.export(protoRequest).get();
  }

  @Benchmark
  @Threads(1)
  public ExportTraceServiceResponse marshalCustom()
      throws ExecutionException, InterruptedException {
    TraceMarshaler.RequestMarshaler requestMarshaler =
        TraceMarshaler.RequestMarshaler.create(RequestMarshalState.generateSpanData(16));
    return customStub.export(requestMarshaler).get();
  }

  @Benchmark
  @Threads(1)
  public ExportTraceServiceResponse marshalCustomViaProto()
      throws ExecutionException, InterruptedException, IOException {
    ExportTraceServiceRequest protoRequest =
        TraceMarshaler.RequestMarshaler.create(RequestMarshalState.generateSpanData(16))
            .toRequest();
    return protoStub.export(protoRequest).get();
  }

  private static final class FakeCollector extends TraceServiceGrpc.TraceServiceImplBase {
    @Override
    public void export(
        ExportTraceServiceRequest request,
        StreamObserver<ExportTraceServiceResponse> responseObserver) {
      responseObserver.onNext(ExportTraceServiceResponse.newBuilder().build());
      responseObserver.onCompleted();
    }
  }
}
