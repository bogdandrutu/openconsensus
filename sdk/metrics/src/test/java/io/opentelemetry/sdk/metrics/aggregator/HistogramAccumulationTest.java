/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramPointData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HistogramAccumulationTest {
  @Test
  void toPoint() {
    HistogramAccumulation accumulation =
        HistogramAccumulation.create(12, 25, ImmutableList.of(1.0), ImmutableList.of(1L, 2L));
    DoubleHistogramPointData point = getPoint(accumulation);
    assertThat(point.getCount()).isEqualTo(12);
    assertThat(point.getSum()).isEqualTo(25);
    assertThat(point.getBoundaries()).isEqualTo(ImmutableList.of(1.0));
    assertThat(point.getCounts()).isEqualTo(ImmutableList.of(1L, 2L));

    List<Double> boundaries = new ArrayList<>();
    List<Long> counts = new ArrayList<>();
    point.forEach(
        (b, c) -> {
          boundaries.add(b);
          counts.add(c);
        });
    assertThat(boundaries).isEqualTo(ImmutableList.of(1.0, Double.POSITIVE_INFINITY));
    assertThat(counts).isEqualTo(point.getCounts());
  }

  private static DoubleHistogramPointData getPoint(HistogramAccumulation accumulation) {
    DoubleHistogramPointData point = accumulation.toPoint(12345, 12358, Labels.of("key", "value"));
    assertThat(point).isNotNull();
    assertThat(point.getStartEpochNanos()).isEqualTo(12345);
    assertThat(point.getEpochNanos()).isEqualTo(12358);
    assertThat(point.getLabels().size()).isEqualTo(1);
    assertThat(point.getLabels().get("key")).isEqualTo("value");
    assertThat(point).isInstanceOf(DoubleHistogramPointData.class);
    return point;
  }
}
