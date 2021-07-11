/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.annotation.concurrent.Immutable;

@AutoValue
@Immutable
abstract class StressTestRunner {
  abstract ImmutableList<Operation> getOperations();

  abstract SdkMeter getMeter();

  abstract int getCollectionIntervalMs();

  final void run() {
    List<Operation> operations = getOperations();
    int numThreads = operations.size();
    final CountDownLatch countDownLatch = new CountDownLatch(numThreads);
    Thread collectionThread =
        new Thread(
            () -> {
              // While workers still work, do collections.
              while (countDownLatch.getCount() != 0) {
                Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(getCollectionIntervalMs()));
              }
            });
    List<Thread> operationThreads = new ArrayList<>(numThreads);
    for (final Operation operation : operations) {
      operationThreads.add(
          new Thread(
              () -> {
                for (int i = 0; i < operation.getNumOperations(); i++) {
                  operation.getUpdater().update();
                  Uninterruptibles.sleepUninterruptibly(
                      Duration.ofMillis(operation.getOperationDelayMs()));
                }
                countDownLatch.countDown();
              }));
    }

    // Start collection thread then the rest of the worker threads.
    collectionThread.start();
    for (Thread thread : operationThreads) {
      thread.start();
    }

    // Wait for all the thread to finish.
    for (Thread thread : operationThreads) {
      Uninterruptibles.joinUninterruptibly(thread);
    }
    Uninterruptibles.joinUninterruptibly(collectionThread);
  }

  static Builder builder() {
    return new AutoValue_StressTestRunner.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setMeter(SdkMeter meterSdk);

    abstract ImmutableList.Builder<Operation> operationsBuilder();

    abstract Builder setCollectionIntervalMs(int collectionInterval);

    Builder addOperation(final Operation operation) {
      operationsBuilder().add(operation);
      return this;
    }

    public abstract StressTestRunner build();
  }

  @AutoValue
  @Immutable
  abstract static class Operation {

    abstract int getNumOperations();

    abstract int getOperationDelayMs();

    abstract OperationUpdater getUpdater();

    static Operation create(int numOperations, int operationDelayMs, OperationUpdater updater) {
      return new AutoValue_StressTestRunner_Operation(numOperations, operationDelayMs, updater);
    }
  }

  abstract static class OperationUpdater {

    /** Called every operation. */
    abstract void update();

    /** Called after all operations are completed. */
    abstract void cleanup();
  }

  StressTestRunner() {}
}
