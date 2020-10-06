/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.metrics;

import io.opentelemetry.metrics.AsynchronousInstrument.LongResult;

public interface LongAsynchronousInstrument extends AsynchronousInstrument<LongResult> {
  @Override
  void setCallback(Callback<LongResult> callback);

  Observation observation(long observation);
}
