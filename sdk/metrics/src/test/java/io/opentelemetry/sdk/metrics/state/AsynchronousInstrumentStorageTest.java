/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.state;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.CollectionHandle;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.instrument.DoubleMeasurement;
import io.opentelemetry.sdk.metrics.view.AttributesProcessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class AsynchronousInstrumentStorageTest {
  final CollectionHandle collector = CollectionHandle.create();
  final CollectionHandle collector2 = CollectionHandle.create();
  final Set<CollectionHandle> collectors = CollectionHandle.of(collector, collector2);

  @Test
  @SuppressWarnings("unchecked")
  public void asynchronousStorage_usesAttributesProcessor() {

    final Aggregator<Double> mockAggregator = Mockito.mock(Aggregator.class);
    AsynchronousInstrumentStorage<Double> storage =
        AsynchronousInstrumentStorage.create(
            (ObservableDoubleMeasurement measure) ->
                measure.observe(1.0, Attributes.of(stringKey("k"), "v")),
            mockAggregator,
            (attributes, context) -> Attributes.empty());
    storage.collectAndReset(collector, collectors, 0, 0);
    Mockito.verify(mockAggregator)
        .asyncAccumulation(DoubleMeasurement.create(1.0, Attributes.empty(), Context.root()));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void asynchronousStorage_sendsMeasurementsToAggregatorAndCompletes() {
    final Aggregator<Double> mockAggregator = Mockito.mock(Aggregator.class);
    final Attributes kv = Attributes.of(stringKey("k"), "v");
    // Count every measruement as "1".
    Mockito.when(mockAggregator.asyncAccumulation(Mockito.anyObject())).thenReturn(1d);
    AsynchronousInstrumentStorage<Double> storage =
        AsynchronousInstrumentStorage.create(
            (ObservableDoubleMeasurement measure) -> measure.observe(1.0, kv),
            mockAggregator,
            AttributesProcessor.NOOP);
    storage.collectAndReset(collector, collectors, 0, 0);

    // Verify aggregator received mesurement and completion timestmap.
    Mockito.verify(mockAggregator)
        .asyncAccumulation(
            DoubleMeasurement.create(1.0, Attributes.of(stringKey("k"), "v"), Context.current()));
    Map<Attributes, Double> expectedMeasurements = new HashMap<>();
    expectedMeasurements.put(kv, 1d);
    Mockito.verify(mockAggregator).buildMetric(expectedMeasurements, 0, 0, 0);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void asynchronousStorage_remembersPastMeasurementsForOneCollector() {
    final Aggregator<Double> mockAggregator = Mockito.mock(Aggregator.class);
    final Attributes kv = Attributes.of(stringKey("k"), "v");
    // Count every measruement as "1".
    Mockito.when(mockAggregator.asyncAccumulation(Mockito.anyObject())).thenReturn(1d);
    Mockito.when(
            mockAggregator.diffPrevious(
                Mockito.anyObject(), Mockito.anyObject(), Mockito.anyBoolean()))
        .thenAnswer(
            i -> {
              // TODO: less gross add method.
              final Map<Attributes, Double> previous = i.getArgument(0);
              final Map<Attributes, Double> current = i.getArgument(1);
              final Map<Attributes, Double> result = new HashMap<>();
              previous.forEach(result::put);
              current.forEach(
                  (k, v) -> {
                    if (result.containsKey(k)) {
                      result.put(k, v + result.get(k));
                    } else {
                      result.put(k, v);
                    }
                  });
              return result;
            });
    AsynchronousInstrumentStorage<Double> storage =
        AsynchronousInstrumentStorage.create(
            (ObservableDoubleMeasurement measure) -> measure.observe(1.0, kv),
            mockAggregator,
            AttributesProcessor.NOOP);
    storage.collectAndReset(collector, collectors, 0, 0);

    // Verify aggregator received mesurement and completion timestmap.
    Mockito.verify(mockAggregator)
        .asyncAccumulation(
            DoubleMeasurement.create(1.0, Attributes.of(stringKey("k"), "v"), Context.current()));
    Map<Attributes, Double> expectedMeasurements = new HashMap<>();
    expectedMeasurements.put(kv, 1d);
    Mockito.verify(mockAggregator).buildMetric(expectedMeasurements, 0, 0, 0);

    // Now run a second time, expecting a diff with previous to double the count.
    storage.collectAndReset(collector, collectors, 0, 10);
    Map<Attributes, Double> expectedMeasurements2 = new HashMap<>();
    expectedMeasurements2.put(kv, 2d);
    Mockito.verify(mockAggregator).buildMetric(expectedMeasurements2, 0, 0, 10);

    // Every time we "diff" one recording with another, we expect to add ju8st the previous,
    // receiving 2 again.
    storage.collectAndReset(collector, collectors, 0, 20);
    Map<Attributes, Double> expectedMeasurements3 = new HashMap<>();
    expectedMeasurements3.put(kv, 2d);
    Mockito.verify(mockAggregator).buildMetric(expectedMeasurements3, 0, 0, 20);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void asynchronousStorage_keepCollectorsSeparate() {
    final Aggregator<Double> mockAggregator = Mockito.mock(Aggregator.class);
    final Attributes kv = Attributes.of(stringKey("k"), "v");
    // Count every measruement as "1".
    Mockito.when(mockAggregator.asyncAccumulation(Mockito.anyObject())).thenReturn(1d);
    Mockito.when(
            mockAggregator.diffPrevious(
                Mockito.anyObject(), Mockito.anyObject(), Mockito.anyBoolean()))
        .thenAnswer(
            i -> {
              // TODO: less gross add method.
              final Map<Attributes, Double> previous = i.getArgument(0);
              final Map<Attributes, Double> current = i.getArgument(1);
              final Map<Attributes, Double> result = new HashMap<>();
              previous.forEach(result::put);
              current.forEach(
                  (k, v) -> {
                    if (result.containsKey(k)) {
                      result.put(k, v + result.get(k));
                    } else {
                      result.put(k, v);
                    }
                  });
              return result;
            });
    AsynchronousInstrumentStorage<Double> storage =
        AsynchronousInstrumentStorage.create(
            (ObservableDoubleMeasurement measure) -> measure.observe(1.0, kv),
            mockAggregator,
            AttributesProcessor.NOOP);
    storage.collectAndReset(collector, collectors, 0, 0);

    // Verify aggregator received mesurement and completion timestmap.
    Mockito.verify(mockAggregator)
        .asyncAccumulation(
            DoubleMeasurement.create(1.0, Attributes.of(stringKey("k"), "v"), Context.current()));
    Map<Attributes, Double> expectedMeasurements = new HashMap<>();
    expectedMeasurements.put(kv, 1d);
    Mockito.verify(mockAggregator).buildMetric(expectedMeasurements, 0, 0, 0);
    // Now call for a different collector and make sure we don't see previous values of the first.
    storage.collectAndReset(collector2, collectors, 0, 10);
    Mockito.verify(mockAggregator).buildMetric(expectedMeasurements, 0, 0, 10);
  }
}
