/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

/**
 * A counter instrument.
 *
 * <p>This will have a specific subclass that recoords appropriate primitive values, e.g. {@link
 * LongCounter} will record {@code long} values.
 */
public interface Counter extends SynchronousInstrument {}
