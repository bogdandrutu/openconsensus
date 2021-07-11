/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.instrument;

/** All instrument types available in the metric package. */
public enum InstrumentType {
  COUNTER,
  UP_DOWN_COUNTER,
  HISTOGRAM,
  OBSERVABLE_GAUGE,
  OBSERVABLE_SUM,
  OBSERVBALE_UP_DOWN_SUM;

  /** Returns true, if this is a synchronous insturment type. */
  public boolean isSynchronous() {
    switch (this) {
      case COUNTER:
      case UP_DOWN_COUNTER:
      case HISTOGRAM:
        return true;
      default:
        return false;
    }
  }
}
