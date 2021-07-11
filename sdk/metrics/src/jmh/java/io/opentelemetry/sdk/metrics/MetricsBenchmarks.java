/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.ThreadParams;

@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class MetricsBenchmarks {

  @State(Scope.Thread)
  public static class ThreadState {

    @Param TestSdk sdk;

    @Param MetricsTestOperationBuilder opBuilder;

    MetricsTestOperationBuilder.Operation op;
    final Attributes sharedLabelSet = Attributes.builder().put("KEY", "VALUE").build();
    Attributes threadUniqueLabelSet;

    @Setup
    public void setup(ThreadParams threadParams) {
      Meter meter = sdk.getMeter();
      op = opBuilder.build(meter);
      threadUniqueLabelSet =
          Attributes.builder().put("KEY", String.valueOf(threadParams.getThreadIndex())).build();
    }
  }

  @Benchmark
  @Threads(1)
  public void oneThread(ThreadState threadState) {
    threadState.op.perform(threadState.sharedLabelSet);
  }

  @Benchmark
  @Threads(1)
  public void oneThreadBound(ThreadState threadState) {
    threadState.op.performBound();
  }

  @Benchmark
  @Threads(8)
  public void eightThreadsCommonLabelSet(ThreadState threadState) {
    threadState.op.perform(threadState.sharedLabelSet);
  }

  @Benchmark
  @Threads(8)
  public void eightThreadsSeparateLabelSets(ThreadState threadState) {
    threadState.op.perform(threadState.threadUniqueLabelSet);
  }

  @Benchmark
  @Threads(8)
  public void eightThreadsBound(ThreadState threadState) {
    threadState.op.performBound();
  }
}
