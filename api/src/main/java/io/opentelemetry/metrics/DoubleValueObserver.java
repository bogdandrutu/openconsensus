/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.metrics;

import javax.annotation.concurrent.ThreadSafe;

/**
 * {@code ValueObserver} is the asynchronous instrument corresponding to ValueRecorder, used to
 * capture values that are treated as individual observations, recorded with the observe(value)
 * method.
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
 *   private static final DoubleValueObserver cpuObserver =
 *       meter.
 *           .doubleValueObserverBuilder("cpu_temperature")
 *           .setDescription("System CPU temperature")
 *           .setUnit("ms")
 *           .build();
 *
 *   void init() {
 *     cpuObserver.setCallback(
 *         new DoubleValueObserver.Callback<DoubleResult>() {
 *          {@literal @}Override
 *           public void update(DoubleResult result) {
 *             // Get system cpu temperature
 *             result.observe(cpuTemperature);
 *           }
 *         });
 *   }
 * }
 * }</pre>
 *
 * @since 0.5.0
 */
@ThreadSafe
public interface DoubleValueObserver extends DoubleAsynchronousInstrument {

  /** Builder class for {@link DoubleValueObserver}. */
  interface Builder extends AsynchronousInstrument.Builder {
    @Override
    Builder setDescription(String description);

    @Override
    Builder setUnit(String unit);

    @Override
    DoubleValueObserver build();
  }
}
