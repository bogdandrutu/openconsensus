/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.sdk.metrics.view.Aggregation;
import io.opentelemetry.sdk.metrics.view.Aggregations;

// notes:
//  specify by pieces of the descriptor.
//    instrument type
//    instrument value type
//    instrument name  (wildcards allowed?)
//    constant labels (?)
//    units (?)

// what you can choose:
//   aggregation
//   all labels vs. a list of labels
//   delta vs. cumulative

/**
 * Central location for Views to be registered. Registration of a view should eventually be done via
 * the {@link io.opentelemetry.sdk.metrics.MeterSdkProvider}.
 */
class ViewRegistry {

  /**
   * Create a new {@link io.opentelemetry.sdk.metrics.Batcher} for use in metric recording
   * aggregation.
   */
  Batcher createBatcher(
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      InstrumentDescriptor descriptor) {

    Aggregation aggregation = getRegisteredAggregation(descriptor);

    // todo: don't just use the defaults!
    switch (descriptor.getType()) {
      case COUNTER:
      case UP_DOWN_COUNTER:
      case SUM_OBSERVER:
      case UP_DOWN_SUM_OBSERVER:
        return Batchers.getCumulativeAllLabels(
            descriptor, meterProviderSharedState, meterSharedState, aggregation);
      case VALUE_RECORDER:
        // TODO: Revisit the batcher used here for value observers,
        // currently this does not remove duplicate records in the same cycle.
      case VALUE_OBSERVER:
        return Batchers.getDeltaAllLabels(
            descriptor, meterProviderSharedState, meterSharedState, aggregation);
    }
    throw new IllegalArgumentException("Unknown descriptor type: " + descriptor.getType());
  }

  private static Aggregation getRegisteredAggregation(InstrumentDescriptor descriptor) {
    // todo look up based on fields of the descriptor.
    switch (descriptor.getType()) {
      case COUNTER:
      case UP_DOWN_COUNTER:
        return Aggregations.sum();
      case VALUE_RECORDER:
        return Aggregations.minMaxSumCount();
        // TODO allow selection of ddSketch:
        //        return Aggregations.ddSketch();
      case VALUE_OBSERVER:
      case SUM_OBSERVER:
      case UP_DOWN_SUM_OBSERVER:
        return Aggregations.lastValue();
    }
    throw new IllegalArgumentException("Unknown descriptor type: " + descriptor.getType());
  }
}
