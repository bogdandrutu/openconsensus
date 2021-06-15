/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure.spi;

import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * A service provider interface (SPI) for providing additional exporters that can be used with the
 * autoconfigured SDK. If the {@code otel.traces.exporter} property contains a value equal to what
 * is returned by {@link #getName()}, the exporter returned by {@link
 * #createExporter(ConfigProperties)} will be enabled and added to the SDK.
 */
public interface ConfigurableSpanExporterProvider {

  /**
   * Returns a {@link SpanExporter} that can be registered to OpenTelemetry by providing the
   * property value specified by {@link #getName()}.
   */
  SpanExporter createExporter(ConfigProperties config);

  /**
   * Returns the name of this exporter, which can be specified with the {@code otel.traces.exporter}
   * property to enable it. The name returned should NOT be the same as any other exporter name. If
   * the name does conflict with another exporter name, the resulting behavior is undefined and it
   * is explicitly unspecified which exporter will actually be used.
   */
  String getName();
}
