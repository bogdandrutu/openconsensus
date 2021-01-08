/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Labels;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorHandle;
import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.common.InstrumentValueType;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.view.AggregationConfiguration;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class SynchronousInstrumentAccumulatorTest {
  private static final InstrumentDescriptor DESCRIPTOR =
      InstrumentDescriptor.create(
          "name", "description", "unit", InstrumentType.COUNTER, InstrumentValueType.DOUBLE);
  private final MeterProviderSharedState providerSharedState =
      MeterProviderSharedState.create(TestClock.create(), Resource.getEmpty());
  private final MeterSharedState meterSharedState =
      MeterSharedState.create(InstrumentationLibraryInfo.create("test", "1.0"));

  @Test
  void sameAggregator_ForSameLabelSet() {
    SynchronousInstrumentAccumulator<?> accumulator =
        new SynchronousInstrumentAccumulator<>(
            InstrumentProcessor.createProcessor(
                providerSharedState,
                meterSharedState,
                DESCRIPTOR,
                AggregationConfiguration.create(
                    AggregatorFactory.count(), MetricData.AggregationTemporality.CUMULATIVE)));
    AggregatorHandle<?> aggregatorHandle = accumulator.bind(Labels.of("K", "V"));
    AggregatorHandle<?> duplicateAggregatorHandle = accumulator.bind(Labels.of("K", "V"));
    try {
      assertThat(duplicateAggregatorHandle).isSameAs(aggregatorHandle);
      accumulator.collectAll(new ArrayList<>());
      AggregatorHandle<?> anotherDuplicateAggregatorHandle = accumulator.bind(Labels.of("K", "V"));
      try {
        assertThat(anotherDuplicateAggregatorHandle).isSameAs(aggregatorHandle);
      } finally {
        anotherDuplicateAggregatorHandle.release();
      }
    } finally {
      duplicateAggregatorHandle.release();
      aggregatorHandle.release();
    }

    // At this point we should be able to unmap because all references are gone. Because this is an
    // internal detail we cannot call collectAll after this anymore.
    assertThat(aggregatorHandle.tryUnmap()).isTrue();
  }
}
