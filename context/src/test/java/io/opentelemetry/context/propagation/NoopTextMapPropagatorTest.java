/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.context.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import java.util.HashMap;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class NoopTextMapPropagatorTest {

  @Test
  void noopFields() {
    assertThat(TextMapPropagator.noop().fields()).isEmpty();
  }

  @Test
  void contextUnchanged() {
    Context input = Context.current();
    Context result =
        TextMapPropagator.noop()
            .extract(
                input,
                new HashMap<>(),
                new TextMapGetter<HashMap<? extends Object, ? extends Object>>() {
                  @Override
                  public Iterable<String> keys(HashMap<?, ?> carrier) {
                    return null;
                  }

                  @Nullable
                  @Override
                  public String get(@Nullable HashMap<?, ?> carrier, String key) {
                    return null;
                  }
                });
    assertThat(result).isSameAs(input);
  }
}
