/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

/**
 * Builder class for {@link Histogram}.
 *
 * <p>This can build both {@link LongHistogram} and {@link DoubleHistogram} instruments.
 */
public interface HistogramBuilder<InstrumentT extends Histogram>
    extends InstrumentBuilder<InstrumentT> {
  @Override
  public HistogramBuilder<InstrumentT> setDescription(String description);

  @Override
  public HistogramBuilder<InstrumentT> setUnit(String unit);
  /** Sets the histogram for recording {@code long} values. */
  public HistogramBuilder<LongHistogram> ofLongs();
  /** Sets the histogram for recording {@code double} values. */
  public HistogramBuilder<DoubleHistogram> ofDoubles();
}
