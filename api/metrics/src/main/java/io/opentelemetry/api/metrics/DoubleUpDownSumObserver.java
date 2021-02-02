/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import javax.annotation.concurrent.ThreadSafe;

/**
 * UpDownSumObserver is the asynchronous instrument corresponding to UpDownCounter, used to capture
 * a non-monotonic count with Observe(sum).
 *
 * <p>"Sum" appears in the name to remind that it is used to capture sums directly. Use a
 * UpDownSumObserver to capture any value that starts at zero and rises or falls throughout the
 * process lifetime.
 *
 * <p>A {@code UpDownSumObserver} is a good choice in situations where a measurement is expensive to
 * compute, such that it would be wasteful to compute on every request.
 *
 * <p>Example:
 *
 * <pre>{@code
 * class YourClass {
 *   private static final USED_LABELS = Labels.of("state", "user")
 *   private static final FREE_LABELS = Labels.of("state", "idle")
 *   private static final Meter meter = OpenTelemetry.getMeterProvider().get("my_library_name");
 *
 *   void init() {
 *     meter.
 *         .doubleUpDownSumObserverBuilder("memory_usage")
 *         .setDescription("System memory usage")
 *         .setUnit("by")
 *         .setUpdater(result -> {
 *           // Get system memory usage
 *           result.accept(memoryUsed, USED_LABELS);
 *           result.accept(memoryFree, FREE_LABELS);
 *         })
 *         .build();
 *   }
 * }
 * }</pre>
 */
@ThreadSafe
public interface DoubleUpDownSumObserver extends AsynchronousInstrument {}
