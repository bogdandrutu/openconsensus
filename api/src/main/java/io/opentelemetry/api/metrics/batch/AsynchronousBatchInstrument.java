/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics.batch;

import io.opentelemetry.api.metrics.Instrument;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public interface AsynchronousBatchInstrument extends Instrument {
  interface Builder extends Instrument.Builder {
    @Override
    AsynchronousBatchInstrument build();
  }

  interface Observation {}
}
