/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.assertj.metrics;

import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.SumData;
import org.assertj.core.api.AbstractAssert;

public class AbstractSumDataAssert<
        SumAssertT extends AbstractSumDataAssert<SumAssertT, SumT>, SumT extends SumData<?>>
    extends AbstractAssert<SumAssertT, SumT> {
  protected AbstractSumDataAssert(SumT actual, Class<SumAssertT> assertClass) {
    super(actual, assertClass);
  }

  public SumAssertT isMonotonic() {
    isNotNull();
    if (!actual.isMonotonic()) {
      failWithActualExpectedAndMessage(
          actual, "montonic: true", "Exepcted Sum to be monotonic", true, actual.isMonotonic());
    }
    return myself;
  }

  public SumAssertT isNotMonotonic() {
    isNotNull();
    if (actual.isMonotonic()) {
      failWithActualExpectedAndMessage(
          actual,
          "montonic: fail",
          "Exepcted Sum to be non-monotonic",
          false,
          actual.isMonotonic());
    }
    return myself;
  }

  public SumAssertT isCumulative() {
    isNotNull();
    if (actual.getAggregationTemporality() != AggregationTemporality.CUMULATIVE) {
      failWithActualExpectedAndMessage(
          actual,
          "aggregationTemporality: CUMULATIVE",
          "Exepcted Sum to have cumulative aggregation but found <%s>",
          AggregationTemporality.CUMULATIVE,
          actual.getAggregationTemporality());
    }
    return myself;
  }

  public SumAssertT isDelta() {
    isNotNull();
    if (actual.getAggregationTemporality() != AggregationTemporality.DELTA) {
      failWithActualExpectedAndMessage(
          actual,
          "aggregationTemporality: DELTA",
          "Exepcted Sum to have cumulative aggregation but found <%s>",
          AggregationTemporality.DELTA,
          actual.getAggregationTemporality());
    }
    return myself;
  }
}
