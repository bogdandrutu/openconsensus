/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.baggage.Baggage;
import io.opentelemetry.baggage.BaggageManager;
import io.opentelemetry.baggage.DefaultBaggageManager;
import io.opentelemetry.baggage.spi.BaggageManagerFactory;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.metrics.BatchObserver;
import io.opentelemetry.metrics.BatchRecorder;
import io.opentelemetry.metrics.DefaultMeterProvider;
import io.opentelemetry.metrics.DoubleCounter;
import io.opentelemetry.metrics.DoubleSumObserver;
import io.opentelemetry.metrics.DoubleUpDownCounter;
import io.opentelemetry.metrics.DoubleUpDownSumObserver;
import io.opentelemetry.metrics.DoubleValueObserver;
import io.opentelemetry.metrics.DoubleValueRecorder;
import io.opentelemetry.metrics.LongCounter;
import io.opentelemetry.metrics.LongSumObserver;
import io.opentelemetry.metrics.LongUpDownCounter;
import io.opentelemetry.metrics.LongUpDownSumObserver;
import io.opentelemetry.metrics.LongValueObserver;
import io.opentelemetry.metrics.LongValueRecorder;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.metrics.MeterProvider;
import io.opentelemetry.metrics.spi.MeterProviderFactory;
import io.opentelemetry.trace.DefaultTracerProvider;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracerProvider;
import io.opentelemetry.trace.spi.TracerProviderFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpenTelemetryTest {

  @BeforeAll
  static void beforeClass() {
    OpenTelemetry.reset();
  }

  @AfterEach
  void after() {
    OpenTelemetry.reset();
    System.clearProperty(TracerProviderFactory.class.getName());
    System.clearProperty(MeterProviderFactory.class.getName());
    System.clearProperty(BaggageManagerFactory.class.getName());
  }

  @Test
  void testDefault() {
    assertThat(OpenTelemetry.getTracerProvider()).isInstanceOf(DefaultTracerProvider.class);
    assertThat(OpenTelemetry.getTracerProvider()).isSameAs(OpenTelemetry.getTracerProvider());
    assertThat(OpenTelemetry.getMeterProvider()).isInstanceOf(DefaultMeterProvider.class);
    assertThat(OpenTelemetry.getMeterProvider()).isSameAs(OpenTelemetry.getMeterProvider());
    assertThat(OpenTelemetry.getBaggageManager()).isInstanceOf(DefaultBaggageManager.class);
    assertThat(OpenTelemetry.getBaggageManager()).isSameAs(OpenTelemetry.getBaggageManager());
    assertThat(OpenTelemetry.getPropagators()).isInstanceOf(DefaultContextPropagators.class);
    assertThat(OpenTelemetry.getPropagators()).isSameAs(OpenTelemetry.getPropagators());
  }

  @Test
  void testTracerLoadArbitrary() throws IOException {
    File serviceFile =
        createService(
            TracerProviderFactory.class,
            FirstTracerProviderFactory.class,
            SecondTracerProviderFactory.class);
    try {
      assertTrue(
          (OpenTelemetry.getTracerProvider().get("") instanceof FirstTracerProviderFactory)
              || (OpenTelemetry.getTracerProvider().get("")
                  instanceof SecondTracerProviderFactory));
    } finally {
      serviceFile.delete();
    }
  }

  @Test
  void testTracerSystemProperty() throws IOException {
    File serviceFile =
        createService(
            TracerProviderFactory.class,
            FirstTracerProviderFactory.class,
            SecondTracerProviderFactory.class);
    System.setProperty(
        TracerProviderFactory.class.getName(), SecondTracerProviderFactory.class.getName());
    try {
      assertThat(OpenTelemetry.getTracerProvider().get(""))
          .isInstanceOf(SecondTracerProviderFactory.class);
    } finally {
      serviceFile.delete();
    }
  }

  @Test
  void testTracerNotFound() {
    System.setProperty(TracerProviderFactory.class.getName(), "io.does.not.exists");
    assertThrows(IllegalStateException.class, () -> OpenTelemetry.getTracer("testTracer"));
  }

  @Test
  void testMeterLoadArbitrary() throws IOException {
    File serviceFile =
        createService(
            MeterProviderFactory.class,
            FirstMeterProviderFactory.class,
            SecondMeterProviderFactory.class);
    try {
      assertTrue(
          (OpenTelemetry.getMeterProvider() instanceof FirstMeterProviderFactory)
              || (OpenTelemetry.getMeterProvider() instanceof SecondMeterProviderFactory));
      assertThat(OpenTelemetry.getMeterProvider()).isEqualTo(OpenTelemetry.getMeterProvider());
    } finally {
      serviceFile.delete();
    }
  }

  @Test
  void testMeterSystemProperty() throws IOException {
    File serviceFile =
        createService(
            MeterProviderFactory.class,
            FirstMeterProviderFactory.class,
            SecondMeterProviderFactory.class);
    System.setProperty(
        MeterProviderFactory.class.getName(), SecondMeterProviderFactory.class.getName());
    try {
      assertThat(OpenTelemetry.getMeterProvider()).isInstanceOf(SecondMeterProviderFactory.class);
      assertThat(OpenTelemetry.getMeterProvider()).isEqualTo(OpenTelemetry.getMeterProvider());
    } finally {
      serviceFile.delete();
    }
  }

  @Test
  void testMeterNotFound() {
    System.setProperty(MeterProviderFactory.class.getName(), "io.does.not.exists");
    assertThrows(IllegalStateException.class, () -> OpenTelemetry.getMeterProvider());
  }

  @Test
  void testBaggageManagerLoadArbitrary() throws IOException {
    File serviceFile =
        createService(
            BaggageManagerFactory.class, FirstBaggageManager.class, SecondBaggageManager.class);
    try {
      assertTrue(
          (OpenTelemetry.getBaggageManager() instanceof FirstBaggageManager)
              || (OpenTelemetry.getBaggageManager() instanceof SecondBaggageManager));
      assertThat(OpenTelemetry.getBaggageManager()).isEqualTo(OpenTelemetry.getBaggageManager());
    } finally {
      serviceFile.delete();
    }
  }

  @Test
  void testBaggageManagerSystemProperty() throws IOException {
    File serviceFile =
        createService(
            BaggageManagerFactory.class, FirstBaggageManager.class, SecondBaggageManager.class);
    System.setProperty(BaggageManagerFactory.class.getName(), SecondBaggageManager.class.getName());
    try {
      assertThat(OpenTelemetry.getBaggageManager()).isInstanceOf(SecondBaggageManager.class);
      assertThat(OpenTelemetry.getBaggageManager()).isEqualTo(OpenTelemetry.getBaggageManager());
    } finally {
      serviceFile.delete();
    }
  }

  @Test
  void testBaggageManagerNotFound() {
    System.setProperty(BaggageManagerFactory.class.getName(), "io.does.not.exists");
    assertThrows(IllegalStateException.class, () -> OpenTelemetry.getBaggageManager());
  }

  @Test
  void testPropagatorsSet() {
    ContextPropagators propagators = DefaultContextPropagators.builder().build();
    OpenTelemetry.setPropagators(propagators);
    assertThat(OpenTelemetry.getPropagators()).isEqualTo(propagators);
  }

  @Test
  void testPropagatorsSetNull() {
    assertThrows(NullPointerException.class, () -> OpenTelemetry.setPropagators(null));
  }

  private static File createService(Class<?> service, Class<?>... impls) throws IOException {
    URL location = Tracer.class.getProtectionDomain().getCodeSource().getLocation();
    File file = new File(location.getPath() + "META-INF/services/" + service.getName());
    file.getParentFile().mkdirs();

    @SuppressWarnings("DefaultCharset")
    Writer output = new FileWriter(file);
    for (Class<?> impl : impls) {
      output.write(impl.getName());
      output.write(System.getProperty("line.separator"));
    }
    output.close();
    return file;
  }

  public static class SecondTracerProviderFactory extends FirstTracerProviderFactory {
    @Override
    public Tracer get(String instrumentationName) {
      return new SecondTracerProviderFactory();
    }

    @Override
    public Tracer get(String instrumentationName, String instrumentationVersion) {
      return get(instrumentationName);
    }

    @Override
    public TracerProvider create() {
      return new SecondTracerProviderFactory();
    }
  }

  public static class FirstTracerProviderFactory
      implements Tracer, TracerProvider, TracerProviderFactory {
    @Override
    public Tracer get(String instrumentationName) {
      return new FirstTracerProviderFactory();
    }

    @Override
    public Tracer get(String instrumentationName, String instrumentationVersion) {
      return get(instrumentationName);
    }

    @Nullable
    @Override
    public Span getCurrentSpan() {
      return null;
    }

    @Nullable
    @Override
    public Scope withSpan(Span span) {
      return null;
    }

    @Nullable
    @Override
    public Span.Builder spanBuilder(String spanName) {
      return null;
    }

    @Override
    public TracerProvider create() {
      return new FirstTracerProviderFactory();
    }
  }

  public static class SecondMeterProviderFactory extends FirstMeterProviderFactory {
    @Override
    public Meter get(String instrumentationName) {
      return new SecondMeterProviderFactory();
    }

    @Override
    public Meter get(String instrumentationName, String instrumentationVersion) {
      return get(instrumentationName);
    }

    @Override
    public MeterProvider create() {
      return new SecondMeterProviderFactory();
    }
  }

  public static class FirstMeterProviderFactory
      implements Meter, MeterProviderFactory, MeterProvider {
    @Override
    public MeterProvider create() {
      return new FirstMeterProviderFactory();
    }

    @Nullable
    @Override
    public DoubleCounter.Builder doubleCounterBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public LongCounter.Builder longCounterBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public DoubleUpDownCounter.Builder doubleUpDownCounterBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public LongUpDownCounter.Builder longUpDownCounterBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public DoubleValueRecorder.Builder doubleValueRecorderBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public LongValueRecorder.Builder longValueRecorderBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public DoubleSumObserver.Builder doubleSumObserverBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public LongSumObserver.Builder longSumObserverBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public DoubleUpDownSumObserver.Builder doubleUpDownSumObserverBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public LongUpDownSumObserver.Builder longUpDownSumObserverBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public DoubleValueObserver.Builder doubleValueObserverBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public LongValueObserver.Builder longValueObserverBuilder(String name) {
      return null;
    }

    @Nullable
    @Override
    public BatchRecorder newBatchRecorder(String... keyValuePairs) {
      return null;
    }

    @Override
    public BatchObserver newBatchObserver(String name) {
      return null;
    }

    @Override
    public Meter get(String instrumentationName) {
      return new FirstMeterProviderFactory();
    }

    @Override
    public Meter get(String instrumentationName, String instrumentationVersion) {
      return get(instrumentationName);
    }
  }

  public static class SecondBaggageManager extends FirstBaggageManager {
    @Override
    public BaggageManager create() {
      return new SecondBaggageManager();
    }
  }

  public static class FirstBaggageManager implements BaggageManager, BaggageManagerFactory {
    @Override
    public BaggageManager create() {
      return new FirstBaggageManager();
    }

    @Nullable
    @Override
    public Baggage getCurrentBaggage() {
      return null;
    }

    @Nullable
    @Override
    public Baggage.Builder baggageBuilder() {
      return null;
    }

    @Nullable
    @Override
    public Scope withContext(Baggage distContext) {
      return null;
    }
  }
}
