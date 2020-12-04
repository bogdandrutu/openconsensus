/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.api.common.Labels;
import io.opentelemetry.api.metrics.batch.DoubleValueBatchObserver;
import io.opentelemetry.api.metrics.batch.LongValueBatchObserver;
import org.junit.jupiter.api.Test;

public class BatchObserverTest {
  private static final Meter meter = Meter.getDefault();
  private static final Labels labels = Labels.of("key", "value");

  @Test
  void preventNull_ValueLong() {
    assertThrows(
        NullPointerException.class,
        () -> meter.newBatchObserver(context -> context.longValueObserverBuilder(null)),
        "name");
  }

  @Test
  void preventNull_ValueDouble() {
    assertThrows(
        NullPointerException.class,
        () -> meter.newBatchObserver(context -> context.doubleValueObserverBuilder(null)),
        "name");
  }

  @Test
  void testNewBatchObserver_acceptNullFunction() {
    meter.newBatchObserver(
        context -> {
          context.registerCallback(null);
        });
  }

  @Test
  void doesNotThrow() {
    meter.newBatchObserver(
        context -> {
          DoubleValueBatchObserver doubleValueObserver =
              context.doubleValueObserverBuilder("doubleValueObserver").build();
          LongValueBatchObserver longValueObserver =
              context.longValueObserverBuilder("longValueObserver").build();
          // TODO Add the other types

          context.registerCallback(
              result ->
                  result.observe(
                      labels,
                      doubleValueObserver.observation(77.56d),
                      longValueObserver.observation(54L)));
        });
  }
}
