/*
 * Copyright 2019, OpenTelemetry Authors
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

package io.opentelemetry.contrib.spring.boot.actuate;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.opentelemetry.contrib.spring.boot.actuate.OpenTelemetryProperties.SamplerName;
import io.opentelemetry.sdk.trace.TracerSdkRegistry;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import org.junit.Test;

/** Unit tests for {@link TracerSdkRegistryBean}. */
public class TracerSdkRegistryBeanTest {

  @Test
  public void shouldConstructCustomSamplerIfConfigured() {
    OpenTelemetryProperties properties = new OpenTelemetryProperties();
    properties.getTracer().getSampler().setName(SamplerName.CUSTOM);
    properties
        .getTracer()
        .getSampler()
        .setImplClass("io.opentelemetry.contrib.spring.boot.actuate.TestOnlySampler");
    properties.getTracer().getSampler().getProperties().put("reservoir", "10");
    properties.getTracer().getSampler().getProperties().put("rate", "2.5");
    properties.getTracer().getSampler().getProperties().put("enabled", "true");
    TracerSdkRegistryBean factoryBean = new TracerSdkRegistryBean();
    factoryBean.setProperties(properties);
    factoryBean.afterPropertiesSet();
    TracerSdkRegistry tracerSdkRegistry = (TracerSdkRegistry) factoryBean.getObject();
    TraceConfig traceConfig = tracerSdkRegistry.getActiveTraceConfig();
    assertTrue(traceConfig.getSampler() instanceof TestOnlySampler);
  }

  @Test
  public void shouldUseInjectedSamplerIfPresent() {
    TestOnlySampler sampler = new TestOnlySampler();
    OpenTelemetryProperties properties = new OpenTelemetryProperties();
    TracerSdkRegistryBean factoryBean = new TracerSdkRegistryBean();
    factoryBean.setProperties(properties);
    factoryBean.setSampler(sampler);
    factoryBean.afterPropertiesSet();
    TracerSdkRegistry tracerSdkRegistry = (TracerSdkRegistry) factoryBean.getObject();
    TraceConfig traceConfig = tracerSdkRegistry.getActiveTraceConfig();
    assertSame(sampler, traceConfig.getSampler());
  }
}
