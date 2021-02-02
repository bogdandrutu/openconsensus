/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.common.Labels;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builder class for {@link DoubleUpDownSumObserver}. */
public interface DoubleUpDownSumObserverBuilder
    extends AsynchronousInstrumentBuilder<BiConsumer<Double, Labels>> {
  @Override
  DoubleUpDownSumObserverBuilder setDescription(String description);

  @Override
  DoubleUpDownSumObserverBuilder setUnit(String unit);

  @Override
  DoubleUpDownSumObserverBuilder setUpdater(Consumer<BiConsumer<Double, Labels>> updater);

  @Override
  DoubleUpDownSumObserver build();
}
