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

package io.opentelemetry.logging;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Labels;
import io.opentelemetry.internal.Utils;
import io.opentelemetry.logging.api.Exporter;
import io.opentelemetry.logging.api.LogProcessor;
import io.opentelemetry.logging.api.LogRecord;
import io.opentelemetry.metrics.LongCounter;
import io.opentelemetry.metrics.LongCounter.BoundLongCounter;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.sdk.common.DaemonThreadFactory;
import io.opentelemetry.sdk.common.export.CompletableResultCode;
import io.opentelemetry.sdk.common.export.ConfigBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class BatchLogProcessor implements LogProcessor {
  private static final String WORKER_THREAD_NAME =
      BatchLogProcessor.class.getSimpleName() + "_WorkerThread";
  private static final String TIMER_THREAD_NAME =
      BatchLogProcessor.class.getSimpleName() + "_TimerThread";

  private final Worker worker;
  private final Thread workerThread;

  private BatchLogProcessor(
      int maxQueueSize,
      long scheduleDelayMillis,
      int maxExportBatchSize,
      long exporterTimeout,
      Exporter exporter) {
    this.worker = new Worker(maxQueueSize, scheduleDelayMillis, maxExportBatchSize, exporterTimeout,
        exporter);
    this.workerThread = new DaemonThreadFactory(WORKER_THREAD_NAME).newThread(worker);
    this.workerThread.start();
  }

  public static Builder builder(Exporter exporter) {
    return new Builder(exporter);
  }

  @Override
  public void addLogRecord(LogRecord record) {
    worker.addLogRecord(record);
  }

  @Override
  public void shutdown() {
    workerThread.interrupt();
    worker.shutdown();
  }

  @Override
  public void forceFlush() {
    worker.forceFlush();
  }

  private static class Worker implements Runnable {
    static {
      Meter meter = OpenTelemetry.getMeter("io.opentelemetry.sdk.logging");
      LongCounter logProcessorErrors =
          meter
              .longCounterBuilder("logProcessorErrors")
              .setUnit("1")
              .setDescription(
                  "Number of errors encountered while processing logs")
              .build();
      Labels.Builder builder = Labels.of("logProcessorType", BatchLogProcessor.class.getName())
          .toBuilder();
      exporterFailureCounter =
          logProcessorErrors.bind(
              builder.setLabel("errorType", "exporter failure").build());
//      exporterExceptionCounter =
//          logProcessorErrors.bind(
//              builder.setLabel("errorType", "exporter exception").build());
      exporterBusyCounter =
          logProcessorErrors.bind(
              builder.setLabel("errorType", "exporter busy").build());
      droppedRecordCounter =
          logProcessorErrors.bind(
              builder.setLabel("errorType", "dropped record - queue full").build());
    }

    private static final BoundLongCounter exporterFailureCounter;
    private static final BoundLongCounter exporterBusyCounter;
    private static final BoundLongCounter droppedRecordCounter;
    private static final Logger logger = Logger.getLogger(Worker.class.getName());

    private final Object monitor = new Object();
    private final int maxQueueSize;
    private final long scheduleDelayMillis;
    private final ArrayList<LogRecord> logRecords;
    private final int maxExportBatchSize;
    private final ExecutorService executor;
    private final Exporter exporter;
    private final Timer timer = new Timer(TIMER_THREAD_NAME, true);
//    private final AtomicBoolean exportAvailable = new AtomicBoolean(true);
    private final long exporterTimeout;

    private Worker(
        int maxQueueSize,
        long scheduleDelayMillis,
        int maxExportBatchSize,
        long exporterTimeout,
        Exporter exporter) {
      this.maxQueueSize = maxQueueSize;
      this.maxExportBatchSize = maxExportBatchSize;

      this.exporterTimeout = exporterTimeout;
      this.scheduleDelayMillis = scheduleDelayMillis;
      this.exporter = exporter;
      this.logRecords = new ArrayList<>(maxQueueSize);
      // We should be able to drain a full queue without dropping a batch
      int exportQueueSize = maxQueueSize / maxExportBatchSize;
      RejectedExecutionHandler h = new RejectedExecutionHandler() {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
          exporterBusyCounter.add(1);
        }
      };
      this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<Runnable>(exportQueueSize), h);
    }

    @Override
    public void run() {
      ArrayList<LogRecord> logsCopy;
      while (!Thread.currentThread().isInterrupted()) {
        synchronized (monitor) {
          if (this.logRecords.size() < maxExportBatchSize) {
            do {
              try {
                monitor.wait(scheduleDelayMillis);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
            } while (this.logRecords.isEmpty());
          }
          logsCopy = new ArrayList<>(this.logRecords);
          logRecords.clear();
        }
        exportBatches(logsCopy);
      }
    }

    private void exportBatches(ArrayList<LogRecord> recordsToShip) {
      for (int i = 0; i < recordsToShip.size(); ) {
        int lastIndexToTake = Math.min(i + maxExportBatchSize, recordsToShip.size());
        onBatchExport(createLogDataForExport(recordsToShip, i, lastIndexToTake));
        i = lastIndexToTake;
      }
    }

    private void onBatchExport(final List<LogRecord> logDataForExport) {
      // The call to exporter.accept(logDataForExport) is presumed to be asynchronous
      // but we ensure that the call takes less than exporterSubmissionTimeout time
      // to enqueue the action. We then monitor completion of the CompletableResultCode
      // and ensure that execution occurs within the exporterTimeout time frame.
      final Future<CompletableResultCode> f = executor.submit(
          new Callable<CompletableResultCode>() {
            @Override
            public CompletableResultCode call() {
              return exporter.accept(logDataForExport);
            }
          });
//      final CompletableResultCode result;
//      try {
//        // This may block the worker thread for up to exporterSubmissionTimeout. This
//        // means that there is at most a single call to exporter.accept() happening at
//        // a time, and that they are in order. Ideally exporter.accept() should be
//        // enqueuing the work and returning immediately to be completed asynchronously.
//        result = f.get(exporterSubmissionTimeout, TimeUnit.MILLISECONDS);
//      } catch (InterruptedException e) {
//        logger.warning("log exporter interruption:" + e.getLocalizedMessage());
//        f.cancel(true);
//        return;
//      } catch (ExecutionException e) {
//        logger.warning("log exporter exception:" + e.getLocalizedMessage());
//        exporterFailureCounter.add(1);
//        return;
//      } catch (TimeoutException e) {
//        logger.warning("log exporter submission timeout");
//        exporterTimeoutCounter.add(1);
//        f.cancel(true);
//        return;
//      }
//
//      result.whenComplete(
//          new Runnable() {
//            @Override
//            public void run() {
//              if (!result.isSuccess()) {
//                exporterFailureCounter.add(1);
//                // TODO: would be nice to be able to capture an error in CompletableResultCode
//                logger.warning("Error encountered while exporting log batch");
//              }
//              exportAvailable.set(true);
//            }
//          });

      timer.schedule(
          new TimerTask() {
            @Override
            public void run() {
              if (f.isDone()) {
                try {
                  final CompletableResultCode result = f.get(0, TimeUnit.MILLISECONDS);
                  if (!result.isSuccess()) {
                    // This may mean that the export process has failed, or that it's still running
                    // but it's at the end of it's timeout.
                    logger.warning("log exporter has failed or timed out");
                    exporterFailureCounter.add(1);
                  }
                } catch (InterruptedException | ExecutionException e) {
                  logger.warning("log exporter failure:" + e.getLocalizedMessage());
                  exporterFailureCounter.add(1);
                } catch (TimeoutException e) {
                  logger.warning("log exporter has failed to return async result");
                  exporterFailureCounter.add(1);
                }
              }
            }
          },
          exporterTimeout);
    }

    private static List<LogRecord> createLogDataForExport(
        ArrayList<LogRecord> recordsToShip, int startIndex, int endIndex) {
      List<LogRecord> logDataBuffer = new ArrayList<>(endIndex - startIndex);
      for (int i = startIndex; i < endIndex; i++) {
        logDataBuffer.add(recordsToShip.get(i));
        recordsToShip.set(i, null);
      }
      return Collections.unmodifiableList(logDataBuffer);
    }

    public void addLogRecord(LogRecord record) {
      synchronized (monitor) {
        if (logRecords.size() >= maxQueueSize) {
          droppedRecordCounter.add(1);
          // FIXME: call callback
        }
        logRecords.add(record);
        if (logRecords.size() >= maxExportBatchSize) {
          monitor.notifyAll();
        }
      }
    }

    private void shutdown() {
      forceFlush();
      timer.cancel();
      exporter.shutdown();
    }

    private void forceFlush() {
      ArrayList<LogRecord> logsCopy;
      synchronized (monitor) {
        logsCopy = new ArrayList<>(this.logRecords);
        logRecords.clear();
      }
      exportBatches(logsCopy);
    }
  }

  static class Builder extends ConfigBuilder<Builder> {
    /* @VisibleForTesting */ static final long DEFAULT_SCHEDULE_DELAY_MILLIS = 5000;
    static final int DEFAULT_MAX_QUEUE_SIZE = 2048;
    static final int DEFAULT_MAX_EXPORT_BATCH_SIZE = 512;
    static final long DEFAULT_EXPORT_TIMEOUT_MILLIS = 30_000;

    private final Exporter exporter;
    private long scheduleDelayMillis = DEFAULT_SCHEDULE_DELAY_MILLIS;
    private int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
    private int maxExportBatchSize = DEFAULT_MAX_EXPORT_BATCH_SIZE;
    private long exporterTimeoutMillis = DEFAULT_EXPORT_TIMEOUT_MILLIS;

    private Builder(Exporter exporter) {
      this.exporter = Utils.checkNotNull(exporter, "Exporter argument can not be null");
    }

    public Builder newBuilder(Exporter exporter) {
      return new Builder(exporter);
    }

    public BatchLogProcessor build() {
      return new BatchLogProcessor(maxQueueSize, scheduleDelayMillis, maxExportBatchSize,
          exporterTimeoutMillis, exporter);
    }


    /**
     * Sets the delay interval between two consecutive exports. The actual interval may be shorter
     * if the batch size is getting larger than {@code maxQueuedSpans / 2}.
     *
     * <p>Default value is {@code 5000}ms.
     *
     * @param scheduleDelayMillis the delay interval between two consecutive exports.
     * @return this.
     * @see BatchLogProcessor.Builder#DEFAULT_SCHEDULE_DELAY_MILLIS
     */
    public BatchLogProcessor.Builder setScheduleDelayMillis(long scheduleDelayMillis) {
      this.scheduleDelayMillis = scheduleDelayMillis;
      return this;
    }

    long getScheduleDelayMillis() {
      return scheduleDelayMillis;
    }

    /**
     * Sets the maximum time an exporter will be allowed to run before being cancelled.
     *
     * <p>Default value is {@code 30000}ms
     *
     * @param exporterTimeoutMillis the timeout for exports in milliseconds.
     * @return this
     * @see BatchLogProcessor.Builder#DEFAULT_EXPORT_TIMEOUT_MILLIS
     */
    public Builder setExporterTimeoutMillis(int exporterTimeoutMillis) {
      this.exporterTimeoutMillis = exporterTimeoutMillis;
      return this;
    }

    long getExporterTimeoutMillis() {
      return exporterTimeoutMillis;
    }

    /**
     * Sets the maximum number of Spans that are kept in the queue before start dropping.
     *
     * <p>See the BatchSampledSpansProcessor class description for a high-level design description
     * of this class.
     *
     * <p>Default value is {@code 2048}.
     *
     * @param maxQueueSize the maximum number of Spans that are kept in the queue before start
     *     dropping.
     * @return this.
     * @see BatchLogProcessor.Builder#DEFAULT_MAX_QUEUE_SIZE
     */
    public Builder setMaxQueueSize(int maxQueueSize) {
      this.maxQueueSize = maxQueueSize;
      return this;
    }

    int getMaxQueueSize() {
      return maxQueueSize;
    }

    /**
     * Sets the maximum batch size for every export. This must be smaller or equal to {@code
     * maxQueuedSpans}.
     *
     * <p>Default value is {@code 512}.
     *
     * @param maxExportBatchSize the maximum batch size for every export.
     * @return this.
     * @see BatchLogProcessor.Builder#DEFAULT_MAX_EXPORT_BATCH_SIZE
     */
    public Builder setMaxExportBatchSize(int maxExportBatchSize) {
      Utils.checkArgument(maxExportBatchSize > 0, "maxExportBatchSize must be positive.");
      this.maxExportBatchSize = maxExportBatchSize;
      return this;
    }

    int getMaxExportBatchSize() {
      return maxExportBatchSize;
    }


    @Override
    protected Builder fromConfigMap(Map<String, String> configMap,
        NamingConvention namingConvention) {
      return null;
    }
  }

}
