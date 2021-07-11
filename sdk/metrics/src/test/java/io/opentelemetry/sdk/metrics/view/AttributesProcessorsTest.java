/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Tests for the {@link AttributesProcessors} DSL-ish library. */
public class AttributesProcessorsTest {
  @Test
  public void filterKeys_removesKeys() {
    AttributesProcessor processor = AttributesProcessors.filterKeys(Pattern.compile("test"));

    assertThat(
            processor.process(
                Attributes.builder().put("remove", "me").put("test", "keep").build(),
                Context.root()))
        .hasSize(1)
        .containsEntry("test", "keep");
  }

  @Test
  public void appendAttributes_works() {
    AttributesProcessor processor =
        AttributesProcessors.appendAttributes(Attributes.builder().put("append", "me").build());
    assertThat(processor.process(Attributes.empty(), Context.root()))
        .hasSize(1)
        .containsEntry("append", "me");
  }

  @Test
  public void appendAttributes_doesNotOverrideExistingKeys() {
    AttributesProcessor processor =
        AttributesProcessors.appendAttributes(Attributes.builder().put("test", "drop").build());
    assertThat(processor.process(Attributes.builder().put("test", "keep").build(), Context.root()))
        .hasSize(1)
        .containsEntry("test", "keep");
  }

  @Test
  public void appendBaggage_works() {
    AttributesProcessor processor = AttributesProcessors.appendBaggage();
    Baggage baggage = Baggage.builder().put("baggage", "value").build();
    Context context = Context.root().with(baggage);

    assertThat(processor.process(Attributes.builder().put("test", "keep").build(), context))
        .hasSize(2)
        .containsEntry("test", "keep")
        .containsEntry("baggage", "value");
  }

  @Test
  public void appendBaggage_doesNotOverrideExistingKeys() {
    AttributesProcessor processor = AttributesProcessors.appendBaggage();
    Baggage baggage = Baggage.builder().put("test", "drop").build();
    Context context = Context.root().with(baggage);

    assertThat(processor.process(Attributes.builder().put("test", "keep").build(), context))
        .hasSize(1)
        .containsEntry("test", "keep");
  }

  @Test
  public void appendBaggageByKeys_works() {
    AttributesProcessor processor =
        AttributesProcessors.appendBaggageByKeys(Pattern.compile("keep"));
    Baggage baggage = Baggage.builder().put("baggage", "value").put("keep", "baggage").build();
    Context context = Context.root().with(baggage);

    assertThat(processor.process(Attributes.builder().put("test", "keep").build(), context))
        .hasSize(2)
        .containsEntry("test", "keep")
        .containsEntry("keep", "baggage");
  }

  @Test
  public void proccessors_joinByThen() {
    // Baggage should be added, then all keys filtered.
    AttributesProcessor processor =
        AttributesProcessors.appendBaggage()
            .then(AttributesProcessors.filterKeys(Pattern.compile("baggage")));
    Baggage baggage = Baggage.builder().put("baggage", "value").put("keep", "baggage").build();
    Context context = Context.root().with(baggage);

    assertThat(processor.process(Attributes.builder().put("test", "keep").build(), context))
        .containsEntry("baggage", "value")
        .hasSize(1);
  }
}
