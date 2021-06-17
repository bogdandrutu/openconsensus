/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api;

import io.opentelemetry.api.internal.GuardedBy;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A global singleton for the entrypoint to telemetry functionality for tracing, metrics and
 * baggage.
 *
 * <p>If using the OpenTelemetry SDK, you may want to instantiate the {@link OpenTelemetry} to
 * provide configuration, for example of {@code Resource} or {@code Sampler}. See {@code
 * OpenTelemetrySdk} and {@code OpenTelemetrySdk.builder} for information on how to construct the
 * SDK {@link OpenTelemetry}.
 *
 * @see TracerProvider
 * @see ContextPropagators
 */
public final class GlobalOpenTelemetry {

  private static final Logger logger = Logger.getLogger(GlobalOpenTelemetry.class.getName());

  private static final boolean suppressSdkCheck =
      Boolean.getBoolean("otel.sdk.suppress-sdk-initialized-warning");

  private static final Object mutex = new Object();

  @Nullable private static volatile ObfuscatedOpenTelemetry globalOpenTelemetry;

  @GuardedBy("mutex")
  @Nullable
  private static Throwable setGlobalCaller;

  private GlobalOpenTelemetry() {}

  /**
   * Returns the registered global {@link OpenTelemetry}.
   *
   * @throws IllegalStateException if a provider has been specified by system property using the
   *     interface FQCN but the specified provider cannot be found.
   */
  public static OpenTelemetry get() {
    if (globalOpenTelemetry == null) {
      synchronized (mutex) {
        if (globalOpenTelemetry == null) {

          OpenTelemetry autoConfigured = maybeAutoConfigure();
          if (autoConfigured != null) {
            return autoConfigured;
          }

          if (!suppressSdkCheck) {
            SdkChecker.logIfSdkFound();
          }

          set(OpenTelemetry.noop());
          return OpenTelemetry.noop();
        }
      }
    }
    return globalOpenTelemetry;
  }

  /**
   * Sets the {@link OpenTelemetry} that should be the global instance. Future calls to {@link
   * #get()} will return the provided {@link OpenTelemetry} instance. This should be called once as
   * early as possible in your application initialization logic, often in a {@code static} block in
   * your main class. It should only be called once - an attempt to call it a second time will
   * result in an error. If trying to set the global {@link OpenTelemetry} multiple times in tests,
   * use {@link GlobalOpenTelemetry#resetForTest()} between them.
   *
   * <p>If you are using the OpenTelemetry SDK, you should generally use {@code
   * OpenTelemetrySdk.builder().buildAndRegisterGlobal()} instead of calling this method directly.
   */
  public static void set(OpenTelemetry openTelemetry) {
    synchronized (mutex) {
      if (globalOpenTelemetry != null) {
        throw new IllegalStateException(
            "GlobalOpenTelemetry.set has already been called. GlobalOpenTelemetry.set must be "
                + "called only once before any calls to GlobalOpenTelemetry.get. If you are using "
                + "the OpenTelemetrySdk, use OpenTelemetrySdkBuilder.buildAndRegisterGlobal "
                + "instead. Previous invocation set to cause of this exception.",
            setGlobalCaller);
      }
      globalOpenTelemetry = new ObfuscatedOpenTelemetry(openTelemetry);
      setGlobalCaller = new Throwable();
    }
  }

  /** Returns the globally registered {@link TracerProvider}. */
  public static TracerProvider getTracerProvider() {
    return get().getTracerProvider();
  }

  /**
   * Gets or creates a named tracer instance from the globally registered {@link TracerProvider}.
   *
   * <p>This is a shortcut method for {@code getTracerProvider().get(instrumentationName)}
   *
   * @param instrumentationName The name of the instrumentation library, not the name of the
   *     instrument*ed* library (e.g., "io.opentelemetry.contrib.mongodb"). Must not be null.
   * @return a tracer instance.
   */
  public static Tracer getTracer(String instrumentationName) {
    return get().getTracer(instrumentationName);
  }

  /**
   * Gets or creates a named and versioned tracer instance from the globally registered {@link
   * TracerProvider}.
   *
   * <p>This is a shortcut method for {@code getTracerProvider().get(instrumentationName,
   * instrumentationVersion)}
   *
   * @param instrumentationName The name of the instrumentation library, not the name of the
   *     instrument*ed* library (e.g., "io.opentelemetry.contrib.mongodb"). Must not be null.
   * @param instrumentationVersion The version of the instrumentation library (e.g., "1.0.0").
   * @return a tracer instance.
   */
  public static Tracer getTracer(String instrumentationName, String instrumentationVersion) {
    return get().getTracer(instrumentationName, instrumentationVersion);
  }

  /**
   * Creates a TracerBuilder for a named {@link Tracer} instance.
   *
   * <p>This is a shortcut method for {@code get().tracerBuilder(instrumentationName)}
   *
   * @param instrumentationName The name of the instrumentation library, not the name of the
   *     instrument*ed* library.
   * @return a TracerBuilder instance.
   * @since 1.4.0
   */
  public static TracerBuilder tracerBuilder(String instrumentationName) {
    return get().tracerBuilder(instrumentationName);
  }

  /**
   * Unsets the global {@link OpenTelemetry}. This is only meant to be used from tests which need to
   * reconfigure {@link OpenTelemetry}.
   */
  public static void resetForTest() {
    globalOpenTelemetry = null;
  }

  /**
   * Returns the globally registered {@link ContextPropagators} for remote propagation of a context.
   */
  public static ContextPropagators getPropagators() {
    return get().getPropagators();
  }

  @Nullable
  private static OpenTelemetry maybeAutoConfigure() {
    final Class<?> openTelemetrySdkAutoConfiguration;
    try {
      openTelemetrySdkAutoConfiguration =
          Class.forName("io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration");
    } catch (ClassNotFoundException e) {
      return null;
    }

    try {
      Method initialize = openTelemetrySdkAutoConfiguration.getMethod("initialize");
      return (OpenTelemetry) initialize.invoke(null);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(
          "OpenTelemetrySdkAutoConfiguration detected on classpath "
              + "but could not invoke initialize method. This is a bug in OpenTelemetry.",
          e);
    } catch (InvocationTargetException t) {
      logger.log(
          Level.SEVERE,
          "Error automatically configuring OpenTelemetry SDK. OpenTelemetry will not be enabled.",
          t.getTargetException());
      return null;
    }
  }

  // Use an inner class that checks and logs in its static initializer to have log-once behavior
  // using initial classloader lock and no further runtime locks or atomics.
  private static class SdkChecker {
    static {
      boolean hasSdk = false;
      try {
        Class.forName("io.opentelemetry.sdk.OpenTelemetrySdk");
        hasSdk = true;
      } catch (Throwable t) {
        // Ignore
      }

      if (hasSdk) {
        logger.log(
            Level.SEVERE,
            "Attempt to access GlobalOpenTelemetry.get before OpenTelemetrySdk has been "
                + "initialized. This generally means telemetry will not be recorded for parts of "
                + "your application. Make sure to initialize OpenTelemetrySdk, using "
                + "OpenTelemetrySdk.builder()...buildAndRegisterGlobal(), as early as possible in "
                + "your application. If you do not need to use the OpenTelemetry SDK, either "
                + "exclude it from your classpath or set the "
                + "'otel.sdk.suppress-sdk-initialized-warning' system property to true.",
            // Add stack trace to log to allow user to find the problematic invocation.
            new Throwable());
      }
    }

    // All the logic is in the static initializer, this method is called just to load the class and
    // that's it. JVM will then optimize it away completely because it's empty so we have no
    // overhead for a log-once pattern.
    static void logIfSdkFound() {}
  }

  /**
   * Static global instances are obfuscated when they are returned from the API to prevent users
   * from casting them to their SDK-specific implementation. For example, we do not want users to
   * use patterns like {@code (OpenTelemetrySdk) GlobalOpenTelemetry.get()}.
   */
  @ThreadSafe
  static class ObfuscatedOpenTelemetry implements OpenTelemetry {

    private final OpenTelemetry delegate;

    ObfuscatedOpenTelemetry(OpenTelemetry delegate) {
      this.delegate = delegate;
    }

    @Override
    public TracerProvider getTracerProvider() {
      return delegate.getTracerProvider();
    }

    @Override
    public ContextPropagators getPropagators() {
      return delegate.getPropagators();
    }
  }
}
