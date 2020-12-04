/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics.batch;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public interface DoubleValueBatchObserver extends AsynchronousBatchInstrument {
  Observation observation(double result);

  interface Builder extends AsynchronousBatchInstrument.Builder {
    @Override
    Builder setDescription(String description);

    @Override
    Builder setUnit(String unit);

    @Override
    DoubleValueBatchObserver build();
  }
}
