/*
 * Copyright 2019, OpenConsensus Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package openconsensus.contrib.metrics.runtime;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import openconsensus.metrics.CounterLong;
import openconsensus.metrics.LabelKey;
import openconsensus.metrics.LabelValue;
import openconsensus.metrics.Meter;
import openconsensus.metrics.Metrics;

/**
 * Exports metrics about JVM garbage collectors.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * new GarbageCollector().exportAll();
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   jvm_gc_collection{gc="PS1"} 6.7
 * </pre>
 */
public final class GarbageCollector {
  private static final LabelKey GC = LabelKey.create("gc", "");

  private final List<GarbageCollectorMXBean> garbageCollectors;
  private final Meter meter;

  /** Constructs a new module that is capable to export metrics about "jvm_gc". */
  public GarbageCollector() {
    this.garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans();
    this.meter = Metrics.getMeter();
  }

  /** Export all metrics generated by this module. */
  public void exportAll() {
    // TODO: This should probably be a cumulative Histogram without buckets (or Summary without
    //  percentiles) to allow count/sum.
    final CounterLong collectionMetric =
        meter
            .counterLongBuilder("collection")
            .setDescription("Time spent in a given JVM garbage collector in milliseconds.")
            .setUnit("ms")
            .setLabelKeys(Collections.singletonList(GC))
            .setComponent("jvm_gc")
            .build();
    collectionMetric.setCallback(
        new Runnable() {
          @Override
          public void run() {
            for (final GarbageCollectorMXBean gc : garbageCollectors) {
              LabelValue gcName = LabelValue.create(gc.getName());
              collectionMetric
                  .getOrCreateTimeSeries(Collections.singletonList(gcName))
                  .set(gc.getCollectionTime());
            }
          }
        });
  }
}
