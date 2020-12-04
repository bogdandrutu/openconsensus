/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics.batch;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public interface LongValueBatchObserver extends AsynchronousBatchInstrument {
  Observation observation(long result);

  interface Builder extends AsynchronousBatchInstrument.Builder {
    @Override
    Builder setDescription(String description);

    @Override
    Builder setUnit(String unit);

    @Override
    LongValueBatchObserver build();
  }
}
