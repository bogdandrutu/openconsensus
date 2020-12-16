/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.internal.ComponentRegistry;
import io.opentelemetry.sdk.internal.SystemClock;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** {@code Tracer} provider implementation for {@link TracerProvider}. */
public final class SdkTracerProvider implements TracerProvider, SdkTracerManagement {
  private static final Logger logger = Logger.getLogger(SdkTracerProvider.class.getName());
  static final String DEFAULT_TRACER_NAME = "unknown";
  private final TracerSharedState sharedState;
  private final ComponentRegistry<SdkTracer> tracerSdkComponentRegistry;

  /**
   * Returns a new {@link Builder} for {@link SdkTracerProvider}.
   *
   * @return a new {@link Builder} for {@link SdkTracerProvider}.
   */
  public static Builder builder() {
    return new Builder();
  }

  private SdkTracerProvider(
      Clock clock, IdGenerator idsGenerator, Resource resource, TraceConfig traceConfig) {
    this.sharedState = new TracerSharedState(clock, idsGenerator, resource, traceConfig);
    this.tracerSdkComponentRegistry =
        new ComponentRegistry<>(
            instrumentationLibraryInfo -> new SdkTracer(sharedState, instrumentationLibraryInfo));
  }

  @Override
  public Tracer get(String instrumentationName) {
    return get(instrumentationName, null);
  }

  @Override
  public Tracer get(String instrumentationName, @Nullable String instrumentationVersion) {
    // Per the spec, both null and empty are "invalid" and a "default" should be used.
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      logger.fine("Tracer requested without instrumentation name.");
      instrumentationName = DEFAULT_TRACER_NAME;
    }
    return tracerSdkComponentRegistry.get(instrumentationName, instrumentationVersion);
  }

  @Override
  public TraceConfig getActiveTraceConfig() {
    return sharedState.getActiveTraceConfig();
  }

  @Override
  public void updateActiveTraceConfig(TraceConfig traceConfig) {
    sharedState.updateActiveTraceConfig(traceConfig);
  }

  @Override
  public void addSpanProcessor(SpanProcessor spanProcessor) {
    sharedState.addSpanProcessor(spanProcessor);
  }

  @Override
  public void shutdown() {
    if (sharedState.isStopped()) {
      logger.log(Level.WARNING, "Calling shutdown() multiple times.");
      return;
    }
    sharedState.stop();
  }

  @Override
  public CompletableResultCode forceFlush() {
    return sharedState.getActiveSpanProcessor().forceFlush();
  }

  /**
   * Builder class for the TraceSdkProvider. Has fully functional default implementations of all
   * three required interfaces.
   */
  public static class Builder {

    private Clock clock = SystemClock.getInstance();
    private IdGenerator idsGenerator = IdGenerator.random();
    private Resource resource = Resource.getDefault();
    private TraceConfig traceConfig = TraceConfig.getDefault();

    /**
     * Assign a {@link Clock}.
     *
     * @param clock The clock to use for all temporal needs.
     * @return this
     */
    public Builder setClock(Clock clock) {
      Objects.requireNonNull(clock, "clock");
      this.clock = clock;
      return this;
    }

    /**
     * Assign an {@link IdGenerator}.
     *
     * @param idGenerator A generator for trace and span ids. Note: this should be thread-safe and
     *     as contention free as possible.
     * @return this
     */
    public Builder setIdGenerator(IdGenerator idGenerator) {
      Objects.requireNonNull(idGenerator, "idGenerator");
      this.idsGenerator = idGenerator;
      return this;
    }

    /**
     * Assign a {@link Resource} to be attached to all Spans created by Tracers.
     *
     * @param resource A Resource implementation.
     * @return this
     */
    public Builder setResource(Resource resource) {
      Objects.requireNonNull(resource, "resource");
      this.resource = resource;
      return this;
    }

    /**
     * Assign an initial {@link TraceConfig} that should be used with this SDK.
     *
     * @return this
     */
    public Builder setTraceConfig(TraceConfig traceConfig) {
      this.traceConfig = traceConfig;
      Objects.requireNonNull(traceConfig);
      return this;
    }

    /**
     * Create a new TraceSdkProvider instance.
     *
     * @return An initialized TraceSdkProvider.
     */
    public SdkTracerProvider build() {
      return new SdkTracerProvider(clock, idsGenerator, resource, traceConfig);
    }

    private Builder() {}
  }
}
