/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.sdk.metrics.AbstractInstrument.Builder.ERROR_MESSAGE_INVALID_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.common.InstrumentValueType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AbstractInstrument.Builder}. */
class AbstractInstrumentBuilderTest {

  private static final String NAME = "name";
  private static final String DESCRIPTION = "description";
  private static final String UNIT = "1";

  @Test
  void preventNull_Name() {
    assertThatThrownBy(() -> new TestInstrumentBuilder(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name");
  }

  @Test
  void preventEmpty_Name() {
    assertThatThrownBy(() -> new TestInstrumentBuilder(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Name");
  }

  @Test
  void checkCorrect_Name() {
    new TestInstrumentBuilder("a");
    new TestInstrumentBuilder("METRIC_name");
    new TestInstrumentBuilder("metric.name_01");
    new TestInstrumentBuilder("metric_name.01");
    assertThatThrownBy(() -> new TestInstrumentBuilder("01.metric_name_01"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Name");
  }

  @Test
  void preventNonPrintableName() {
    assertThatThrownBy(() -> new TestInstrumentBuilder("\2").build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preventTooLongName() {
    char[] chars = new char[StringUtils.METRIC_NAME_MAX_LENGTH + 1];
    Arrays.fill(chars, 'a');
    String longName = String.valueOf(chars);
    assertThatThrownBy(() -> new TestInstrumentBuilder(longName).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ERROR_MESSAGE_INVALID_NAME);
  }

  @Test
  void preventNull_Description() {
    assertThatThrownBy(() -> new TestInstrumentBuilder(NAME).setDescription(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("description");
  }

  @Test
  void preventNull_Unit() {
    assertThatThrownBy(() -> new TestInstrumentBuilder(NAME).setUnit(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("unit");
  }

  @Test
  void defaultValue() {
    TestInstrumentBuilder testInstrumentBuilder = new TestInstrumentBuilder(NAME);
    TestInstrument testInstrument = testInstrumentBuilder.build();
    assertThat(testInstrument).isInstanceOf(TestInstrument.class);
    assertThat(testInstrument.getDescriptor().getName()).isEqualTo(NAME);
    assertThat(testInstrument.getDescriptor().getDescription()).isEmpty();
    assertThat(testInstrument.getDescriptor().getUnit()).isEqualTo("1");
  }

  @Test
  void setAndGetValues() {
    TestInstrumentBuilder testInstrumentBuilder =
        new TestInstrumentBuilder(NAME).setDescription(DESCRIPTION).setUnit(UNIT);

    TestInstrument testInstrument = testInstrumentBuilder.build();
    assertThat(testInstrument).isInstanceOf(TestInstrument.class);
    assertThat(testInstrument.getDescriptor().getName()).isEqualTo(NAME);
    assertThat(testInstrument.getDescriptor().getDescription()).isEqualTo(DESCRIPTION);
    assertThat(testInstrument.getDescriptor().getUnit()).isEqualTo(UNIT);
    assertThat(testInstrument.getDescriptor().getType()).isEqualTo(InstrumentType.UP_DOWN_COUNTER);
    assertThat(testInstrument.getDescriptor().getValueType()).isEqualTo(InstrumentValueType.LONG);
  }

  private static final class TestInstrumentBuilder
      extends AbstractInstrument.Builder<TestInstrumentBuilder> {
    TestInstrumentBuilder(String name) {
      super(name, InstrumentType.UP_DOWN_COUNTER, InstrumentValueType.LONG);
    }

    @Override
    TestInstrumentBuilder getThis() {
      return this;
    }

    @Override
    public TestInstrument build() {
      return new TestInstrument(buildDescriptor());
    }
  }
}
