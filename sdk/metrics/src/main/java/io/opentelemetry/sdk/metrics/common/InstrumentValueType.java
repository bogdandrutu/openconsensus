/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.common;

/** All possible types for the values recorded via the instruments. */
public enum InstrumentValueType {
  LONG,
  DOUBLE,
  BATCH_OBSERVER
}
