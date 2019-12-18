/*
 * Copyright 2019, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.sdk.metrics;

import static com.google.common.truth.Truth.assertThat;

import io.opentelemetry.metrics.LabelSet;
import io.opentelemetry.metrics.Observer;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AbstractObserverBuilder}. */
@RunWith(JUnit4.class)
public class AbstractObserverBuilderTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  private static final String NAME = "name";

  @Test
  public void defaultValue() {
    TestMetricBuilder testMetricBuilder = TestMetricBuilder.newBuilder(NAME);
    assertThat(testMetricBuilder.getName()).isEqualTo(NAME);
    assertThat(testMetricBuilder.getDescription()).isEmpty();
    assertThat(testMetricBuilder.getUnit()).isEqualTo("1");
    assertThat(testMetricBuilder.getLabelKeys()).isEmpty();
    assertThat(testMetricBuilder.getConstantLabels()).isEmpty();
    assertThat(testMetricBuilder.getMonotonic()).isFalse();
    assertThat(testMetricBuilder.build()).isInstanceOf(TestInstrument.class);
  }

  @Test
  public void setAndGetValues() {
    TestMetricBuilder testMetricBuilder = TestMetricBuilder.newBuilder(NAME).setMonotonic(true);
    assertThat(testMetricBuilder.getName()).isEqualTo(NAME);
    assertThat(testMetricBuilder.getMonotonic()).isTrue();
    assertThat(testMetricBuilder.build()).isInstanceOf(TestInstrument.class);
  }

  private static final class TestMetricBuilder
      extends AbstractObserverBuilder<TestMetricBuilder, TestInstrument> {
    static TestMetricBuilder newBuilder(String name) {
      return new TestMetricBuilder(name);
    }

    TestMetricBuilder(String name) {
      super(name);
    }

    @Override
    TestMetricBuilder getThis() {
      return this;
    }

    @Override
    public TestInstrument build() {
      return new TestInstrument();
    }
  }

  private static final class TestInstrument implements Observer<TestResult, TestBound> {

    @Nullable
    @Override
    public TestBound bind(LabelSet labelSet) {
      return null;
    }

    @Override
    public void unbind(TestBound boundInstrument) {}

    @Override
    public void setCallback(Callback<TestResult> metricUpdater) {}
  }

  private static final class TestBound {}

  private static final class TestResult {}
}
