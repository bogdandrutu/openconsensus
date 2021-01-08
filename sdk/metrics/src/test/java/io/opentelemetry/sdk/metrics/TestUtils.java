/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.ArrayList;
import java.util.List;

final class TestUtils {
  // TODO: Remove this helper for test method.
  static List<MetricData> collectAll(AbstractInstrument abstractInstrument) {
    List<MetricData> output = new ArrayList<>();
    abstractInstrument.collectAll(output);
    return output;
  }

  private TestUtils() {}
}
