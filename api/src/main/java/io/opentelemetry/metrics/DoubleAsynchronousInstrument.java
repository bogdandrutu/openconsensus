/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.metrics;

import io.opentelemetry.metrics.AsynchronousInstrument.DoubleResult;

public interface DoubleAsynchronousInstrument extends AsynchronousInstrument<DoubleResult> {
  @Override
  void setCallback(Callback<DoubleResult> callback);

  Observation observation(double observation);
}
