/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.metrics;

import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument.Observation;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Util class that can be use to atomically observe multiple instruments in one callback.
 *
 * <p>This class is equivalent with individually calling observe on every Measure, but has the
 * advantage that all these operations are recorded atomically.
 *
 * <p>Example:
 *
 * <pre>{@code
 * private void test() {
 *     final LongSumObserver distanceObserver =
 *         this.meter.longSumObserverBuilder("distance").setUnit("m").build();
 *     final DoubleSumObserver timeObserver =
 *         this.meter.doubleSumObserverBuilder("time").setUnit("s").build();
 *     this.meter.newBatchObserver(
 *         "exampleBatchObserver",
 *         new BatchObserverFunction() {
 *           {@literal @}Override
 *           public void observe(BatchObserverResult result) {
 *             MyMeasure measure = MyMeasure.getdata();
 *             result.observe(
 *                 Labels.of("myKey", "myalue"),
 *                 distanceObserver.observation(measure.getDistance()),
 *                 timeObserver.observation(measure.getTime())
 *             );
 *           }
 *         });
 *   }
 * }</pre>
 */
@ThreadSafe
public interface BatchObserver extends ObserverInstruments {

  void setFunction(BatchObserverFunction function);

  interface BatchObserverFunction {
    void observe(BatchObserverResult result);
  }

  interface BatchObserverResult {
    void observe(Labels labels, Observation... observations);
  }
}
