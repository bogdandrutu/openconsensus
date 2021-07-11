/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.state;

import io.opentelemetry.sdk.metrics.instrument.Measurement;

/** A bound handle for recoroding measurements against a particular set of attributes. */
public interface StorageHandle {
  /** Record a specific measurement. */
  void record(Measurement measurement);
  /** Release this handle back to the storage. */
  void release();
}
