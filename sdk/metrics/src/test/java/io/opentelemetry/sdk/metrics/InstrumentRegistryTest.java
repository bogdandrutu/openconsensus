/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.common.InstrumentValueType;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InstrumentRegistry}. */
class InstrumentRegistryTest {
  private static final InstrumentDescriptor INSTRUMENT_DESCRIPTOR =
      InstrumentDescriptor.create(
          "name", "description", "1", InstrumentType.COUNTER, InstrumentValueType.LONG);
  private static final InstrumentDescriptor OTHER_INSTRUMENT_DESCRIPTOR =
      InstrumentDescriptor.create(
          "name", "other_description", "1", InstrumentType.COUNTER, InstrumentValueType.LONG);

  @Test
  void register() {
    MeterSharedState meterSharedState =
        MeterSharedState.create(InstrumentationLibraryInfo.getEmpty());
    TestInstrument testInstrument = new TestInstrument(INSTRUMENT_DESCRIPTOR);
    assertThat(meterSharedState.getInstrumentRegistry().register(testInstrument))
        .isSameAs(testInstrument);
    assertThat(meterSharedState.getInstrumentRegistry().register(testInstrument))
        .isSameAs(testInstrument);
    assertThat(
            meterSharedState
                .getInstrumentRegistry()
                .register(new TestInstrument(INSTRUMENT_DESCRIPTOR)))
        .isSameAs(testInstrument);
  }

  @Test
  void register_OtherDescriptor() {
    MeterSharedState meterSharedState =
        MeterSharedState.create(InstrumentationLibraryInfo.getEmpty());
    TestInstrument testInstrument = new TestInstrument(INSTRUMENT_DESCRIPTOR);
    assertThat(meterSharedState.getInstrumentRegistry().register(testInstrument))
        .isSameAs(testInstrument);

    assertThatThrownBy(
            () ->
                meterSharedState
                    .getInstrumentRegistry()
                    .register(new TestInstrument(OTHER_INSTRUMENT_DESCRIPTOR)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Instrument with same name and different descriptor already created.");
  }

  @Test
  void register_OtherInstance() {
    MeterSharedState meterSharedState =
        MeterSharedState.create(InstrumentationLibraryInfo.getEmpty());
    TestInstrument testInstrument = new TestInstrument(INSTRUMENT_DESCRIPTOR);
    assertThat(meterSharedState.getInstrumentRegistry().register(testInstrument))
        .isSameAs(testInstrument);

    assertThatThrownBy(
            () ->
                meterSharedState
                    .getInstrumentRegistry()
                    .register(new OtherTestInstrument(INSTRUMENT_DESCRIPTOR)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Instrument with same name and different descriptor already created.");
  }

  private static final class OtherTestInstrument extends AbstractInstrument {
    OtherTestInstrument(InstrumentDescriptor descriptor) {
      super(descriptor);
    }

    @Override
    void collectAll(List<MetricData> output) {}
  }
}
