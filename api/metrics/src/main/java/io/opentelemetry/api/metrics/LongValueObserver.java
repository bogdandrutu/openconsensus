/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import javax.annotation.concurrent.ThreadSafe;

/**
 * {@code ValueObserver} is the asynchronous instrument corresponding to ValueRecorder, used to
 * capture values that are treated as individual observations.
 *
 * <p>A {@code ValueObserver} is a good choice in situations where a measurement is expensive to
 * compute, such that it would be wasteful to compute on every request.
 *
 * <p>Example:
 *
 * <pre>{@code
 * class YourClass {
 *
 *   private static final Meter meter = OpenTelemetry.getMeterProvider().get("my_library_name");
 *
 *   void init() {
 *     meter.
 *         .longValueObserverBuilder("cpu_fan_speed")
 *         .setDescription("System CPU fan speed")
 *         .setUnit("ms")
 *         .setUpdater(result -> {
 *           // Get system cpu fan speed
 *           result.accept(cpuFanSpeed, Labels.empty());
 *         })
 *         .build();
 *   }
 * }
 * }</pre>
 */
@ThreadSafe
public interface LongValueObserver extends AsynchronousInstrument {}
