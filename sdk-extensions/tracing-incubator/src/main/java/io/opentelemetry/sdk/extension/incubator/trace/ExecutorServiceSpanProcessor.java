/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.trace;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.jctools.queues.MpscArrayQueue;

/**
 * A Batch {@link SpanProcessor} that uses a user-provided {@link
 * java.util.concurrent.ScheduledExecutorService} to run background tasks.
 */
@SuppressWarnings("FutureReturnValueIgnored")
public final class ExecutorServiceSpanProcessor implements SpanProcessor {

  private static final AttributeKey<String> SPAN_PROCESSOR_TYPE_KEY =
      AttributeKey.stringKey("spanProcessorType");
  private static final AttributeKey<Boolean> DROPPED_KEY = AttributeKey.booleanKey("dropped");
  private static final String SPAN_PROCESSOR_TYPE_VALUE =
      ExecutorServiceSpanProcessor.class.getSimpleName();
  private static final Attributes SPAN_PROCESSOR_LABELS =
      Attributes.of(SPAN_PROCESSOR_TYPE_KEY, SPAN_PROCESSOR_TYPE_VALUE);
  private static final Attributes SPAN_PROCESSOR_DROPPED_LABELS =
      Attributes.of(SPAN_PROCESSOR_TYPE_KEY, SPAN_PROCESSOR_TYPE_VALUE, DROPPED_KEY, true);
  private static final Attributes SPAN_PROCESSOR_EXPORTED_LABELS =
      Attributes.of(SPAN_PROCESSOR_TYPE_KEY, SPAN_PROCESSOR_TYPE_VALUE, DROPPED_KEY, false);

  private final Worker worker;
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);
  private final boolean ownsExecutorService;
  private final ScheduledExecutorService executorService;

  /**
   * Create a new {@link ExecutorServiceSpanProcessorBuilder} with the required components.
   *
   * @param spanExporter The {@link SpanExporter} to be used for exports.
   * @param executorService The {@link ScheduledExecutorService} for running background tasks.
   * @param ownsExecutorService Whether this component can be considered the "owner" of the provided
   *     {@link ScheduledExecutorService}. If true, the {@link ScheduledExecutorService} will be
   *     shut down when this SpanProcessor is shut down.
   */
  public static ExecutorServiceSpanProcessorBuilder builder(
      SpanExporter spanExporter,
      ScheduledExecutorService executorService,
      boolean ownsExecutorService) {
    return new ExecutorServiceSpanProcessorBuilder(
        spanExporter, executorService, ownsExecutorService);
  }

  ExecutorServiceSpanProcessor(
      SpanExporter spanExporter,
      long scheduleDelayNanos,
      int maxQueueSize,
      int maxExportBatchSize,
      long exporterTimeoutNanos,
      ScheduledExecutorService executorService,
      boolean ownsExecutorService,
      long workerScheduleIntervalNanos) {
    this.worker =
        new Worker(
            spanExporter,
            scheduleDelayNanos,
            maxExportBatchSize,
            exporterTimeoutNanos,
            new MpscArrayQueue<>(maxQueueSize),
            executorService,
            isShutdown,
            workerScheduleIntervalNanos);
    this.ownsExecutorService = ownsExecutorService;
    this.executorService = executorService;
    executorService.schedule(worker, workerScheduleIntervalNanos, TimeUnit.NANOSECONDS);
  }

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {}

  @Override
  public boolean isStartRequired() {
    return false;
  }

  @Override
  public void onEnd(ReadableSpan span) {
    if (!span.getSpanContext().isSampled()) {
      return;
    }
    worker.addSpan(span);
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  @Override
  public CompletableResultCode shutdown() {
    if (isShutdown.getAndSet(true)) {
      return CompletableResultCode.ofSuccess();
    }
    CompletableResultCode result = worker.shutdown();
    // do the cleanup after worker finishes flush
    result.whenComplete(
        () -> {
          if (ownsExecutorService) {
            executorService.shutdown();
          }
        });

    return result;
  }

  @Override
  public CompletableResultCode forceFlush() {
    return worker.forceFlush();
  }

  // Visible for testing
  List<SpanData> getBatch() {
    return new ArrayList<>(worker.batch);
  }

  private static class Worker implements Runnable {

    private final AtomicLong nextExportTime = new AtomicLong();
    private final ArrayBlockingQueue<SpanData> batch;
    private final AtomicBoolean isShutdown;
    private final long workerScheduleIntervalNanos;
    private final WorkerExporter workerExporter;
    private final BoundLongCounter droppedSpans;
    private final AtomicReference<CompletableResultCode> flushRequested = new AtomicReference<>();
    private final long scheduleDelayNanos;
    private final MpscArrayQueue<ReadableSpan> queue;
    private final int maxExportBatchSize;
    private final SpanExporter spanExporter;
    private final ScheduledExecutorService executorService;

    private Worker(
        SpanExporter spanExporter,
        long scheduleDelayNanos,
        int maxExportBatchSize,
        long exporterTimeoutNanos,
        MpscArrayQueue<ReadableSpan> queue,
        ScheduledExecutorService executorService,
        AtomicBoolean isShutdown,
        long workerScheduleIntervalNanos) {
      // TODO: As of Specification 1.4, this should have a telemetry schema version.
      Meter meter = GlobalMeterProvider.get().meterBuilder("io.opentelemetry.sdk.trace").build();
      meter
          .gaugeBuilder("queueSize")
          .ofLongs()
          .setDescription("The number of spans queued")
          .setUnit("1")
          .buildWithCallback(result -> result.observe(queue.size(), SPAN_PROCESSOR_LABELS));
      LongCounter processedSpansCounter =
          meter
              .counterBuilder("processedSpans")
              .setUnit("1")
              .setDescription(
                  "The number of spans processed by the BatchSpanProcessor. "
                      + "[dropped=true if they were dropped due to high throughput]")
              .build();
      droppedSpans = processedSpansCounter.bind(SPAN_PROCESSOR_DROPPED_LABELS);
      BoundLongCounter exportedSpans = processedSpansCounter.bind(SPAN_PROCESSOR_EXPORTED_LABELS);
      this.isShutdown = isShutdown;
      this.executorService = executorService;
      this.spanExporter = spanExporter;
      this.batch = new ArrayBlockingQueue<>(maxExportBatchSize);
      this.workerScheduleIntervalNanos = workerScheduleIntervalNanos;
      this.maxExportBatchSize = maxExportBatchSize;
      this.workerExporter =
          new WorkerExporter(
              spanExporter,
              executorService,
              Logger.getLogger(getClass().getName()),
              exporterTimeoutNanos,
              exportedSpans,
              flushRequested,
              maxExportBatchSize);
      this.scheduleDelayNanos = scheduleDelayNanos;
      this.queue = queue;
      updateNextExportTime();
    }

    private void updateNextExportTime() {
      nextExportTime.set(System.nanoTime() + scheduleDelayNanos);
    }

    @Override
    public void run() {
      // nextExportTime is set for the first time in the constructor

      boolean continueWork = true;
      while (continueWork && !isShutdown.get()) {
        if (flushRequested.get() != null) {
          workerExporter.flush(batch, queue);
        }

        ReadableSpan lastElement = queue.poll();
        if (lastElement != null) {
          batch.add(lastElement.toSpanData());
        } else {
          // nothing in the queue, so schedule next run and release the thread
          continueWork = false;
          scheduleNextRun();
        }

        if (batch.size() >= maxExportBatchSize || System.nanoTime() >= nextExportTime.get()) {
          continueWork = false;
          workerExporter.exportCurrentBatch(batch).whenComplete(this::scheduleNextRun);
          updateNextExportTime();
        }
      }
      // flush may be requested when processor shuts down
      if (flushRequested.get() != null) {
        workerExporter.flush(batch, queue);
      }
    }

    private void scheduleNextRun() {
      if (!isShutdown.get()) {
        executorService.schedule(this, workerScheduleIntervalNanos, TimeUnit.NANOSECONDS);
      }
    }

    public CompletableResultCode shutdown() {
      final CompletableResultCode result = new CompletableResultCode();

      final CompletableResultCode flushResult = forceFlush();
      flushResult.whenComplete(
          () -> {
            final CompletableResultCode shutdownResult = spanExporter.shutdown();
            shutdownResult.whenComplete(
                () -> {
                  if (!flushResult.isSuccess() || !shutdownResult.isSuccess()) {
                    result.fail();
                  } else {
                    result.succeed();
                  }
                });
          });

      return result;
    }

    public void addSpan(ReadableSpan span) {
      if (!queue.offer(span)) {
        droppedSpans.add(1);
      }
    }

    public CompletableResultCode forceFlush() {
      CompletableResultCode flushResult = new CompletableResultCode();
      // we set the atomic here to trigger the worker loop to do a flush on its next iteration.
      flushRequested.compareAndSet(null, flushResult);
      CompletableResultCode possibleResult = flushRequested.get();
      // there's a race here where the flush happening in the worker loop could complete before we
      // get what's in the atomic. In that case, just return success, since we know it succeeded in
      // the interim.
      return possibleResult == null ? CompletableResultCode.ofSuccess() : possibleResult;
    }
  }
}
