/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurableSamplerProvider;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;

public class TestConfigurableSamplerProvider implements ConfigurableSamplerProvider {
  @Override
  public Sampler createSampler(ConfigProperties config) {
    return new TestSampler(config);
  }

  @Override
  public String getName() {
    return "testSampler";
  }

  public static class TestSampler implements Sampler {

    private final ConfigProperties config;

    public TestSampler(ConfigProperties config) {
      this.config = config;
    }

    @Override
    public SamplingResult shouldSample(
        Context parentContext,
        String traceId,
        String name,
        SpanKind spanKind,
        Attributes attributes,
        List<LinkData> parentLinks) {
      return SamplingResult.create(SamplingDecision.RECORD_AND_SAMPLE);
    }

    @Override
    public String getDescription() {
      return "test";
    }

    public ConfigProperties getConfig() {
      return config;
    }
  }
}
