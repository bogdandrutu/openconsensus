/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.internal.GuardedBy;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.internal.ComponentRegistry;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.metrics.state.MeterProviderSharedState;
import io.opentelemetry.sdk.metrics.view.View;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * {@code SdkMeterProvider} implementation for {@link MeterProvider}.
 *
 * <p>This class is not intended to be used in application code and it is used only by {@link
 * OpenTelemetry}.
 */
public class SdkMeterProvider implements MeterProvider {

  private static final Logger LOGGER = Logger.getLogger(SdkMeterProvider.class.getName());
  static final String DEFAULT_METER_NAME = "unknown";
  private final ComponentRegistry<SdkMeter> registry;
  private final MeterProviderSharedState sharedState;
  private final ReentrantLock collectorsLock = new ReentrantLock();

  @GuardedBy("collectorsLock")
  private final Set<CollectionHandle> collectors = CollectionHandle.mutableSet();

  SdkMeterProvider(
      Clock clock, Resource resource, MeasurementProcessor processor, List<View> views) {
    this.sharedState = MeterProviderSharedState.create(clock, resource, processor, views);
    this.registry =
        new ComponentRegistry<>(
            instrumentationLibraryInfo -> new SdkMeter(sharedState, instrumentationLibraryInfo));
  }

  @Override
  public MeterBuilder meterBuilder(String instrumentationName) {
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      LOGGER.fine("Meter requested without instrumentation name.");
      instrumentationName = DEFAULT_METER_NAME;
    }
    return new MyMeterBuilder(instrumentationName);
  }

  // TODO - This should be static, and not dynamically registered.

  /**
   * Construct a new MetricProducer that will collect + export metrics.
   *
   * <p>{@link MetricProducer} instances are isolated from each other, and can independently sample
   * metrics from this {@link MeterProvider}. However, all metric data will be cached until read by
   * all {@link MetricProducer}s that have been created from this {@link MeterProvider}.
   *
   * @return A mechanism of pulling metrics.
   */
  public MetricProducer newMetricProducer() {
    collectorsLock.lock();
    try {
      CollectionHandle handle = CollectionHandle.create();
      collectors.add(handle);
      return new LeasedMetricProducer(handle);
    } finally {
      collectorsLock.unlock();
    }
  }

  /** Helper class to expose registered metric exports. */
  private class LeasedMetricProducer implements MetricProducer {
    private final CollectionHandle handle;

    LeasedMetricProducer(CollectionHandle handle) {
      this.handle = handle;
    }

    @Override
    public Collection<MetricData> collectAllMetrics() {
      Collection<SdkMeter> meters = registry.getComponents();
      List<MetricData> result = new ArrayList<>(meters.size());
      Set<CollectionHandle> allCollectors;
      collectorsLock.lock();
      try {
        allCollectors = collectors;
      } finally {
        collectorsLock.unlock();
      }
      for (SdkMeter meter : meters) {
        result.addAll(meter.collectAll(handle, allCollectors, sharedState.getClock().now()));
      }
      return Collections.unmodifiableCollection(result);
    }
  }

  /** Implementation of MeterBuilder that registers on this provider. */
  private class MyMeterBuilder implements MeterBuilder {
    private final String instrumentationName;
    private String instrumentationVersion;
    private String schemaUrl;

    public MyMeterBuilder(String name) {
      this.instrumentationName = name;
    }

    @Override
    public MeterBuilder setSchemaUrl(String schemaUrl) {
      this.schemaUrl = schemaUrl;
      return this;
    }

    @Override
    public final MeterBuilder setInstrumentationVersion(String instrumentationVersion) {
      this.instrumentationVersion = instrumentationVersion;
      return this;
    }

    @Override
    public final Meter build() {
      return registry.get(instrumentationName, instrumentationVersion, schemaUrl);
    }
  }

  public static SdkMeterProviderBuilder builder() {
    return new SdkMeterProviderBuilder();
  }
}
