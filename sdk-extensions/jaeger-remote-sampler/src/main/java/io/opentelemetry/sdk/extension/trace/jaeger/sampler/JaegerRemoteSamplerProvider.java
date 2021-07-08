/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.trace.jaeger.sampler;

import io.opentelemetry.sdk.autoconfigure.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurableSamplerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

public class JaegerRemoteSamplerProvider implements ConfigurableSamplerProvider {

  @Override
  public Sampler createSampler(ConfigProperties config) {
    String serviceName = config.getString("otel.service.name");
    JaegerRemoteSamplerBuilder builder = JaegerRemoteSampler.builder().setServiceName(serviceName);

    // Optional configuration
    String endpoint = config.getString("otel.exporter.jaeger.endpoint");
    if (endpoint != null) {
      builder.setEndpoint(endpoint);
    }
    // TODO (pavolloffay) add initial rate and pooling interval once they are added to the spec.
    return builder.build();
  }

  @Override
  public String getName() {
    return "jaeger_remote_sampler";
  }
}
