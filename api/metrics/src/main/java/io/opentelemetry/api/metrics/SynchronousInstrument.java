/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.metrics;

import javax.annotation.concurrent.ThreadSafe;

/** Marker interface for synchronous instruments. */
@ThreadSafe
public interface SynchronousInstrument {}
