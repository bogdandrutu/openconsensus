/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.instrument;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

/**
 * A measurement represents a data point reported via the metric instrument.
 *
 * <p>Measurements encapsulate:
 *
 * <ul>
 *   <li>A value
 *   <li>Attributes
 * </ul>
 *
 * <p>See
 * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#measurement
 */
public interface Measurement {

  // TODO - start/end times from MetricPoint?

  /** The attributes associated with this measurement. */
  Attributes getAttributes();

  /** For synchronous instruments, returns the context during the measruement. */
  Context getContext();

  /** Convert the recorded value to a double. This may loose precision/overflow. */
  DoubleMeasurement asDouble();
}
