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

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.sdk.metrics.view.Aggregation;
import io.opentelemetry.sdk.metrics.view.AggregationConfiguration;
import io.opentelemetry.sdk.metrics.view.AggregationConfiguration.Temporality;
import io.opentelemetry.sdk.metrics.view.Aggregations;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

// notes:
//  specify by pieces of the descriptor.
//    instrument type √
//    instrument name  (regex) √
//    instrument value type (?)
//    constant labels (?)
//    units (?)

// what you can choose:
//   aggregation √
//   delta vs. cumulative √
//   all labels vs. a list of labels

/**
 * Central location for Views to be registered. Registration of a view should eventually be done via
 * the {@link io.opentelemetry.sdk.metrics.MeterSdkProvider}.
 */
class ViewRegistry {

  private static final AggregationConfiguration CUMULATIVE_SUM =
      AggregationConfiguration.create(Aggregations.sum(), Temporality.CUMULATIVE);
  private static final AggregationConfiguration DELTA_SUMMARY =
      AggregationConfiguration.create(Aggregations.minMaxSumCount(), Temporality.DELTA);
  private static final AggregationConfiguration CUMULATIVE_LAST_VALUE =
      AggregationConfiguration.create(Aggregations.lastValue(), Temporality.CUMULATIVE);

  private final Map<InstrumentSelector, AggregationConfiguration> configuration =
      new ConcurrentHashMap<>();

  /**
   * Create a new {@link io.opentelemetry.sdk.metrics.Batcher} for use in metric recording
   * aggregation.
   */
  Batcher createBatcher(
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      InstrumentDescriptor descriptor) {

    AggregationConfiguration specification = findBestMatch(descriptor);

    Aggregation aggregation = specification.aggregation();

    if (Temporality.CUMULATIVE == specification.temporality()) {
      return Batchers.getCumulativeAllLabels(
          descriptor, meterProviderSharedState, meterSharedState, aggregation);
    } else if (Temporality.DELTA == specification.temporality()) {
      return Batchers.getDeltaAllLabels(
          descriptor, meterProviderSharedState, meterSharedState, aggregation);
    }
    throw new IllegalStateException("unsupported Temporality: " + specification.temporality());
  }

  // todo: consider moving this method to its own class, for more targetted testing.
  private AggregationConfiguration findBestMatch(InstrumentDescriptor descriptor) {

    for (Map.Entry<InstrumentSelector, AggregationConfiguration> entry : configuration.entrySet()) {
      InstrumentSelector registeredSelector = entry.getKey();

      if (matchesOnType(descriptor, registeredSelector)
          && matchesOnName(descriptor, registeredSelector)) {
        return entry.getValue();
      }
    }

    // If none found, use the defaults:
    return getDefaultSpecification(descriptor);
  }

  private static boolean matchesOnType(
      InstrumentDescriptor descriptor, InstrumentSelector registeredSelector) {
    if (registeredSelector.instrumentType() == null) {
      return true;
    }
    return registeredSelector.instrumentType().equals(descriptor.getType());
  }

  private static boolean matchesOnName(
      InstrumentDescriptor descriptor, InstrumentSelector registeredSelector) {
    Pattern pattern = registeredSelector.instrumentNamePattern();
    if (pattern == null) {
      return true;
    }
    return pattern.matcher(descriptor.getName()).matches();
  }

  private static AggregationConfiguration getDefaultSpecification(InstrumentDescriptor descriptor) {
    switch (descriptor.getType()) {
      case COUNTER:
      case UP_DOWN_COUNTER:
        return CUMULATIVE_SUM;
        // TODO: Revisit the batcher used here for value observers,
        // currently this does not remove duplicate records in the same cycle.
      case VALUE_OBSERVER:
      case VALUE_RECORDER:
        return DELTA_SUMMARY;
      case SUM_OBSERVER:
      case UP_DOWN_SUM_OBSERVER:
        return CUMULATIVE_LAST_VALUE;
    }
    throw new IllegalArgumentException("Unknown descriptor type: " + descriptor.getType());
  }

  void registerView(InstrumentSelector selector, AggregationConfiguration specification) {
    configuration.put(selector, specification);
  }
}
