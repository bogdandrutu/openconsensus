/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.common.Labels;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builder class for {@link LongSumObserver}. */
public interface LongSumObserverBuilder
    extends AsynchronousInstrumentBuilder<BiConsumer<Long, Labels>> {
  @Override
  LongSumObserverBuilder setDescription(String description);

  @Override
  LongSumObserverBuilder setUnit(String unit);

  @Override
  LongSumObserverBuilder setUpdater(Consumer<BiConsumer<Long, Labels>> updater);

  @Override
  LongSumObserver build();
}
