/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class ConfigurableSamplerTest {

  @Test
  void configuration() {
    DefaultConfigProperties config =
        DefaultConfigProperties.createForTest(ImmutableMap.of("test.option", "true"));
    Sampler sampler = TracerProviderConfiguration.configureSampler("testSampler", config);

    assertThat(sampler)
        .isInstanceOfSatisfying(
            TestConfigurableSamplerProvider.TestSampler.class,
            s -> assertThat(s.getConfig()).isSameAs(config));
  }

  @Test
  void samplerNotFound() {
    assertThatThrownBy(
            () ->
                TracerProviderConfiguration.configureSampler(
                    "catSampler", DefaultConfigProperties.createForTest(Collections.emptyMap())))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("catSampler");
  }
}
