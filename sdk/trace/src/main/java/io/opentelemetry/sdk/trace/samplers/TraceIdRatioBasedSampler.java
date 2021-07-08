/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.samplers;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.internal.OtelEncodingUtils;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.util.List;
import javax.annotation.concurrent.Immutable;

/**
 * We assume the lower 64 bits of the traceId's are randomly distributed around the whole (long)
 * range. We convert an incoming probability into an upper bound on that value, such that we can
 * just compare the absolute value of the id and the bound to see if we are within the desired
 * probability range. Using the low bits of the traceId also ensures that systems that only use 64
 * bit ID's will also work with this sampler.
 */
@Immutable
final class TraceIdRatioBasedSampler implements Sampler {

  private static final SamplingResult POSITIVE_SAMPLING_RESULT = SamplingResult.recordAndSample();

  private static final SamplingResult NEGATIVE_SAMPLING_RESULT = SamplingResult.drop();

  private final long idUpperBound;
  private final String description;

  static TraceIdRatioBasedSampler create(double ratio) {
    if (ratio < 0.0 || ratio > 1.0) {
      throw new IllegalArgumentException("ratio must be in range [0.0, 1.0]");
    }
    long idUpperBound;
    // Special case the limits, to avoid any possible issues with lack of precision across
    // double/long boundaries. For probability == 0.0, we use Long.MIN_VALUE as this guarantees
    // that we will never sample a trace, even in the case where the id == Long.MIN_VALUE, since
    // Math.Abs(Long.MIN_VALUE) == Long.MIN_VALUE.
    if (ratio == 0.0) {
      idUpperBound = Long.MIN_VALUE;
    } else if (ratio == 1.0) {
      idUpperBound = Long.MAX_VALUE;
    } else {
      idUpperBound = (long) (ratio * Long.MAX_VALUE);
    }
    return new TraceIdRatioBasedSampler(ratio, idUpperBound);
  }

  TraceIdRatioBasedSampler(double ratio, long idUpperBound) {
    this.idUpperBound = idUpperBound;
    description = String.format("TraceIdRatioBased{%.6f}", ratio);
  }

  @Override
  public final SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {
    // Always sample if we are within probability range. This is true even for child spans (that
    // may have had a different sampling samplingResult made) to allow for different sampling
    // policies,
    // and dynamic increases to sampling probabilities for debugging purposes.
    // Note use of '<' for comparison. This ensures that we never sample for probability == 0.0,
    // while allowing for a (very) small chance of *not* sampling if the id == Long.MAX_VALUE.
    // This is considered a reasonable tradeoff for the simplicity/performance requirements (this
    // code is executed in-line for every Span creation).
    return Math.abs(getTraceIdRandomPart(traceId)) < idUpperBound
        ? POSITIVE_SAMPLING_RESULT
        : NEGATIVE_SAMPLING_RESULT;
  }

  @Override
  public final String getDescription() {
    return description;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TraceIdRatioBasedSampler)) {
      return false;
    }
    TraceIdRatioBasedSampler that = (TraceIdRatioBasedSampler) obj;
    return idUpperBound == that.idUpperBound;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(idUpperBound);
  }

  @Override
  public final String toString() {
    return getDescription();
  }

  // Visible for testing
  long getIdUpperBound() {
    return idUpperBound;
  }

  private static long getTraceIdRandomPart(String traceId) {
    return OtelEncodingUtils.longFromBase16String(traceId, 16);
  }
}
