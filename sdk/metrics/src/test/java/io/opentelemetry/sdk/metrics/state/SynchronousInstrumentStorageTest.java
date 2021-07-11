/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.CollectionHandle;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.aggregator.ExemplarSampler;
import io.opentelemetry.sdk.metrics.aggregator.SynchronousHandle;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import io.opentelemetry.sdk.metrics.view.AttributesProcessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SynchronousInstrumentStorageTest {

  private final CollectionHandle collector1 = CollectionHandle.create();
  private final CollectionHandle collector2 = CollectionHandle.create();
  private final Set<CollectionHandle> collectors = CollectionHandle.of(collector1, collector2);

  @Test
  @SuppressWarnings("unchecked")
  public void synchronousStorage_returnsNoMetricsWithNoData() {
    final Aggregator<Object> mockAggregator = Mockito.mock(Aggregator.class);
    SynchronousInstrumentStorage<Object> storage =
        SynchronousInstrumentStorage.create(mockAggregator, AttributesProcessor.NOOP);
    assertThat(storage.collectAndReset(collector1, collectors, 0, 10)).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void synchronousStorage_pullsHandlesFromAggregatorPerAttribute() {
    final Aggregator<Object> mockAggregator = Mockito.mock(Aggregator.class);
    final Attributes kv = Attributes.of(AttributeKey.stringKey("k"), "v");
    Mockito.when(mockAggregator.createStreamStorage()).thenAnswer(invocation -> new NoopHandle());
    SynchronousInstrumentStorage<Object> storage =
        SynchronousInstrumentStorage.create(mockAggregator, AttributesProcessor.NOOP);
    // Now we make sure we create N storage handles, and ensure the attributes
    // show up when we aggregate via `batchStreamAccumulate`.
    storage
        .bind(Attributes.empty())
        .record(LongMeasurement.create(1, Attributes.empty(), Context.root()));
    storage.bind(kv).record(LongMeasurement.create(1, kv, Context.root()));
    // Binding a handle with no value will NOT cause accumulation.
    storage.bind(Attributes.of(AttributeKey.stringKey("k"), "unused"));
    storage.collectAndReset(collector1, collectors, 0, 10);
    // Verify aggregator received measurements.
    Mockito.verify(mockAggregator)
        .buildMetric(makeMeasurement(kv, "result", Attributes.empty(), "result"), 0, 0, 10);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void synchronousStorage_usesAttributeProcessor() {
    final Aggregator<Object> mockAggregator = Mockito.mock(Aggregator.class);
    final Attributes kv = Attributes.of(AttributeKey.stringKey("k"), "v");
    final Attributes kv2 = Attributes.of(AttributeKey.stringKey("k"), "v2");
    Mockito.when(mockAggregator.createStreamStorage()).thenAnswer(invocation -> new NoopHandle());
    SynchronousInstrumentStorage<Object> storage =
        SynchronousInstrumentStorage.create(
            mockAggregator,
            (attributes, context) -> {
              if (attributes.equals(kv)) {
                return kv2;
              }
              return attributes;
            });
    // Now we make sure we create N storage handles, and ensure the attributes
    // show up when we aggregate via `batchStreamAccumulate`.
    storage
        .bind(Attributes.empty())
        .record(LongMeasurement.create(1, Attributes.empty(), Context.root()));
    storage.bind(kv).record(LongMeasurement.create(1, kv, Context.root()));
    // Binding a handle with no value will NOT cause accumulation.
    storage.bind(Attributes.of(AttributeKey.stringKey("k"), "unused"));
    storage.collectAndReset(collector1, collectors, 0, 10);
    Mockito.verify(mockAggregator)
        .buildMetric(makeMeasurement(kv2, "result", Attributes.empty(), "result"), 0, 0, 10);
  }

  /** Stubbed version of synchronous handle for testing. */
  private static class NoopHandle extends SynchronousHandle<Object> {
    NoopHandle() {
      super(ExemplarSampler.NEVER);
    }

    @Override
    protected Object doAccumulateThenReset(Iterable<Measurement> exemplars) {
      return "result";
    }

    @Override
    protected void doRecord(Measurement value) {}
  }

  private static Map<Attributes, Object> makeMeasurement(Object... values) {
    Map<Attributes, Object> result = new HashMap<>();
    int idx = 0;
    while (idx + 1 < values.length) {
      result.put((Attributes) values[idx], values[idx + 1]);
      idx += 2;
    }
    return result;
  }
}
