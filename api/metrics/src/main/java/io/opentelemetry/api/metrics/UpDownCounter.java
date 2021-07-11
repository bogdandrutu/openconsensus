/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

/**
 * An up-down-counter instrument.
 *
 * <p>This will have a specific subclass that recoords appropriate primitive values, e.g. {@link
 * LongUpDownCounter} will record {@code long} values.
 */
public interface UpDownCounter extends SynchronousInstrument {}
