/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Labels;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorHandle;
import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;

abstract class AbstractSynchronousInstrument extends AbstractInstrument {
  private final SynchronousInstrumentAccumulator<?> accumulator;

  AbstractSynchronousInstrument(
      InstrumentDescriptor descriptor, SynchronousInstrumentAccumulator<?> accumulator) {
    super(descriptor);
    this.accumulator = accumulator;
  }

  @Override
  final void collectAll(List<MetricData> output) {
    accumulator.collectAll(output);
  }

  AggregatorHandle<?> acquireHandle(Labels labels) {
    return accumulator.bind(labels);
  }
}
