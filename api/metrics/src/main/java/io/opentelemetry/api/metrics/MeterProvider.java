/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.metrics.internal.NoopMeterProvider;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A registry for creating named {@link Meter}s.
 *
 * <p>A MeterProvider represents a configured (or noop) Metric collection system that can be used to
 * instrument code.
 *
 * <p>The name <i>Provider</i> is for consistency with other languages and it is <b>NOT</b> loaded
 * using reflection.
 *
 * @see io.opentelemetry.api.metrics.Meter
 */
@ThreadSafe
public interface MeterProvider {
  /**
   * Gets or creates a named and versioned meter instance.
   *
   * @param instrumentationName The name of the instrumentation library, not the name of the
   *     instrument*ed* library.
   * @return a meter instance.
   */
  default Meter get(String instrumentationName) {
    return meterBuilder(instrumentationName).build();
  }

  /**
   * Gets or creates a named and versioned meter instance.
   *
   * @param instrumentationName The name of the instrumentation library, not the name of the
   *     instrument*ed* library.
   * @param instrumentationVersion The version of the instrumentation library.
   * @param schemaUrl Specifies the Schema URL that should be recorded in the emitted metrics.
   * @return a meter instance.
   */
  default Meter get(String instrumentationName, String instrumentationVersion, String schemaUrl) {
    return meterBuilder(instrumentationName)
        .setInstrumentationVersion(instrumentationVersion)
        .setSchemaUrl(schemaUrl)
        .build();
  }

  /**
   * Creates a MeterBuilder for a named meter instance.
   *
   * @param instrumentationName The name of the instrumentation library, not the name of the
   *     instrument*ed* library.
   * @return a MeterBuilder instance.
   * @since 1.4.0
   */
  MeterBuilder meterBuilder(String instrumentationName);

  public static MeterProvider noop() {
    return NoopMeterProvider.getInstance();
  }
}
