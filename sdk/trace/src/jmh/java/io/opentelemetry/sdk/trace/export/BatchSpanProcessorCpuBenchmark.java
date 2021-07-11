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
import java.util.concurrent.locks.LockSupport;
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

/*
 * Run this along with a profiler to measure the CPU usage of BatchSpanProcessor's exporter thread.
 */
public class BatchSpanProcessorCpuBenchmark {
  @State(Scope.Benchmark)
  public static class BenchmarkState {
    private MetricProducer collector;
    private BatchSpanProcessor processor;
    private Tracer tracer;
    private int numThreads = 1;

    @Param({"1"})
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
    }

    @TearDown(Level.Iteration)
    public final void tearDown() {
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

  private static void doWork(BenchmarkState benchmarkState) {
    benchmarkState.processor.onEnd(
        (ReadableSpan) benchmarkState.tracer.spanBuilder("span").startSpan());
    // This sleep is essential to maintain a steady state of the benchmark run by generating 10k
    // spans per second per thread. Without this JMH outer loop consumes as much CPU as possible
    // making comparing different processor versions difficult.
    // Note that time spent outside of the sleep is negligible allowing this sleep to control
    // span generation rate. Here we get 1 / 100_000 = 10K spans generated per second.
    LockSupport.parkNanos(100_000);
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
    doWork(benchmarkState);
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
    doWork(benchmarkState);
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
    doWork(benchmarkState);
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
    doWork(benchmarkState);
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
    doWork(benchmarkState);
  }
}
