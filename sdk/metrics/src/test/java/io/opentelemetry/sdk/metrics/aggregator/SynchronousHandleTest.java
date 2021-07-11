/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.AtomicDouble;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.instrument.DoubleMeasurement;
import io.opentelemetry.sdk.metrics.instrument.LongMeasurement;
import io.opentelemetry.sdk.metrics.instrument.Measurement;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

public class SynchronousHandleTest {

  @Test
  void acquireMapped() {
    TestSynchronousHandle storage = new TestSynchronousHandle();
    assertThat(storage.acquire()).isTrue();
    storage.release();
    assertThat(storage.acquire()).isTrue();
    assertThat(storage.acquire()).isTrue();
    storage.release();
    assertThat(storage.acquire()).isTrue();
    storage.release();
    storage.release();
  }

  @Test
  void tryUnmap_AcquiredHandler() {
    TestSynchronousHandle storage = new TestSynchronousHandle();
    assertThat(storage.acquire()).isTrue();
    assertThat(storage.tryUnmap()).isFalse();
    storage.release();
    // The aggregator is by default acquired, so need an extra release.
    assertThat(storage.tryUnmap()).isFalse();
    storage.release();
    assertThat(storage.tryUnmap()).isTrue();
  }

  @Test
  void tryUnmap_AcquiredHandler_MultipleTimes() {
    TestSynchronousHandle storage = new TestSynchronousHandle();
    assertThat(storage.acquire()).isTrue();
    assertThat(storage.acquire()).isTrue();
    assertThat(storage.acquire()).isTrue();
    assertThat(storage.tryUnmap()).isFalse();
    storage.release();
    assertThat(storage.acquire()).isTrue();
    assertThat(storage.tryUnmap()).isFalse();
    storage.release();
    assertThat(storage.tryUnmap()).isFalse();
    storage.release();
    assertThat(storage.tryUnmap()).isFalse();
    storage.release();
    // The aggregator is by default acquired, so need an extra release.
    assertThat(storage.tryUnmap()).isFalse();
    storage.release();
    assertThat(storage.tryUnmap()).isTrue();
  }

  @Test
  void bind_ThenUnmap_ThenTryToBind() {
    TestSynchronousHandle storage = new TestSynchronousHandle();
    storage.release();
    assertThat(storage.tryUnmap()).isTrue();
    assertThat(storage.acquire()).isFalse();
    storage.release();
  }

  @Test
  void testRecordings() {
    TestSynchronousHandle storage = new TestSynchronousHandle();

    storage.record(LongMeasurement.createNoContext(22, Attributes.empty()));
    assertThat(storage.recordedLong.get()).isEqualTo(22);
    assertThat(storage.recordedDouble.get()).isEqualTo(0);

    storage.accumulateThenReset();
    assertThat(storage.recordedLong.get()).isEqualTo(0);
    assertThat(storage.recordedDouble.get()).isEqualTo(0);

    storage.record(DoubleMeasurement.createNoContext(33.55, Attributes.empty()));
    assertThat(storage.recordedLong.get()).isEqualTo(0);
    assertThat(storage.recordedDouble.get()).isEqualTo(33.55);

    storage.accumulateThenReset();
    assertThat(storage.recordedLong.get()).isEqualTo(0);
    assertThat(storage.recordedDouble.get()).isEqualTo(0);
  }

  @Test
  void testExemplars() {
    TestSynchronousHandle storage = new TestSynchronousHandle(/* sampler=*/ (measurement) -> true);

    // First record one measurement as exemplar and see if it is passed correctly.
    final LongMeasurement firstMeasurement =
        LongMeasurement.createNoContext(22, Attributes.empty());
    storage.record(firstMeasurement);
    assertThat(storage.recordedLong.get()).isEqualTo(22);
    assertThat(storage.recordedDouble.get()).isEqualTo(0);
    assertThat(storage.recordedExemplars.get()).isEmpty();

    storage.accumulateThenReset();
    assertThat(storage.recordedLong.get()).isEqualTo(0);
    assertThat(storage.recordedDouble.get()).isEqualTo(0);
    assertThat(storage.recordedExemplars.get()).containsExactlyInAnyOrder(firstMeasurement);

    // Now record two measurements and see if they both get sampled as exemplar.
    final LongMeasurement secondMeasurement =
        LongMeasurement.createNoContext(44, Attributes.empty());
    final LongMeasurement thirdMeasurement =
        LongMeasurement.createNoContext(33, Attributes.empty());
    storage.record(secondMeasurement);
    assertThat(storage.recordedLong.get()).isEqualTo(44);
    storage.record(thirdMeasurement);
    assertThat(storage.recordedLong.get()).isEqualTo(33);
    storage.accumulateThenReset();
    assertThat(storage.recordedExemplars.get())
        .containsExactlyInAnyOrder(secondMeasurement, thirdMeasurement);
  }

  private static class TestSynchronousHandle extends SynchronousHandle<Void> {
    final AtomicLong recordedLong = new AtomicLong();
    final AtomicDouble recordedDouble = new AtomicDouble();
    final AtomicReference<Iterable<Measurement>> recordedExemplars =
        new AtomicReference<>(Collections.emptyList());

    TestSynchronousHandle() {
      super(ExemplarSampler.NEVER);
    }

    TestSynchronousHandle(ExemplarSampler sampler) {
      super(sampler);
    }

    @Nullable
    @Override
    protected Void doAccumulateThenReset(Iterable<Measurement> exemplars) {
      recordedLong.set(0);
      recordedDouble.set(0);
      recordedExemplars.set(exemplars);
      return null;
    }

    @Override
    protected void doRecord(Measurement value) {
      if (value instanceof LongMeasurement) {
        recordedLong.set(((LongMeasurement) value).getValue());
      } else {
        recordedDouble.set(((DoubleMeasurement) value).getValue());
      }
    }
  }
}
