/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import io.opentelemetry.api.metrics.internal.NoopMeterProvider;
import java.util.concurrent.atomic.AtomicReference;

/** This class is a temporary solution until metrics SDK is marked stable. */
public class GlobalMeterProvider {
  private static final AtomicReference<MeterProvider> globalMeterProvider =
      new AtomicReference<>(NoopMeterProvider.getInstance());

  private GlobalMeterProvider() {}

  /** Returns the globally registered {@link MeterProvider}. */
  public static MeterProvider get() {
    // Note: AtomicRef.get provides memory barrier.
    // Until we run autoconfigure here, we don't need more.
    return globalMeterProvider.get();
  }
  /**
   * Sets the {@link MeterProvider} that should be the global instance. Future calls to {@link
   * #get()} will return the provided {@link MeterProvider} instance. This should be called once as
   * early as possible in your application initialization logic, often in a {@code static} block in
   * your main class.
   */
  public static void set(MeterProvider provider) {
    // Note: `get()` involves a memory barrier which will flush write queue.
    globalMeterProvider.lazySet(provider == null ? NoopMeterProvider.getInstance() : provider);
  }
}
