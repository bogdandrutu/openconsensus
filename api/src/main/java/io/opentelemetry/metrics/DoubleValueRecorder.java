/*
 * Copyright 2019, OpenTelemetry Authors
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

package io.opentelemetry.metrics;

import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.DoubleValueRecorder.BoundDoubleValueRecorder;
import javax.annotation.concurrent.ThreadSafe;

/**
 * ValueRecorder is a synchronous instrument useful for recording any number, positive or negative.
 * Values captured by a Record(value) are treated as individual events belonging to a distribution
 * that is being summarized.
 *
 * <p>ValueRecorder should be chosen either when capturing measurements that do not contribute
 * meaningfully to a sum, or when capturing numbers that are additive in nature, but where the
 * distribution of individual increments is considered interesting.
 *
 * <p>One of the most common uses for ValueRecorder is to capture latency measurements. Latency
 * measurements are not additive in the sense that there is little need to know the latency-sum of
 * all processed requests. We use a ValueRecorder instrument to capture latency measurements
 * typically because we are interested in knowing mean, median, and other summary statistics about
 * individual events.
 *
 * <p>Example:
 *
 * <pre>{@code
 * class YourClass {
 *
 *   private static final Meter meter = OpenTelemetry.getMeterRegistry().get("my_library_name");
 *   private static final DoubleValueRecorder valueRecorder =
 *       meter.
 *           .doubleValueRecorderBuilder("doWork_latency")
 *           .setDescription("gRPC Latency")
 *           .setUnit("ms")
 *           .build();
 *
 *   // It is recommended that the API user keep references to a Bound Counters.
 *   private static final BoundDoubleValueRecorder someWorkBound =
 *       valueRecorder.bind("work_name", "some_work");
 *
 *   void doWork() {
 *      long startTime = System.nanoTime();
 *      // Your code here.
 *      someWorkBound.record((System.nanoTime() - startTime) / 1e6);
 *   }
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
@ThreadSafe
public interface DoubleValueRecorder extends SynchronousInstrument<BoundDoubleValueRecorder> {

  /**
   * Records the given measurement, associated with the current {@code Context} and provided set of
   * labels.
   *
   * @param value the measurement to record.
   * @param labels the set of labels to be associated to this recording
   * @throws IllegalArgumentException if value is negative.
   * @since 0.3.0
   */
  void record(double value, Labels labels);

  void record(double value);

  @Override
  BoundDoubleValueRecorder bind(Labels labels);

  /**
   * A {@code Bound Instrument} for a {@link DoubleValueRecorder}.
   *
   * @since 0.1.0
   */
  @ThreadSafe
  interface BoundDoubleValueRecorder extends SynchronousInstrument.BoundInstrument {
    /**
     * Records the given measurement, associated with the current {@code Context}.
     *
     * @param value the measurement to record.
     * @throws IllegalArgumentException if value is negative.
     * @since 0.1.0
     */
    void record(double value);

    @Override
    void unbind();
  }

  /** Builder class for {@link DoubleValueRecorder}. */
  interface Builder extends SynchronousInstrument.Builder {
    @Override
    Builder setDescription(String description);

    @Override
    Builder setUnit(String unit);

    @Override
    Builder setConstantLabels(Labels constantLabels);

    @Override
    DoubleValueRecorder build();
  }
}
