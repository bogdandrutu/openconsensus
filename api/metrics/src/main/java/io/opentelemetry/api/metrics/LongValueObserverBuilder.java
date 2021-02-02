/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.common.Labels;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builder class for {@link LongValueObserver}. */
public interface LongValueObserverBuilder
    extends AsynchronousInstrumentBuilder<BiConsumer<Long, Labels>> {
  @Override
  LongValueObserverBuilder setDescription(String description);

  @Override
  LongValueObserverBuilder setUnit(String unit);

  @Override
  LongValueObserverBuilder setUpdater(Consumer<BiConsumer<Long, Labels>> updater);

  @Override
  LongValueObserver build();
}
