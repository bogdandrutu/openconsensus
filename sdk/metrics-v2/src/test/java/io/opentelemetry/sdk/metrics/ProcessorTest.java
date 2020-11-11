/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class ProcessorTest {

  private final InstrumentationLibraryInfo instrumentationLibraryInfo =
      InstrumentationLibraryInfo.create("test", "1.0");

  @Test
  void processCycle() {
    TestClock clock = TestClock.create();
    Processor processor = new Processor(Resource.create(Attributes.empty()), clock);
    processor.start();

    AggregatorKey key =
        AggregatorKey.create(
            instrumentationLibraryInfo,
            InstrumentDescriptor.create(
                "one", "desc", "1", InstrumentType.COUNTER, InstrumentValueType.LONG),
            Labels.empty());

    processor.process(key, LongAccumulation.create(clock.now(), 35L));

    Collection<MetricData> result = processor.finish();

    System.out.println("result = " + result);
  }
}
