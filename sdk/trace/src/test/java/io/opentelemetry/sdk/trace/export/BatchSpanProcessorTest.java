/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.internal.GuardedBy;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@SuppressWarnings("PreferJavaTimeOverload")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchSpanProcessorTest {

  private static final String SPAN_NAME_1 = "MySpanName/1";
  private static final String SPAN_NAME_2 = "MySpanName/2";
  private static final long MAX_SCHEDULE_DELAY_MILLIS = 500;
  private SdkTracerProvider sdkTracerProvider;
  private final BlockingSpanExporter blockingSpanExporter = new BlockingSpanExporter();

  @Mock private Sampler mockSampler;
  @Mock private SpanExporter mockSpanExporter;

  @BeforeEach
  void setUp() {
    when(mockSpanExporter.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
  }

  @AfterEach
  void cleanup() {
    if (sdkTracerProvider != null) {
      sdkTracerProvider.shutdown();
    }
  }

  private ReadableSpan createEndedSpan(String spanName) {
    Tracer tracer = sdkTracerProvider.get(getClass().getName());
    Span span = tracer.spanBuilder(spanName).startSpan();
    span.end();
    return (ReadableSpan) span;
  }

  @Test
  void configTest_EmptyOptions() {
    BatchSpanProcessorBuilder config =
        BatchSpanProcessor.builder(new WaitingSpanExporter(0, CompletableResultCode.ofSuccess()));
    assertThat(config.getScheduleDelayNanos())
        .isEqualTo(
            TimeUnit.MILLISECONDS.toNanos(BatchSpanProcessorBuilder.DEFAULT_SCHEDULE_DELAY_MILLIS));
    assertThat(config.getMaxQueueSize())
        .isEqualTo(BatchSpanProcessorBuilder.DEFAULT_MAX_QUEUE_SIZE);
    assertThat(config.getMaxExportBatchSize())
        .isEqualTo(BatchSpanProcessorBuilder.DEFAULT_MAX_EXPORT_BATCH_SIZE);
    assertThat(config.getExporterTimeoutNanos())
        .isEqualTo(
            TimeUnit.MILLISECONDS.toNanos(BatchSpanProcessorBuilder.DEFAULT_EXPORT_TIMEOUT_MILLIS));
  }

  @Test
  void invalidConfig() {
    SpanExporter exporter = mock(SpanExporter.class);
    assertThatThrownBy(
            () -> BatchSpanProcessor.builder(exporter).setScheduleDelay(-1, TimeUnit.MILLISECONDS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("delay must be non-negative");
    assertThatThrownBy(() -> BatchSpanProcessor.builder(exporter).setScheduleDelay(1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("unit");
    assertThatThrownBy(() -> BatchSpanProcessor.builder(exporter).setScheduleDelay(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("delay");
    assertThatThrownBy(
            () ->
                BatchSpanProcessor.builder(exporter).setExporterTimeout(-1, TimeUnit.MILLISECONDS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("timeout must be non-negative");
    assertThatThrownBy(() -> BatchSpanProcessor.builder(exporter).setExporterTimeout(1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("unit");
    assertThatThrownBy(() -> BatchSpanProcessor.builder(exporter).setExporterTimeout(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("timeout");
  }

  @Test
  void startEndRequirements() {
    BatchSpanProcessor spansProcessor =
        BatchSpanProcessor.builder(new WaitingSpanExporter(0, CompletableResultCode.ofSuccess()))
            .build();
    assertThat(spansProcessor.isStartRequired()).isFalse();
    assertThat(spansProcessor.isEndRequired()).isTrue();
  }

  @Test
  void exportDifferentSampledSpans() {
    WaitingSpanExporter waitingSpanExporter =
        new WaitingSpanExporter(2, CompletableResultCode.ofSuccess());
    sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(waitingSpanExporter)
                    .setScheduleDelay(MAX_SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    .build())
            .build();

    ReadableSpan span1 = createEndedSpan(SPAN_NAME_1);
    ReadableSpan span2 = createEndedSpan(SPAN_NAME_2);
    List<SpanData> exported = waitingSpanExporter.waitForExport();
    assertThat(exported).containsExactly(span1.toSpanData(), span2.toSpanData());
  }

  @Test
  void exportMoreSpansThanTheBufferSize() {
    CompletableSpanExporter spanExporter = new CompletableSpanExporter();

    sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(spanExporter)
                    .setMaxQueueSize(6)
                    .setMaxExportBatchSize(2)
                    .setScheduleDelay(MAX_SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    .build())
            .build();

    ReadableSpan span1 = createEndedSpan(SPAN_NAME_1);
    ReadableSpan span2 = createEndedSpan(SPAN_NAME_1);
    ReadableSpan span3 = createEndedSpan(SPAN_NAME_1);
    ReadableSpan span4 = createEndedSpan(SPAN_NAME_1);
    ReadableSpan span5 = createEndedSpan(SPAN_NAME_1);
    ReadableSpan span6 = createEndedSpan(SPAN_NAME_1);

    spanExporter.succeed();

    await()
        .untilAsserted(
            () ->
                assertThat(spanExporter.getExported())
                    .containsExactly(
                        span1.toSpanData(),
                        span2.toSpanData(),
                        span3.toSpanData(),
                        span4.toSpanData(),
                        span5.toSpanData(),
                        span6.toSpanData()));
  }

  @Test
  void forceExport() {
    WaitingSpanExporter waitingSpanExporter =
        new WaitingSpanExporter(100, CompletableResultCode.ofSuccess(), 1);
    BatchSpanProcessor batchSpanProcessor =
        BatchSpanProcessor.builder(waitingSpanExporter)
            .setMaxQueueSize(10_000)
            // Force flush should send all spans, make sure the number of spans we check here is
            // not divisible by the batch size.
            .setMaxExportBatchSize(49)
            .setScheduleDelay(10, TimeUnit.SECONDS)
            .build();

    sdkTracerProvider = SdkTracerProvider.builder().addSpanProcessor(batchSpanProcessor).build();
    for (int i = 0; i < 50; i++) {
      createEndedSpan("notExported");
    }
    List<SpanData> exported = waitingSpanExporter.waitForExport();
    assertThat(exported).isNotNull();
    assertThat(exported.size()).isEqualTo(49);

    for (int i = 0; i < 50; i++) {
      createEndedSpan("notExported");
    }
    exported = waitingSpanExporter.waitForExport();
    assertThat(exported).isNotNull();
    assertThat(exported.size()).isEqualTo(49);

    batchSpanProcessor.forceFlush().join(10, TimeUnit.SECONDS);
    exported = waitingSpanExporter.getExported();
    assertThat(exported).isNotNull();
    assertThat(exported.size()).isEqualTo(2);
  }

  @Test
  void exportSpansToMultipleServices() {
    WaitingSpanExporter waitingSpanExporter =
        new WaitingSpanExporter(2, CompletableResultCode.ofSuccess());
    WaitingSpanExporter waitingSpanExporter2 =
        new WaitingSpanExporter(2, CompletableResultCode.ofSuccess());
    sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(
                        SpanExporter.composite(
                            Arrays.asList(waitingSpanExporter, waitingSpanExporter2)))
                    .setScheduleDelay(MAX_SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    .build())
            .build();

    ReadableSpan span1 = createEndedSpan(SPAN_NAME_1);
    ReadableSpan span2 = createEndedSpan(SPAN_NAME_2);
    List<SpanData> exported1 = waitingSpanExporter.waitForExport();
    List<SpanData> exported2 = waitingSpanExporter2.waitForExport();
    assertThat(exported1).containsExactly(span1.toSpanData(), span2.toSpanData());
    assertThat(exported2).containsExactly(span1.toSpanData(), span2.toSpanData());
  }

  @Test
  void exportMoreSpansThanTheMaximumLimit() {
    final int maxQueuedSpans = 8;
    WaitingSpanExporter waitingSpanExporter =
        new WaitingSpanExporter(maxQueuedSpans, CompletableResultCode.ofSuccess());
    sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(
                        SpanExporter.composite(
                            Arrays.asList(blockingSpanExporter, waitingSpanExporter)))
                    .setScheduleDelay(MAX_SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    .setMaxQueueSize(maxQueuedSpans)
                    .setMaxExportBatchSize(maxQueuedSpans / 2)
                    .build())
            .build();

    List<SpanData> spansToExport = new ArrayList<>(maxQueuedSpans + 1);
    // Wait to block the worker thread in the BatchSampledSpansProcessor. This ensures that no items
    // can be removed from the queue. Need to add a span to trigger the export otherwise the
    // pipeline is never called.
    spansToExport.add(createEndedSpan("blocking_span").toSpanData());
    blockingSpanExporter.waitUntilIsBlocked();

    for (int i = 0; i < maxQueuedSpans; i++) {
      // First export maxQueuedSpans, the worker thread is blocked so all items should be queued.
      spansToExport.add(createEndedSpan("span_1_" + i).toSpanData());
    }

    // TODO: assertThat(spanExporter.getReferencedSpans()).isEqualTo(maxQueuedSpans);

    // Now we should start dropping.
    for (int i = 0; i < 7; i++) {
      createEndedSpan("span_2_" + i);
      // TODO: assertThat(getDroppedSpans()).isEqualTo(i + 1);
    }

    // TODO: assertThat(getReferencedSpans()).isEqualTo(maxQueuedSpans);

    // Release the blocking exporter
    blockingSpanExporter.unblock();

    // While we wait for maxQueuedSpans we ensure that the queue is also empty after this.
    List<SpanData> exported = waitingSpanExporter.waitForExport();
    assertThat(exported).isNotNull();
    assertThat(exported).containsExactlyElementsOf(spansToExport);
    exported.clear();
    spansToExport.clear();

    waitingSpanExporter.reset();
    // We cannot compare with maxReferencedSpans here because the worker thread may get
    // unscheduled immediately after exporting, but before updating the pushed spans, if that is
    // the case at most bufferSize spans will miss.
    // TODO: assertThat(getPushedSpans()).isAtLeast((long) maxQueuedSpans - maxBatchSize);

    for (int i = 0; i < maxQueuedSpans; i++) {
      spansToExport.add(createEndedSpan("span_3_" + i).toSpanData());
      // No more dropped spans.
      // TODO: assertThat(getDroppedSpans()).isEqualTo(7);
    }

    exported = waitingSpanExporter.waitForExport();
    assertThat(exported).isNotNull();
    assertThat(exported).containsExactlyElementsOf(spansToExport);
  }

  @Test
  void exporterThrowsException() {
    SpanExporter mockSpanExporter = mock(SpanExporter.class);
    WaitingSpanExporter waitingSpanExporter =
        new WaitingSpanExporter(1, CompletableResultCode.ofSuccess());
    doThrow(new IllegalArgumentException("No export for you."))
        .when(mockSpanExporter)
        .export(ArgumentMatchers.anyList());
    sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(
                        SpanExporter.composite(
                            Arrays.asList(mockSpanExporter, waitingSpanExporter)))
                    .setScheduleDelay(MAX_SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    .build())
            .build();
    ReadableSpan span1 = createEndedSpan(SPAN_NAME_1);
    List<SpanData> exported = waitingSpanExporter.waitForExport();
    assertThat(exported).containsExactly(span1.toSpanData());
    waitingSpanExporter.reset();
    // Continue to export after the exception was received.
    ReadableSpan span2 = createEndedSpan(SPAN_NAME_2);
    exported = waitingSpanExporter.waitForExport();
    assertThat(exported).containsExactly(span2.toSpanData());
  }

  @Test
  @Timeout(5)
  public void continuesIfExporterTimesOut() throws InterruptedException {
    int exporterTimeoutMillis = 10;
    BatchSpanProcessor bsp =
        BatchSpanProcessor.builder(mockSpanExporter)
            .setExporterTimeout(exporterTimeoutMillis, TimeUnit.MILLISECONDS)
            .setScheduleDelay(1, TimeUnit.MILLISECONDS)
            .setMaxQueueSize(1)
            .build();
    sdkTracerProvider = SdkTracerProvider.builder().addSpanProcessor(bsp).build();

    CountDownLatch exported = new CountDownLatch(1);
    // We return a result we never complete, meaning it will timeout.
    when(mockSpanExporter.export(
            argThat(
                spans -> {
                  assertThat(spans)
                      .anySatisfy(span -> assertThat(span.getName()).isEqualTo(SPAN_NAME_1));
                  exported.countDown();
                  return true;
                })))
        .thenReturn(new CompletableResultCode());
    createEndedSpan(SPAN_NAME_1);
    exported.await();
    // Timed out so the span was dropped.
    await().untilAsserted(() -> assertThat(bsp.getBatch()).isEmpty());

    // Still processing new spans.
    CountDownLatch exportedAgain = new CountDownLatch(1);
    reset(mockSpanExporter);
    when(mockSpanExporter.export(
            argThat(
                spans -> {
                  assertThat(spans)
                      .anySatisfy(span -> assertThat(span.getName()).isEqualTo(SPAN_NAME_2));
                  exportedAgain.countDown();
                  return true;
                })))
        .thenReturn(CompletableResultCode.ofSuccess());
    createEndedSpan(SPAN_NAME_2);
    exported.await();
    await().untilAsserted(() -> assertThat(bsp.getBatch()).isEmpty());
  }

  @Test
  void exportNotSampledSpans() {
    WaitingSpanExporter waitingSpanExporter =
        new WaitingSpanExporter(1, CompletableResultCode.ofSuccess());
    sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(waitingSpanExporter)
                    .setScheduleDelay(MAX_SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    .build())
            .setSampler(mockSampler)
            .build();

    when(mockSampler.shouldSample(any(), any(), any(), any(), any(), anyList()))
        .thenReturn(SamplingResult.drop());
    sdkTracerProvider.get("test").spanBuilder(SPAN_NAME_1).startSpan().end();
    sdkTracerProvider.get("test").spanBuilder(SPAN_NAME_2).startSpan().end();
    when(mockSampler.shouldSample(any(), any(), any(), any(), any(), anyList()))
        .thenReturn(SamplingResult.recordAndSample());
    ReadableSpan span = createEndedSpan(SPAN_NAME_2);
    // Spans are recorded and exported in the same order as they are ended, we test that a non
    // sampled span is not exported by creating and ending a sampled span after a non sampled span
    // and checking that the first exported span is the sampled span (the non sampled did not get
    // exported).
    List<SpanData> exported = waitingSpanExporter.waitForExport();
    // Need to check this because otherwise the variable span1 is unused, other option is to not
    // have a span1 variable.
    assertThat(exported).containsExactly(span.toSpanData());
  }

  @Test
  void exportNotSampledSpans_recordOnly() {
    WaitingSpanExporter waitingSpanExporter =
        new WaitingSpanExporter(1, CompletableResultCode.ofSuccess());

    when(mockSampler.shouldSample(any(), any(), any(), any(), any(), anyList()))
        .thenReturn(SamplingResult.recordOnly());
    sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(waitingSpanExporter)
                    .setScheduleDelay(MAX_SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    .build())
            .setSampler(mockSampler)
            .build();

    createEndedSpan(SPAN_NAME_1);
    when(mockSampler.shouldSample(any(), any(), any(), any(), any(), anyList()))
        .thenReturn(SamplingResult.recordAndSample());
    ReadableSpan span = createEndedSpan(SPAN_NAME_2);

    // Spans are recorded and exported in the same order as they are ended, we test that a non
    // exported span is not exported by creating and ending a sampled span after a non sampled span
    // and checking that the first exported span is the sampled span (the non sampled did not get
    // exported).
    List<SpanData> exported = waitingSpanExporter.waitForExport();
    // Need to check this because otherwise the variable span1 is unused, other option is to not
    // have a span1 variable.
    assertThat(exported).containsExactly(span.toSpanData());
  }

  @Test
  @Timeout(10)
  void shutdownFlushes() {
    WaitingSpanExporter waitingSpanExporter =
        new WaitingSpanExporter(1, CompletableResultCode.ofSuccess());
    // Set the export delay to large value, in order to confirm the #flush() below works

    sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(waitingSpanExporter)
                    .setScheduleDelay(10, TimeUnit.SECONDS)
                    .build())
            .build();

    ReadableSpan span2 = createEndedSpan(SPAN_NAME_2);

    // Force a shutdown, which forces processing of all remaining spans.
    sdkTracerProvider.shutdown().join(10, TimeUnit.SECONDS);

    List<SpanData> exported = waitingSpanExporter.getExported();
    assertThat(exported).containsExactly(span2.toSpanData());
    assertThat(waitingSpanExporter.shutDownCalled.get()).isTrue();
  }

  @Test
  void shutdownPropagatesSuccess() {
    SpanExporter mockSpanExporter = mock(SpanExporter.class);
    when(mockSpanExporter.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
    BatchSpanProcessor processor = BatchSpanProcessor.builder(mockSpanExporter).build();
    CompletableResultCode result = processor.shutdown();
    result.join(1, TimeUnit.SECONDS);
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void shutdownPropagatesFailure() {
    SpanExporter mockSpanExporter = mock(SpanExporter.class);
    when(mockSpanExporter.shutdown()).thenReturn(CompletableResultCode.ofFailure());
    BatchSpanProcessor processor = BatchSpanProcessor.builder(mockSpanExporter).build();
    CompletableResultCode result = processor.shutdown();
    result.join(1, TimeUnit.SECONDS);
    assertThat(result.isSuccess()).isFalse();
  }

  private static final class BlockingSpanExporter implements SpanExporter {

    final Object monitor = new Object();

    private enum State {
      WAIT_TO_BLOCK,
      BLOCKED,
      UNBLOCKED
    }

    @GuardedBy("monitor")
    State state = State.WAIT_TO_BLOCK;

    @Override
    public CompletableResultCode export(Collection<SpanData> spanDataList) {
      synchronized (monitor) {
        while (state != State.UNBLOCKED) {
          try {
            state = State.BLOCKED;
            // Some threads may wait for Blocked State.
            monitor.notifyAll();
            monitor.wait();
          } catch (InterruptedException e) {
            // Do nothing
          }
        }
      }
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    private void waitUntilIsBlocked() {
      synchronized (monitor) {
        while (state != State.BLOCKED) {
          try {
            monitor.wait();
          } catch (InterruptedException e) {
            // Do nothing
          }
        }
      }
    }

    @Override
    public CompletableResultCode shutdown() {
      // Do nothing;
      return CompletableResultCode.ofSuccess();
    }

    private void unblock() {
      synchronized (monitor) {
        state = State.UNBLOCKED;
        monitor.notifyAll();
      }
    }
  }

  private static class CompletableSpanExporter implements SpanExporter {

    private final List<CompletableResultCode> results = new ArrayList<>();

    private final List<SpanData> exported = new ArrayList<>();

    private volatile boolean succeeded;

    List<SpanData> getExported() {
      return exported;
    }

    void succeed() {
      succeeded = true;
      results.forEach(CompletableResultCode::succeed);
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
      exported.addAll(spans);
      if (succeeded) {
        return CompletableResultCode.ofSuccess();
      }
      CompletableResultCode result = new CompletableResultCode();
      results.add(result);
      return result;
    }

    @Override
    public CompletableResultCode flush() {
      if (succeeded) {
        return CompletableResultCode.ofSuccess();
      } else {
        return CompletableResultCode.ofFailure();
      }
    }

    @Override
    public CompletableResultCode shutdown() {
      return flush();
    }
  }

  static class WaitingSpanExporter implements SpanExporter {

    private final List<SpanData> spanDataList = new ArrayList<>();
    private final int numberToWaitFor;
    private final CompletableResultCode exportResultCode;
    private CountDownLatch countDownLatch;
    private int timeout = 10;
    private final AtomicBoolean shutDownCalled = new AtomicBoolean(false);

    WaitingSpanExporter(int numberToWaitFor, CompletableResultCode exportResultCode) {
      countDownLatch = new CountDownLatch(numberToWaitFor);
      this.numberToWaitFor = numberToWaitFor;
      this.exportResultCode = exportResultCode;
    }

    WaitingSpanExporter(int numberToWaitFor, CompletableResultCode exportResultCode, int timeout) {
      this(numberToWaitFor, exportResultCode);
      this.timeout = timeout;
    }

    List<SpanData> getExported() {
      List<SpanData> result = new ArrayList<>(spanDataList);
      spanDataList.clear();
      return result;
    }

    /**
     * Waits until we received numberOfSpans spans to export. Returns the list of exported {@link
     * SpanData} objects, otherwise {@code null} if the current thread is interrupted.
     *
     * @return the list of exported {@link SpanData} objects, otherwise {@code null} if the current
     *     thread is interrupted.
     */
    @Nullable
    List<SpanData> waitForExport() {
      try {
        countDownLatch.await(timeout, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // Preserve the interruption status as per guidance.
        Thread.currentThread().interrupt();
        return null;
      }
      return getExported();
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
      this.spanDataList.addAll(spans);
      for (int i = 0; i < spans.size(); i++) {
        countDownLatch.countDown();
      }
      return exportResultCode;
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      shutDownCalled.set(true);
      return CompletableResultCode.ofSuccess();
    }

    public void reset() {
      this.countDownLatch = new CountDownLatch(numberToWaitFor);
    }
  }
}
