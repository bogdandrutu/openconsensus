/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import javax.annotation.concurrent.ThreadSafe;

/**
 * {@code SumObserver} is the asynchronous instrument corresponding to Counter, used to capture a
 * monotonic sum with Observe(sum).
 *
 * <p>"Sum" appears in the name to remind that it is used to capture sums directly. Use a
 * SumObserver to capture any value that starts at zero and rises throughout the process lifetime
 * and never falls.
 *
 * <p>A {@code SumObserver} is a good choice in situations where a measurement is expensive to
 * compute, such that it would be wasteful to compute on every request.
 *
 * <p>Example:
 *
 * <pre>{@code
 * class YourClass {
 *
 *   private static final IDLE_LABELS = Labels.of("state", "idle")
 *   private static final USER_LABELS = Labels.of("state", "user")
 *   private static final Meter meter = OpenTelemetry.getMeterProvider().get("my_library_name");
 *
 *   void init() {
 *     meter.
 *         .longSumObserverBuilder("cpu_time")
 *         .setDescription("System CPU usage")
 *         .setUnit("ms")
 *         .setUpdater(result -> {
 *           // Get system cpu usage
 *           result.accept(cpuIdle, IDLE_LABELS);
 *           result.accept(cpuUser, USER_LABELS);
 *         })
 *         .build();
 *   }
 * }
 * }</pre>
 */
@ThreadSafe
public interface LongSumObserver extends AsynchronousInstrument {}
