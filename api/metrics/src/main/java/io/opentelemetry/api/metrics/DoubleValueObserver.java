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
 *         .doubleValueObserverBuilder("cpu_temperature")
 *         .setDescription("System CPU temperature")
 *         .setUnit("ms")
 *         .setUpdater(result -> {
 *           // Get system cpu temperature
 *           result.accept(cpuTemperature, Labels.empty());
 *         })
 *         .build();
 *   }
 * }
 * }</pre>
 */
@ThreadSafe
public interface DoubleValueObserver extends AsynchronousInstrument {}
