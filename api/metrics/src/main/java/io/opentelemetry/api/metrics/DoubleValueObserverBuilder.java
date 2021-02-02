/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.common.Labels;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builder class for {@link DoubleValueObserver}. */
public interface DoubleValueObserverBuilder
    extends AsynchronousInstrumentBuilder<BiConsumer<Double, Labels>> {
  @Override
  DoubleValueObserverBuilder setDescription(String description);

  @Override
  DoubleValueObserverBuilder setUnit(String unit);

  @Override
  DoubleValueObserverBuilder setUpdater(Consumer<BiConsumer<Double, Labels>> updater);

  @Override
  DoubleValueObserver build();
}
