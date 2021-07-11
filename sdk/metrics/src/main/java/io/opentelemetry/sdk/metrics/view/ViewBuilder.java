/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

/** Raw interface to construct views. */
public interface ViewBuilder {

  ViewBuilder setSelection(InstrumentSelectionCriteria selection);

  ViewBuilder addAttributesProcessor(AttributesProcessor filter);

  ViewBuilder asSum();

  ViewBuilder asSumWithMonotonicity(boolean isMonotonic);

  ViewBuilder asGauge();

  ViewBuilder asHistogram();

  ViewBuilder asHistogramWithFixedBoundaries(double[] boundaries);

  ViewBuilder withDeltaAggregation();

  ViewBuilder withCumulativeAggregation();

  /** The `name` of the View (optional). If not provided, the Instrument `name` will be used. */
  ViewBuilder setName(String name);

  /** The `description`. If not provided, the Instrument `description` would be used by default. */
  ViewBuilder setDescription(String name);

  View build();
}
