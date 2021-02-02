/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.common.Labels;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builder class for {@link DoubleSumObserver}. */
public interface DoubleSumObserverBuilder
    extends AsynchronousInstrumentBuilder<BiConsumer<Double, Labels>> {
  @Override
  DoubleSumObserverBuilder setDescription(String description);

  @Override
  DoubleSumObserverBuilder setUnit(String unit);

  @Override
  DoubleSumObserverBuilder setUpdater(Consumer<BiConsumer<Double, Labels>> updater);

  @Override
  DoubleSumObserver build();
}
