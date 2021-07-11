/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.aggregator.DoubleHistogramAggregator;
import io.opentelemetry.sdk.metrics.aggregator.DoubleSumAggregator;
import io.opentelemetry.sdk.metrics.aggregator.ExemplarSampler;
import io.opentelemetry.sdk.metrics.aggregator.HistogramConfig;
import io.opentelemetry.sdk.metrics.aggregator.LastValueAggregator;
import io.opentelemetry.sdk.metrics.aggregator.LastValueConfig;
import io.opentelemetry.sdk.metrics.aggregator.LongSumAggregator;
import io.opentelemetry.sdk.metrics.aggregator.SumConfig;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.state.InstrumentStorage;
import io.opentelemetry.sdk.metrics.state.MeterProviderSharedState;
import io.opentelemetry.sdk.metrics.state.MeterSharedState;
import io.opentelemetry.sdk.metrics.state.WriteableInstrumentStorage;
import io.opentelemetry.sdk.metrics.view.AttributesProcessor;
import io.opentelemetry.sdk.metrics.view.View;
import java.util.function.Consumer;

/** A default implementation of measurement processor. */
@AutoValue
public abstract class DefaultMeasurementProcessor implements MeasurementProcessor {
  // Default histograms are designed for average HTTP/RPC latency measurements in "millisecond".
  // Note: These are similar to prometheus default buckets (although prometheus uses "seconds").
  private static final double[] DEFAULT_HISTOGRAM_BOUNDARIES = {
    5, 10, 25, 50, 75, 100, 250, 500, 750, 1_000, 2_500, 5_000, 7_500, 10_000
  };

  /**
   * Histogram boundaries that will be chosen when no hints given.
   *
   * <p>Note: Default histogram boundaries are designed for HTTP/RPC latencies measured in
   * nanoseconds, and are the following buckets: [5ms, 10ms, 25ms, 75ms, 100ms, 250ms, 500ms, 750ms,
   * 1s, 2.5s, 5s, 7.5s, 10s]
   */
  @SuppressWarnings("mutable")
  public abstract double[] getDefaultHistogramBoundaries();

  /** Sampler chosen for exemplars on insturments when no hints given. */
  public abstract ExemplarSampler getDefaultExemplarSampler();

  @Override
  public WriteableInstrumentStorage createStorage(
      InstrumentDescriptor instrument,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState) {
    // First check views
    for (View view : meterProviderSharedState.getViews()) {
      if (view.getInstrumentSelection()
          .matches(instrument, meterSharedState.getInstrumentationLibraryInfo())) {
        return InstrumentStorage.createSynchronous(
            meterProviderSharedState.getStartEpochNanos(),
            // aggregator factory is a bit odd...
            view.getAggregator(instrument)
                .create(
                    meterProviderSharedState.getResource(),
                    meterSharedState.getInstrumentationLibraryInfo(),
                    meterProviderSharedState.getStartEpochNanos(),
                    getDefaultExemplarSampler()),
            view.getAttributesProcessor());
      }
    }

    // TODO: Check if name conflicts with view, but view was not selected, then we have naming
    // conflict.

    // Default storage.
    switch (instrument.getType()) {
      case COUNTER:
      case UP_DOWN_COUNTER:
        return InstrumentStorage.createSynchronous(
            meterProviderSharedState.getStartEpochNanos(),
            sum(instrument, meterProviderSharedState, meterSharedState),
            AttributesProcessor.NOOP);
      case HISTOGRAM:
        return InstrumentStorage.createSynchronous(
            meterProviderSharedState.getStartEpochNanos(),
            histogram(instrument, meterProviderSharedState, meterSharedState),
            AttributesProcessor.NOOP);
      default:
        throw new IllegalArgumentException(
            "Unsupported synchronous metric: " + instrument.getType());
    }
  }

  @Override
  public <T extends ObservableMeasurement> InstrumentStorage createAsynchronousStorage(
      InstrumentDescriptor instrument,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      Consumer<T> callback) {
    // Check views.
    for (View view : meterProviderSharedState.getViews()) {
      if (view.getInstrumentSelection()
          .matches(instrument, meterSharedState.getInstrumentationLibraryInfo())) {
        return InstrumentStorage.createAsynchronous(
            meterProviderSharedState.getStartEpochNanos(),
            callback,
            view.getAggregator(instrument)
                .create(
                    meterProviderSharedState.getResource(),
                    meterSharedState.getInstrumentationLibraryInfo(),
                    meterProviderSharedState.getStartEpochNanos(),
                    getDefaultExemplarSampler()),
            view.getAttributesProcessor());
      }
    }
    // TODO: Check if name conflicts with view, but view was not selected, then we have naming
    // conflict.

    // Default storage.
    switch (instrument.getType()) {
      case OBSERVABLE_SUM:
      case OBSERVBALE_UP_DOWN_SUM:
        return InstrumentStorage.createAsynchronous(
            meterProviderSharedState.getStartEpochNanos(),
            callback,
            sum(instrument, meterProviderSharedState, meterSharedState),
            AttributesProcessor.NOOP);
      case OBSERVABLE_GAUGE:
        return InstrumentStorage.createAsynchronous(
            meterProviderSharedState.getStartEpochNanos(),
            callback,
            gauge(instrument, meterProviderSharedState, meterSharedState),
            AttributesProcessor.NOOP);
      default:
        throw new IllegalArgumentException(
            "Unsupported asynchronous metric: " + instrument.getType());
    }
  }

  protected Aggregator<?> gauge(
      InstrumentDescriptor instrument,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState) {
    LastValueConfig config = LastValueConfig.buildDefaultFromInstrument(instrument);
    return new LastValueAggregator(
        config,
        meterProviderSharedState.getResource(),
        meterSharedState.getInstrumentationLibraryInfo(),
        getDefaultExemplarSampler());
  }

  protected Aggregator<?> sum(
      InstrumentDescriptor instrument,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState) {
    SumConfig config = SumConfig.buildDefaultFromInstrument(instrument);
    // Default aggregation for synchronous sum.
    switch (instrument.getValueType()) {
      case LONG:
        return new LongSumAggregator(
            config,
            meterProviderSharedState.getResource(),
            meterSharedState.getInstrumentationLibraryInfo(),
            getDefaultExemplarSampler());
      case DOUBLE:
        return new DoubleSumAggregator(
            config,
            meterProviderSharedState.getResource(),
            meterSharedState.getInstrumentationLibraryInfo(),
            getDefaultExemplarSampler());
    }
    throw new IllegalArgumentException("Unsupported sum: " + instrument.getValueType());
  }

  protected Aggregator<?> histogram(
      InstrumentDescriptor instrument,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState) {
    // TODO: default histogram boundaries?
    HistogramConfig config =
        HistogramConfig.buildDefaultFromInstrument(instrument).toBuilder()
            .setBoundaries(getDefaultHistogramBoundaries())
            .build();
    // The Double processor will convert LongMeasurements to doubles.
    return new DoubleHistogramAggregator(
        config,
        meterProviderSharedState.getResource(),
        meterSharedState.getInstrumentationLibraryInfo(),
        getDefaultExemplarSampler());
  }

  static Builder builder() {
    return new AutoValue_DefaultMeasurementProcessor.Builder()
        .setDefaultHistogramBoundaries(DEFAULT_HISTOGRAM_BOUNDARIES)
        .setDefaultExemplarSampler(ExemplarSampler.NEVER);
  }

  /** Builder for {@link DefaultMeasurementProcessor}. */
  @AutoValue.Builder
  abstract static class Builder {
    /** Sets the exemplar sampler to use when no hints are provided. */
    abstract Builder setDefaultExemplarSampler(ExemplarSampler sampler);

    /** Sets the histogram boundaries to use when no hints are provided. */
    abstract Builder setDefaultHistogramBoundaries(double[] boundaries);

    abstract DefaultMeasurementProcessor build();
  }
}
