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

package io.opentelemetry.metrics;

import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.DoubleUpDownCounter.BoundDoubleUpDownCounter;

import javax.annotation.concurrent.ThreadSafe;

/**
 * UpDownCounter is a synchronous instrument and very similar to Counter except that Add(increment)
 * supports negative increments. This makes UpDownCounter not useful for computing a rate
 * aggregation. The default aggregation is `Sum`, only the sum is non-monotonic. It is generally
 * useful for capturing changes in an amount of resources used, or any quantity that rises and falls
 * during a request.
 *
 * <p>Example:
 *
 * <pre>{@code
 * class YourClass {
 *   private static final Meter meter = OpenTelemetry.getMeterProvider().get("my_library_name");
 *   private static final DoubleUpDownCounter upDownCounter =
 *       meter.
 *           .doubleUpDownCounterBuilder("resource_usage")
 *           .setDescription("Current resource usage")
 *           .setUnit("1")
 *           .build();
 *
 *   // It is recommended that the API user keep references to a Bound Counters.
 *   private static final BoundDoubleUpDownCounter someWorkBound =
 *       upDownCounter.bind("work_name", "some_work");
 *
 *   void doSomeWork() {
 *      someWorkBound.add(10.2);  // Resources needed for this task.
 *      // Your code here.
 *      someWorkBound.add(-10.0);
 *   }
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
@ThreadSafe
public interface DoubleUpDownCounter extends SynchronousInstrument<BoundDoubleUpDownCounter> {

  /**
   * Adds the given {@code increment} to the current value.
   *
   * <p>The value added is associated with the current {@code Context} and provided set of labels.
   *
   * @param increment the value to add.
   * @param labels the labels to be associated to this recording.
   * @since 0.1.0
   */
  void add(double increment, Labels labels);

  /**
   * Adds the given {@code increment} to the current value.
   *
   * <p>The value added is associated with the current {@code Context} and empty labels.
   *
   * @param increment the value to add.
   * @since 0.8.0
   */
  void add(double increment);

  @Override
  BoundDoubleUpDownCounter bind(Labels labels);

  /**
   * A {@code Bound Instrument} for a {@link DoubleUpDownCounter}.
   *
   * @since 0.1.0
   */
  @ThreadSafe
  interface BoundDoubleUpDownCounter extends BoundInstrument {
    /**
     * Adds the given {@code increment} to the current value.
     *
     * <p>The value added is associated with the current {@code Context}.
     *
     * @param increment the value to add.
     * @since 0.1.0
     */
    void add(double increment);

    @Override
    void unbind();
  }

  /** Builder class for {@link DoubleUpDownCounter}. */
  interface Builder extends SynchronousInstrument.Builder {
    @Override
    Builder setDescription(String description);

    @Override
    Builder setUnit(String unit);

    @Override
    Builder setConstantLabels(Labels constantLabels);

    @Override
    DoubleUpDownCounter build();
  }
}
