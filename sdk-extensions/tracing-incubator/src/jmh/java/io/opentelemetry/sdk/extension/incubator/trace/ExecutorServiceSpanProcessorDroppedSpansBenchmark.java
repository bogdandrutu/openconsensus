/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.trace;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

public class ExecutorServiceSpanProcessorDroppedSpansBenchmark {

  @State(Scope.Benchmark)
  public static class BenchmarkState {
    private MetricProducer collector;
    private ExecutorServiceSpanProcessor processor;
    private Tracer tracer;
    private double dropRatio;
    private long exportedSpans;
    private long droppedSpans;
    private int numThreads;

    @Setup(Level.Iteration)
    public final void setup() {
      final SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();
      collector = sdkMeterProvider.newMetricProducer();
      SpanExporter exporter = new DelayingSpanExporter(0);
      ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
      processor = ExecutorServiceSpanProcessor.builder(exporter, executor, true).build();

      tracer = SdkTracerProvider.builder().build().get("benchmarkTracer");
    }

    @TearDown(Level.Iteration)
    public final void recordMetrics() {
      BatchSpanProcessorMetrics metrics =
          new BatchSpanProcessorMetrics(collector.collectAllMetrics(), numThreads);
      dropRatio = metrics.dropRatio();
      exportedSpans = metrics.exportedSpans();
      droppedSpans = metrics.droppedSpans();
    }

    @TearDown(Level.Iteration)
    public final void tearDown() {
      processor.shutdown();
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

    public double dropRatio() {
      return benchmarkState.dropRatio;
    }

    public long exportedSpans() {
      return benchmarkState.exportedSpans;
    }

    public long droppedSpans() {
      return benchmarkState.droppedSpans;
    }
  }

  /** Export spans through {@link ExecutorServiceSpanProcessor}. */
  @Benchmark
  @Fork(1)
  @Threads(5)
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 5, time = 20)
  @BenchmarkMode(Mode.Throughput)
  public void export(
      BenchmarkState benchmarkState, @SuppressWarnings("unused") ThreadState threadState) {
    benchmarkState.numThreads = 5;
    benchmarkState.processor.onEnd(
        (ReadableSpan) benchmarkState.tracer.spanBuilder("span").startSpan());
  }
}
