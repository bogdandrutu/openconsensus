/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

public interface GaugeBuilder<ObservableMeasurementT extends ObservableMeasurement>
    extends ObservableInstrumentBuilder<ObservableMeasurementT> {}
