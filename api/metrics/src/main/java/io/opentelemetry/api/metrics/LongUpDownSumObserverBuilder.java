/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.common.Labels;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builder class for {@link LongUpDownSumObserver}. */
public interface LongUpDownSumObserverBuilder
    extends AsynchronousInstrumentBuilder<BiConsumer<Long, Labels>> {
  @Override
  LongUpDownSumObserverBuilder setDescription(String description);

  @Override
  LongUpDownSumObserverBuilder setUnit(String unit);

  @Override
  LongUpDownSumObserverBuilder setUpdater(Consumer<BiConsumer<Long, Labels>> updater);

  @Override
  LongUpDownSumObserver build();
}
