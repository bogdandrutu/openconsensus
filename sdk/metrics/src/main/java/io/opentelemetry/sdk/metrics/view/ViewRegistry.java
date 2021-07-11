/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import java.util.Optional;

public interface ViewRegistry {
  Optional<View> findViewFor(
      InstrumentDescriptor instrument, InstrumentationLibraryInfo instrumentationLibrary);
}
