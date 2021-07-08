/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.samplers;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.Test;

class SamplingResultTest {

  @Test
  void noAttributes() {
    assertThat(SamplingResult.create(SamplingDecision.RECORD_AND_SAMPLE))
        .isSameAs(SamplingResult.create(SamplingDecision.RECORD_AND_SAMPLE));
    assertThat(SamplingResult.create(SamplingDecision.DROP))
        .isSameAs(SamplingResult.create(SamplingDecision.DROP));

    assertThat(SamplingResult.create(SamplingDecision.RECORD_AND_SAMPLE).getDecision())
        .isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
    assertThat(SamplingResult.create(SamplingDecision.RECORD_AND_SAMPLE).getAttributes().isEmpty())
        .isTrue();
    assertThat(SamplingResult.create(SamplingDecision.DROP).getDecision())
        .isEqualTo(SamplingDecision.DROP);
    assertThat(SamplingResult.create(SamplingDecision.DROP).getAttributes().isEmpty()).isTrue();

    assertThat(SamplingResult.recordAndSample()).isSameAs(SamplingResult.recordAndSample());
    assertThat(SamplingResult.drop()).isSameAs(SamplingResult.drop());

    assertThat(SamplingResult.recordAndSample().getDecision())
        .isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
    assertThat(SamplingResult.recordAndSample().getAttributes().isEmpty()).isTrue();
    assertThat(SamplingResult.drop().getDecision()).isEqualTo(SamplingDecision.DROP);
    assertThat(SamplingResult.drop().getAttributes().isEmpty()).isTrue();
  }

  @Test
  void emptyAttributes() {
    assertThat(SamplingResult.create(SamplingDecision.RECORD_AND_SAMPLE, Attributes.empty()))
        .isSameAs(SamplingResult.create(SamplingDecision.RECORD_AND_SAMPLE));
    assertThat(SamplingResult.create(SamplingDecision.DROP, Attributes.empty()))
        .isSameAs(SamplingResult.create(SamplingDecision.DROP));
  }

  @Test
  void hasAttributes() {
    final Attributes attrs = Attributes.of(longKey("foo"), 42L, stringKey("bar"), "baz");
    final SamplingResult sampledSamplingResult =
        SamplingResult.create(SamplingDecision.RECORD_AND_SAMPLE, attrs);
    assertThat(sampledSamplingResult.getDecision()).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
    assertThat(sampledSamplingResult.getAttributes()).isEqualTo(attrs);

    final SamplingResult notSampledSamplingResult =
        SamplingResult.create(SamplingDecision.DROP, attrs);
    assertThat(notSampledSamplingResult.getDecision()).isEqualTo(SamplingDecision.DROP);
    assertThat(notSampledSamplingResult.getAttributes()).isEqualTo(attrs);
  }
}
