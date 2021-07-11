/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import javax.annotation.Nullable;

/** Configuration for a view. */
public interface View {
  /** The Instrument selection criteria. */
  InstrumentSelectionCriteria getInstrumentSelection();

  /** The `name` of the View (optional). If not provided, the Instrument `name` will be used. */
  @Nullable
  String getName();

  /**
   * The `aggregation` (optional) to be used. If not provided, the default aggregation (based on the
   * type of the Instrument) will be applied.
   */
  AggregatorFactory<?> getAggregator(InstrumentDescriptor instrument);

  /**
   * A processor which takes in the attributes oof a measurement and determines the resulting set of
   * attributes.
   */
  AttributesProcessor getAttributesProcessor();

  /** Summons a new builder for views. */
  public static ViewBuilder builder() {
    return new ViewBuilderImpl();
  }
}
