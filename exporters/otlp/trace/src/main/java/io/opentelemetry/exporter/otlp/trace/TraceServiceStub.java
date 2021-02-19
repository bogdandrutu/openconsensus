/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.otlp.trace;

import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import java.io.InputStream;

// Copied from the generate grpc code.
final class TraceServiceStub extends io.grpc.stub.AbstractFutureStub<TraceServiceStub> {
  // Start from the official method and replace the marshaller for the request.
  private static final io.grpc.MethodDescriptor<
          TraceMarshaler.RequestMarshaler, ExportTraceServiceResponse>
      exportMethod =
          TraceServiceGrpc.getExportMethod().toBuilder(
                  new MethodDescriptor.Marshaller<TraceMarshaler.RequestMarshaler>() {
                    @Override
                    public InputStream stream(TraceMarshaler.RequestMarshaler value) {
                      return new MarshallerInputStream(value);
                    }

                    @Override
                    public TraceMarshaler.RequestMarshaler parse(InputStream stream) {
                      return null;
                    }
                  },
                  ProtoUtils.marshaller(ExportTraceServiceResponse.getDefaultInstance()))
              .build();

  /** Creates a new ListenableFuture-style stub that supports unary calls on the service */
  static TraceServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TraceServiceStub> factory = TraceServiceStub::new;
    return TraceServiceStub.newStub(factory, channel);
  }

  private TraceServiceStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
    super(channel, callOptions);
  }

  @java.lang.Override
  protected TraceServiceStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
    return new TraceServiceStub(channel, callOptions);
  }

  com.google.common.util.concurrent.ListenableFuture<ExportTraceServiceResponse> export(
      TraceMarshaler.RequestMarshaler request) {
    return io.grpc.stub.ClientCalls.futureUnaryCall(
        getChannel().newCall(exportMethod, getCallOptions()), request);
  }
}
