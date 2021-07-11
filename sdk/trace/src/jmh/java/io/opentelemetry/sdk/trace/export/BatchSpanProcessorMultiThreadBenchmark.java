/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.export;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
public class BatchSpanProcessorMultiThreadBenchmark {

  @State(Scope.Benchmark)
  public static class BenchmarkState {
    private MetricProducer collector;
    private BatchSpanProcessor processor;
    private Tracer tracer;
    private int numThreads = 1;

    @Param({"0"})
    private int delayMs;

    private long exportedSpans;
    private long droppedSpans;

    @Setup(Level.Iteration)
    public final void setup() {
      final SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();
      collector = sdkMeterProvider.newMetricProducer();
      SpanExporter exporter = new DelayingSpanExporter(delayMs);
      processor = BatchSpanProcessor.builder(exporter).build();
      tracer =
          SdkTracerProvider.builder().addSpanProcessor(processor).build().get("benchmarkTracer");
    }

    @TearDown(Level.Iteration)
    public final void recordMetrics() {
      BatchSpanProcessorMetrics metrics =
          new BatchSpanProcessorMetrics(collector.collectAllMetrics(), numThreads);
      exportedSpans = metrics.exportedSpans();
      droppedSpans = metrics.droppedSpans();
      processor.shutdown().join(10, TimeUnit.SECONDS);
    }
  }

  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.OPERATIONS)
  public static class ThreadState {
    BenchmarkState benchmarkState;

    @TearDown(Level.Iteration)
    public final void recordMetrics(BenchmarkState benchmarkState) {
      this.benchmarkState = benchmarkState;
    }

    public long exportedSpans() {
      return benchmarkState.exportedSpans;
    }

    public long droppedSpans() {
      return benchmarkState.droppedSpans;
    }
  }

  @Benchmark
  @Fork(1)
  @Threads(1)
  @Warmup(iterations = 1, time = 1)
  @Measurement(iterations = 5, time = 5)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void export_01Thread(
      BenchmarkState benchmarkState, @SuppressWarnings("unused") ThreadState threadState) {
    benchmarkState.numThreads = 1;
    benchmarkState.processor.onEnd(
        (ReadableSpan) benchmarkState.tracer.spanBuilder("span").startSpan());
  }

  @Benchmark
  @Fork(1)
  @Threads(2)
  @Warmup(iterations = 1, time = 1)
  @Measurement(iterations = 5, time = 5)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void export_02Thread(
      BenchmarkState benchmarkState, @SuppressWarnings("unused") ThreadState threadState) {
    benchmarkState.numThreads = 2;
    benchmarkState.processor.onEnd(
        (ReadableSpan) benchmarkState.tracer.spanBuilder("span").startSpan());
  }

  @Benchmark
  @Fork(1)
  @Threads(5)
  @Warmup(iterations = 1, time = 1)
  @Measurement(iterations = 5, time = 5)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void export_05Thread(
      BenchmarkState benchmarkState, @SuppressWarnings("unused") ThreadState threadState) {
    benchmarkState.numThreads = 5;
    benchmarkState.processor.onEnd(
        (ReadableSpan) benchmarkState.tracer.spanBuilder("span").startSpan());
  }

  @Benchmark
  @Fork(1)
  @Threads(10)
  @Warmup(iterations = 1, time = 1)
  @Measurement(iterations = 5, time = 5)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void export_10Thread(
      BenchmarkState benchmarkState, @SuppressWarnings("unused") ThreadState threadState) {
    benchmarkState.numThreads = 10;
    benchmarkState.processor.onEnd(
        (ReadableSpan) benchmarkState.tracer.spanBuilder("span").startSpan());
  }

  @Benchmark
  @Fork(1)
  @Threads(20)
  @Warmup(iterations = 1, time = 1)
  @Measurement(iterations = 5, time = 5)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void export_20Thread(
      BenchmarkState benchmarkState, @SuppressWarnings("unused") ThreadState threadState) {
    benchmarkState.numThreads = 20;
    benchmarkState.processor.onEnd(
        (ReadableSpan) benchmarkState.tracer.spanBuilder("span").startSpan());
  }
}
