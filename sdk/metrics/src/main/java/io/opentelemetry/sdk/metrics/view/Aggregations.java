/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.aggregator.DDSketchAggregator;
import io.opentelemetry.sdk.metrics.aggregator.DoubleLastValueAggregator;
import io.opentelemetry.sdk.metrics.aggregator.DoubleMinMaxSumCount;
import io.opentelemetry.sdk.metrics.aggregator.DoubleSumAggregator;
import io.opentelemetry.sdk.metrics.aggregator.LongLastValueAggregator;
import io.opentelemetry.sdk.metrics.aggregator.LongMinMaxSumCount;
import io.opentelemetry.sdk.metrics.aggregator.LongSumAggregator;
import io.opentelemetry.sdk.metrics.aggregator.NoopAggregator;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.common.InstrumentValueType;
import io.opentelemetry.sdk.metrics.data.MetricData;
import javax.annotation.concurrent.Immutable;

public class Aggregations {

  /**
   * Returns an {@code Aggregation} that calculates sum of recorded measurements.
   *
   * @return an {@code Aggregation} that calculates sum of recorded measurements.
   */
  public static Aggregation sum() {
    return Sum.INSTANCE;
  }

  /**
   * Returns an {@code Aggregation} that calculates count of recorded measurements (the number of
   * recorded measurements).
   *
   * @return an {@code Aggregation} that calculates count of recorded measurements (the number of
   *     recorded * measurements).
   */
  public static Aggregation count() {
    return Count.INSTANCE;
  }

  /**
   * Returns an {@code Aggregation} that supports distribution metrics and percentile calculations.
   *
   * @return an {@code Aggregation} that calculates count of recorded measurements (the number of
   *     recorded * measurements).
   */
  public static Aggregation ddSketch() {
    return DDSketch.INSTANCE;
  }

  /**
   * Returns an {@code Aggregation} that calculates distribution stats on recorded measurements.
   * Distribution includes sum, count, histogram, and sum of squared deviations.
   *
   * <p>The boundaries for the buckets in the underlying histogram needs to be sorted.
   *
   * @param bucketBoundaries bucket boundaries to use for distribution.
   * @return an {@code Aggregation} that calculates distribution stats on recorded measurements.
   */
  public static Aggregation distributionWithExplicitBounds(Double... bucketBoundaries) {
    return new Distribution(bucketBoundaries);
  }

  /**
   * Returns an {@code Aggregation} that calculates the last value of all recorded measurements.
   *
   * @return an {@code Aggregation} that calculates the last value of all recorded measurements.
   */
  public static Aggregation lastValue() {
    return LastValue.INSTANCE;
  }

  /**
   * Returns an {@code Aggregation} that calculates a simple summary of all recorded measurements.
   * The summary consists of the count of measurements, the sum of all measurements, the maximum
   * value recorded and the minimum value recorded.
   *
   * @return an {@code Aggregation} that calculates a simple summary of all recorded measurements.
   */
  public static Aggregation minMaxSumCount() {
    return MinMaxSumCount.INSTANCE;
  }

  private enum MinMaxSumCount implements Aggregation {
    INSTANCE;

    @Override
    public AggregatorFactory getAggregatorFactory(InstrumentValueType instrumentValueType) {
      return instrumentValueType == InstrumentValueType.LONG
          ? LongMinMaxSumCount.getFactory()
          : DoubleMinMaxSumCount.getFactory();
    }

    @Override
    public MetricData.Type getDescriptorType(
        InstrumentType instrumentType, InstrumentValueType instrumentValueType) {
      return MetricData.Type.SUMMARY;
    }

    @Override
    public String getUnit(String initialUnit) {
      return initialUnit;
    }

    @Override
    public boolean availableForInstrument(InstrumentType instrumentType) {
      return instrumentType == InstrumentType.VALUE_OBSERVER
          || instrumentType == InstrumentType.VALUE_RECORDER;
    }
  }

  @Immutable
  private enum Sum implements Aggregation {
    INSTANCE;

    @Override
    public AggregatorFactory getAggregatorFactory(InstrumentValueType instrumentValueType) {
      return instrumentValueType == InstrumentValueType.LONG
          ? LongSumAggregator.getFactory()
          : DoubleSumAggregator.getFactory();
    }

    @Override
    public MetricData.Type getDescriptorType(
        InstrumentType instrumentType, InstrumentValueType instrumentValueType) {
      switch (instrumentType) {
        case COUNTER:
        case SUM_OBSERVER:
          return instrumentValueType == InstrumentValueType.LONG
              ? MetricData.Type.MONOTONIC_LONG
              : MetricData.Type.MONOTONIC_DOUBLE;
        case UP_DOWN_COUNTER:
        case VALUE_RECORDER:
        case UP_DOWN_SUM_OBSERVER:
        case VALUE_OBSERVER:
          return instrumentValueType == InstrumentValueType.LONG
              ? MetricData.Type.NON_MONOTONIC_LONG
              : MetricData.Type.NON_MONOTONIC_DOUBLE;
      }
      throw new IllegalArgumentException("Unsupported instrument/value types");
    }

    @Override
    public String getUnit(String initialUnit) {
      return initialUnit;
    }

    @Override
    public boolean availableForInstrument(InstrumentType instrumentType) {
      // Available for all instruments.
      return true;
    }
  }

  @Immutable
  private enum Count implements Aggregation {
    INSTANCE;

    @Override
    public AggregatorFactory getAggregatorFactory(InstrumentValueType instrumentValueType) {
      // TODO: Implement count aggregator and use it here.
      return NoopAggregator.getFactory();
    }

    @Override
    public MetricData.Type getDescriptorType(
        InstrumentType instrumentType, InstrumentValueType instrumentValueType) {
      return MetricData.Type.MONOTONIC_LONG;
    }

    @Override
    public String getUnit(String initialUnit) {
      return "1";
    }

    @Override
    public boolean availableForInstrument(InstrumentType instrumentType) {
      // Available for all instruments.
      return true;
    }
  }

  @Immutable
  private static final class Distribution implements Aggregation {
    private final AggregatorFactory factory;

    Distribution(Double... bucketBoundaries) {
      // TODO: Implement distribution aggregator and use it here.
      this.factory = NoopAggregator.getFactory();
    }

    @Override
    public AggregatorFactory getAggregatorFactory(InstrumentValueType instrumentValueType) {
      return factory;
    }

    @Override
    public MetricData.Type getDescriptorType(
        InstrumentType instrumentType, InstrumentValueType instrumentValueType) {
      throw new UnsupportedOperationException("Implement this");
    }

    @Override
    public String getUnit(String initialUnit) {
      return initialUnit;
    }

    @Override
    public boolean availableForInstrument(InstrumentType instrumentType) {
      throw new UnsupportedOperationException("Implement this");
    }
  }

  @Immutable
  private enum DDSketch implements Aggregation {
    INSTANCE;

    @Override
    public AggregatorFactory getAggregatorFactory(InstrumentValueType instrumentValueType) {
      return DDSketchAggregator.getBalancedFactory();
    }

    @Override
    public MetricData.Type getDescriptorType(
        InstrumentType instrumentType, InstrumentValueType instrumentValueType) {
      return MetricData.Type.SUMMARY;
    }

    @Override
    public String getUnit(String initialUnit) {
      return initialUnit;
    }

    @Override
    public boolean availableForInstrument(InstrumentType instrumentType) {
      return instrumentType == InstrumentType.VALUE_OBSERVER
          || instrumentType == InstrumentType.VALUE_RECORDER;
    }
  }

  @Immutable
  private enum LastValue implements Aggregation {
    INSTANCE;

    @Override
    public AggregatorFactory getAggregatorFactory(InstrumentValueType instrumentValueType) {
      return instrumentValueType == InstrumentValueType.LONG
          ? LongLastValueAggregator.getFactory()
          : DoubleLastValueAggregator.getFactory();
    }

    @Override
    public MetricData.Type getDescriptorType(
        InstrumentType instrumentType, InstrumentValueType instrumentValueType) {
      switch (instrumentType) {
        case SUM_OBSERVER:
          return instrumentValueType == InstrumentValueType.LONG
              ? MetricData.Type.MONOTONIC_LONG
              : MetricData.Type.MONOTONIC_DOUBLE;
        case UP_DOWN_SUM_OBSERVER:
          return instrumentValueType == InstrumentValueType.LONG
              ? MetricData.Type.NON_MONOTONIC_LONG
              : MetricData.Type.NON_MONOTONIC_DOUBLE;
        case VALUE_OBSERVER:
          return instrumentValueType == InstrumentValueType.LONG
              ? MetricData.Type.GAUGE_LONG
              : MetricData.Type.GAUGE_DOUBLE;
        default:
          // Do not change this unless the limitations of the current LastValueAggregator are fixed.
          throw new IllegalArgumentException(
              "Unsupported instrument/value types: " + instrumentType + "/" + instrumentValueType);
      }
    }

    @Override
    public String getUnit(String initialUnit) {
      return initialUnit;
    }

    @Override
    public boolean availableForInstrument(InstrumentType instrumentType) {
      // Do not change this unless the limitations of the current LastValueAggregator are fixed.
      return instrumentType == InstrumentType.SUM_OBSERVER
          || instrumentType == InstrumentType.UP_DOWN_SUM_OBSERVER;
    }
  }

  private Aggregations() {}
}
