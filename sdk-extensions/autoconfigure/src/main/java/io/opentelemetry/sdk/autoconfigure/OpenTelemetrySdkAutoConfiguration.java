/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.SdkMeterProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Auto-configuration for the OpenTelemetry SDK. As an alternative to programmatically configuring
 * the SDK using {@link OpenTelemetrySdk#builder()}, this package can be used to automatically
 * configure the SDK using environment properties specified by OpenTelemetry.
 */
public final class OpenTelemetrySdkAutoConfiguration {

  private static final Resource RESOURCE = buildResource();

  /** Returns the automatically configured {@link Resource}. */
  public static Resource getResource() {
    return RESOURCE;
  }

  /**
   * Returns an {@link OpenTelemetrySdk} automatically initialized through recognized system
   * properties and environment variables.
   */
  public static OpenTelemetrySdk initialize() {
    ConfigProperties config = DefaultConfigProperties.get();
    ContextPropagators propagators = PropagatorConfiguration.configurePropagators(config);

    Resource resource = getResource();

    configureMeterProvider(resource, config);

    SdkTracerProvider tracerProvider =
        TracerProviderConfiguration.configureTracerProvider(resource, config);

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setPropagators(propagators)
        .buildAndRegisterGlobal();
  }

  private static void configureMeterProvider(Resource resource, ConfigProperties config) {
    SdkMeterProviderBuilder meterProviderBuilder = SdkMeterProvider.builder().setResource(resource);

    for (SdkMeterProviderConfigurer configurer :
        ServiceLoader.load(SdkMeterProviderConfigurer.class)) {
      configurer.configure(meterProviderBuilder, config);
    }

    SdkMeterProvider meterProvider = meterProviderBuilder.buildAndRegisterGlobal();

    String exporterName = config.getString("otel.metrics.exporter");
    if (exporterName == null) {
      exporterName = "otlp";
    }
    MetricExporterConfiguration.configureExporter(exporterName, config, meterProvider);
  }

  private static Resource buildResource() {
    DefaultConfigProperties config = DefaultConfigProperties.get();
    Resource result = Resource.getDefault();

    // TODO(anuraaga): We use a hyphen only once in this artifact, for
    // otel.java.disabled.resource-providers. But fetching by the dot version is the simplest way
    // to implement it for now.
    Set<String> disabledProviders =
        new HashSet<>(config.getCommaSeparatedValues("otel.java.disabled.resource.providers"));
    for (ResourceProvider resourceProvider : ServiceLoader.load(ResourceProvider.class)) {
      if (disabledProviders.contains(resourceProvider.getClass().getName())) {
        continue;
      }
      result = result.merge(resourceProvider.createResource(config));
    }

    result = result.merge(EnvironmentResource.create(config));

    return result;
  }

  private OpenTelemetrySdkAutoConfiguration() {}
}
