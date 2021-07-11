/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.metrics.view.View;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builder class for the {@link SdkMeterProvider}. Has fully functional default implementations of
 * all three required interfaces.
 */
public final class SdkMeterProviderBuilder {

  private Clock clock = Clock.getDefault();
  private Resource resource = Resource.getDefault();
  private MeasurementProcessor processor = DefaultMeasurementProcessor.builder().build();
  private final List<View> views = new ArrayList<>();

  SdkMeterProviderBuilder() {}

  /**
   * Assign a {@link Clock}.
   *
   * @param clock The clock to use for all temporal needs.
   * @return this
   */
  public SdkMeterProviderBuilder setClock(Clock clock) {
    Objects.requireNonNull(clock, "clock");
    this.clock = clock;
    return this;
  }

  /**
   * Assign a {@link Resource} to be attached to all Spans created by Tracers.
   *
   * @param resource A Resource implementation.
   * @return this
   */
  public SdkMeterProviderBuilder setResource(Resource resource) {
    Objects.requireNonNull(resource, "resource");
    this.resource = resource;
    return this;
  }

  /**
   * Assign a {@link MeasurementProcessor} for this SDK.
   *
   * @param processor The mechanism of converting measurements into metrics.
   * @return this
   */
  public SdkMeterProviderBuilder setMeasurementProcessor(MeasurementProcessor processor) {
    Objects.requireNonNull(processor, "processor");
    this.processor = processor;
    return this;
  }

  /**
   * Registers a new {@link View} for processing measurements.
   *
   * @return this
   */
  @SuppressWarnings("unused")
  public SdkMeterProviderBuilder registerView(View view) {
    views.add(view);
    return this;
  }

  /**
   * Returns a new {@link SdkMeterProvider} built with the configuration of this {@link
   * SdkMeterProviderBuilder} and registers it as the global {@link
   * io.opentelemetry.api.metrics.MeterProvider}.
   *
   * @see GlobalMeterProvider
   */
  public SdkMeterProvider buildAndRegisterGlobal() {
    SdkMeterProvider meterProvider = build();
    GlobalMeterProvider.set(meterProvider);
    return meterProvider;
  }

  /**
   * Returns a new {@link SdkMeterProvider} built with the configuration of this {@link
   * SdkMeterProviderBuilder}. This provider is not registered as the global {@link
   * io.opentelemetry.api.metrics.MeterProvider}. It is recommended that you register one provider
   * using {@link SdkMeterProviderBuilder#buildAndRegisterGlobal()} for use by instrumentation when
   * that requires access to a global instance of {@link
   * io.opentelemetry.api.metrics.MeterProvider}.
   *
   * @see GlobalMeterProvider
   */
  public SdkMeterProvider build() {
    return new SdkMeterProvider(clock, resource, processor, Collections.unmodifiableList(views));
  }
}
