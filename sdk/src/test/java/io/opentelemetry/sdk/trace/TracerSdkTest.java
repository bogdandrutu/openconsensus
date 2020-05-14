/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.sdk.trace;

import static com.google.common.truth.Truth.assertThat;

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.scope.DefaultScopeManager;
import io.opentelemetry.scope.Scope;
import io.opentelemetry.scope.ScopeManager;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.trace.StressTestRunner.OperationUpdater;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpansProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link TracerSdk}. */
@RunWith(JUnit4.class)
// Need to suppress warnings for MustBeClosed because Android 14 does not support
// try-with-resources.
// TODO (trask) delete tests here that were designed to test Tracer methods that are now removed
@SuppressWarnings("MustBeClosedChecker")
public class TracerSdkTest {

  private static final String SPAN_NAME = "span_name";
  private static final String INSTRUMENTATION_LIBRARY_NAME =
      "io.opentelemetry.sdk.trace.TracerSdkTest";
  private static final String INSTRUMENTATION_LIBRARY_VERSION = "semver:0.2.0";
  private static final InstrumentationLibraryInfo instrumentationLibraryInfo =
      InstrumentationLibraryInfo.create(
          INSTRUMENTATION_LIBRARY_NAME, INSTRUMENTATION_LIBRARY_VERSION);
  @Mock private Span span;

  private final ScopeManager scopeManager = DefaultScopeManager.getInstance();
  private final TracerSdk tracer =
      TracerSdkProvider.builder()
          .build()
          .get(INSTRUMENTATION_LIBRARY_NAME, INSTRUMENTATION_LIBRARY_VERSION);

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void defaultGetCurrentSpan() {
    assertThat(scopeManager.getSpan()).isInstanceOf(DefaultSpan.class);
  }

  @Test
  public void defaultSpanBuilder() {
    assertThat(tracer.spanBuilder(SPAN_NAME)).isInstanceOf(SpanBuilderSdk.class);
  }

  @Test
  public void getCurrentSpan() {
    assertThat(scopeManager.getSpan()).isInstanceOf(DefaultSpan.class);
    try (Scope ignored = scopeManager.withSpan(span)) {
      assertThat(scopeManager.getSpan()).isSameInstanceAs(span);
    }
    assertThat(scopeManager.getSpan()).isInstanceOf(DefaultSpan.class);
  }

  @Test
  public void withSpan_NullSpan() {
    assertThat(scopeManager.getSpan()).isInstanceOf(DefaultSpan.class);
    try (Scope ignored = scopeManager.withSpan(null)) {
      assertThat(scopeManager.getSpan()).isInstanceOf(DefaultSpan.class);
    }
    assertThat(scopeManager.getSpan()).isInstanceOf(DefaultSpan.class);
  }

  @Test
  public void getCurrentSpan_WithSpan() {
    assertThat(scopeManager.getSpan()).isInstanceOf(DefaultSpan.class);
    try (Scope ignored = scopeManager.withSpan(span)) {
      assertThat(scopeManager.getSpan()).isSameInstanceAs(span);
    }
    assertThat(scopeManager.getSpan()).isInstanceOf(DefaultSpan.class);
  }

  @Test
  public void getInstrumentationLibraryInfo() {
    assertThat(tracer.getInstrumentationLibraryInfo()).isEqualTo(instrumentationLibraryInfo);
  }

  @Test
  public void propagatesInstrumentationLibraryInfoToSpan() {
    ReadableSpan readableSpan = (ReadableSpan) tracer.spanBuilder("spanName").startSpan();
    assertThat(readableSpan.getInstrumentationLibraryInfo()).isEqualTo(instrumentationLibraryInfo);
  }

  @Test
  public void stressTest() {
    CountingSpanProcessor spanProcessor = new CountingSpanProcessor();
    TracerSdkProvider tracerSdkProvider = TracerSdkProvider.builder().build();
    tracerSdkProvider.addSpanProcessor(spanProcessor);
    TracerSdk tracer =
        tracerSdkProvider.get(INSTRUMENTATION_LIBRARY_NAME, INSTRUMENTATION_LIBRARY_VERSION);

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setTracer(tracer).setSpanProcessor(spanProcessor);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(2_000, 1, new SimpleSpanOperation(tracer)));
    }

    stressTestBuilder.build().run();
    assertThat(spanProcessor.numberOfSpansFinished.get()).isEqualTo(8_000);
    assertThat(spanProcessor.numberOfSpansStarted.get()).isEqualTo(8_000);
  }

  @Test
  public void stressTest_withBatchSpanProcessor() {
    CountingSpanExporter countingSpanExporter = new CountingSpanExporter();
    SpanProcessor spanProcessor = BatchSpansProcessor.create(countingSpanExporter);
    TracerSdkProvider tracerSdkProvider = TracerSdkProvider.builder().build();
    tracerSdkProvider.addSpanProcessor(spanProcessor);
    TracerSdk tracer =
        tracerSdkProvider.get(INSTRUMENTATION_LIBRARY_NAME, INSTRUMENTATION_LIBRARY_VERSION);

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setTracer(tracer).setSpanProcessor(spanProcessor);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(2_000, 1, new SimpleSpanOperation(tracer)));
    }

    stressTestBuilder.build().run();
    assertThat(countingSpanExporter.numberOfSpansExported.get()).isEqualTo(8_000);
  }

  private static class CountingSpanProcessor implements SpanProcessor {
    private final AtomicLong numberOfSpansStarted = new AtomicLong();
    private final AtomicLong numberOfSpansFinished = new AtomicLong();

    @Override
    public void onStart(ReadableSpan span) {
      numberOfSpansStarted.incrementAndGet();
    }

    @Override
    public boolean isStartRequired() {
      return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
      numberOfSpansFinished.incrementAndGet();
    }

    @Override
    public boolean isEndRequired() {
      return true;
    }

    @Override
    public void shutdown() {
      // no-op
    }

    @Override
    public void forceFlush() {
      // no-op
    }
  }

  private static class SimpleSpanOperation implements OperationUpdater {
    private final TracerSdk tracer;

    public SimpleSpanOperation(TracerSdk tracer) {
      this.tracer = tracer;
    }

    @Override
    public void update() {
      Span span = tracer.spanBuilder("testSpan").startSpan();
      span.setAttribute("testAttribute", AttributeValue.stringAttributeValue("testValue"));
      span.end();
    }
  }

  private static class CountingSpanExporter implements SpanExporter {

    public AtomicLong numberOfSpansExported = new AtomicLong();

    @Override
    public ResultCode export(Collection<SpanData> spans) {
      numberOfSpansExported.addAndGet(spans.size());
      return ResultCode.SUCCESS;
    }

    @Override
    public ResultCode flush() {
      return ResultCode.SUCCESS;
    }

    @Override
    public void shutdown() {
      // no-op
    }
  }
}
