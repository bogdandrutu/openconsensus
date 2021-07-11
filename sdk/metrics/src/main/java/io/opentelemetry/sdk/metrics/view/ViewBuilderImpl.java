/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.aggregator.HistogramConfig;
import io.opentelemetry.sdk.metrics.aggregator.LastValueConfig;
import io.opentelemetry.sdk.metrics.aggregator.SumConfig;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;

/** Actual implementation that builds views from the API. */
class ViewBuilderImpl implements ViewBuilder {
  enum Aggregator {
    GAUGE,
    SUM,
    HISTOGRAM
  }

  private InstrumentSelectionCriteria selection = null;
  private AttributesProcessor currentProcessor = AttributesProcessor.NOOP;
  private Aggregator aggregation = Aggregator.GAUGE;
  private AggregationTemporality temporality = AggregationTemporality.CUMULATIVE;
  private boolean isMonotonic = true;
  private double[] histogramBoundaries = HistogramConfig.DEFAULT_HISTOGRAM_BOUNDARIES;
  private String viewName = null;
  private String viewDescription = null;

  @Override
  public ViewBuilder setSelection(InstrumentSelectionCriteria selection) {
    this.selection = selection;
    return this;
  }

  @Override
  public ViewBuilder addAttributesProcessor(AttributesProcessor filter) {
    currentProcessor = currentProcessor.then(filter);
    return this;
  }

  @Override
  public ViewBuilder asSum() {
    return asSumWithMonotonicity(true);
  }

  @Override
  public ViewBuilder asSumWithMonotonicity(boolean isMonotonic) {
    aggregation = Aggregator.SUM;
    this.isMonotonic = isMonotonic;
    return this;
  }

  @Override
  public ViewBuilder asGauge() {
    aggregation = Aggregator.GAUGE;
    return this;
  }

  @Override
  public ViewBuilder asHistogram() {
    aggregation = Aggregator.HISTOGRAM;
    return this;
  }

  @Override
  public ViewBuilder asHistogramWithFixedBoundaries(double[] boundaries) {
    histogramBoundaries = boundaries;
    return this;
  }

  @Override
  public ViewBuilder withDeltaAggregation() {
    temporality = AggregationTemporality.DELTA;
    return this;
  }

  @Override
  public ViewBuilder withCumulativeAggregation() {
    temporality = AggregationTemporality.CUMULATIVE;
    return this;
  }

  @Override
  public ViewBuilder setName(String name) {
    viewName = name;
    return this;
  }

  @Override
  public ViewBuilder setDescription(String description) {
    viewDescription = description;
    return this;
  }

  @Override
  public View build() {
    return new ViewImpl(
        selection,
        currentProcessor,
        aggregation,
        temporality,
        isMonotonic,
        histogramBoundaries,
        viewName,
        viewDescription);
  }

  static class ViewImpl implements View {
    private final InstrumentSelectionCriteria selection;
    private final AttributesProcessor currentProcessor;
    private final Aggregator aggregation;
    private final AggregationTemporality temporality;
    private final boolean isMonotonic;
    private final double[] histogramBoundaries;
    private final String viewName;
    private final String viewDescription;

    ViewImpl(
        InstrumentSelectionCriteria selection,
        AttributesProcessor currentProcessor,
        Aggregator aggregation,
        AggregationTemporality temporality,
        boolean isMonotonic,
        double[] histogramBoundaries,
        String viewName,
        String viewDescription) {
      this.selection = selection;
      this.currentProcessor = currentProcessor;
      this.aggregation = aggregation;
      this.temporality = temporality;
      this.isMonotonic = isMonotonic;
      this.histogramBoundaries = histogramBoundaries;
      this.viewName = viewName;
      this.viewDescription = viewDescription;
    }

    @Override
    public InstrumentSelectionCriteria getInstrumentSelection() {
      return selection;
    }

    @Override
    public String getName() {
      return viewName;
    }

    @Override
    public AggregatorFactory<?> getAggregator(InstrumentDescriptor instrument) {
      switch (aggregation) {
        case SUM:
          return sum(instrument);
        case HISTOGRAM:
          return histogram(instrument);
        case GAUGE:
          return gauge(instrument);
      }
      throw new IllegalStateException("View configured without known aggregation type!");
    }

    private AggregatorFactory<?> gauge(InstrumentDescriptor instrument) {
      return AggregatorFactory.lastValue(
          LastValueConfig.builder()
              .setName(viewName != null ? viewName : instrument.getName())
              .setDescription(
                  viewDescription != null ? viewDescription : instrument.getDescription())
              .setUnit(instrument.getUnit())
              .build());
    }

    private AggregatorFactory<?> sum(InstrumentDescriptor instrument) {
      return AggregatorFactory.doubleSum(
          SumConfig.builder()
              .setName(viewName != null ? viewName : instrument.getName())
              .setDescription(
                  viewDescription != null ? viewDescription : instrument.getDescription())
              .setUnit(instrument.getUnit())
              .setMonotonic(isMonotonic)
              .setTemporality(temporality)
              .setMeasurementTemporality(
                  instrument.getType().isSynchronous()
                      ? AggregationTemporality.DELTA
                      : AggregationTemporality.CUMULATIVE)
              .build());
    }

    private AggregatorFactory<?> histogram(InstrumentDescriptor instrument) {
      return AggregatorFactory.doubleHistogram(
          HistogramConfig.builder()
              .setName(viewName != null ? viewName : instrument.getName())
              .setDescription(
                  viewDescription != null ? viewDescription : instrument.getDescription())
              .setUnit(instrument.getUnit())
              .setTemporality(temporality)
              .setBoundaries(histogramBoundaries)
              .build());
    }

    @Override
    public AttributesProcessor getAttributesProcessor() {
      return currentProcessor;
    }
  }
}
