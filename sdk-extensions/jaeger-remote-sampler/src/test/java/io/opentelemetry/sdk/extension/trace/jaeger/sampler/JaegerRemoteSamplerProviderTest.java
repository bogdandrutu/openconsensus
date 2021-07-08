/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.trace.jaeger.sampler;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigurableSamplerProvider;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class JaegerRemoteSamplerProviderTest {

  @Test
  void serviceProvider() {
    ServiceLoader<ConfigurableSamplerProvider> samplerProviders =
        ServiceLoader.load(ConfigurableSamplerProvider.class);
    Iterator<ConfigurableSamplerProvider> iterator = samplerProviders.iterator();
    iterator.forEachRemaining(
        configurableSamplerProvider ->
            assertThat(configurableSamplerProvider)
                .isInstanceOf(JaegerRemoteSamplerProvider.class));
  }
}
