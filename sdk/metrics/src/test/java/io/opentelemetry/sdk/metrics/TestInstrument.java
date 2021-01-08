/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;

final class TestInstrument extends AbstractInstrument {
  TestInstrument(InstrumentDescriptor descriptor) {
    super(descriptor);
  }

  @Override
  void collectAll(List<MetricData> output) {}
}
