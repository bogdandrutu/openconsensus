/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.regex.Pattern;

/** Collection of factory methods to construct {@link AttributesProcessor}s. */
public final class AttributesProcessors {
  private AttributesProcessors() {}

  /**
   * Creates an {@link AttributesProcessor} that filters incoming keys.
   *
   * @param keyPattern Regex where all matching incoming keys are preserved.
   */
  public static AttributesProcessor filterKeys(Pattern keyPattern) {
    return new SimpleAttributesProcessor() {
      @Override
      @SuppressWarnings("unchecked")
      // We are unable to get existentials working for attribute k-v pairs without
      // better type gymnastics in the core library.
      protected Attributes proocess(Attributes incoming) {
        AttributesBuilder result = Attributes.builder();
        incoming.forEach(
            (k, v) -> {
              if (keyPattern.matcher(k.getKey()).matches()) {
                result.put((AttributeKey<Object>) k, v);
              }
            });
        return result.build();
      }

      @Override
      public final String toString() {
        return "filterKeys(" + keyPattern + ")";
      }
    };
  }

  /**
   * Creates an {@link AttributesProcessor} the appends attributes to existing metrics.
   *
   * @param append A set of attributes to append to metrics if they don't already exist.
   */
  public static AttributesProcessor appendAttributes(final Attributes append) {
    return new SimpleAttributesProcessor() {
      @Override
      protected Attributes proocess(Attributes incoming) {
        // We start with append, so all incoming attributes override the append list
        // on `.putAll` method.
        return append.toBuilder().putAll(incoming).build();
      }
    };
  }

  /**
   * Creates an {@link AttributesProcessor} the appends any matching baggage keys.
   *
   * @param keyPattern Regex where all matching baggage keys are included.
   */
  public static AttributesProcessor appendBaggageByKeys(final Pattern keyPattern) {
    return new BaggageAttributesProcessor() {
      @Override
      protected Attributes process(Attributes attributes, Baggage baggage) {
        AttributesBuilder result = Attributes.builder();
        baggage.forEach(
            (k, v) -> {
              if (keyPattern.matcher(k).matches()) {
                result.put(k, v.getValue());
              }
            });
        // Override any baggage keys with existing keys.
        result.putAll(attributes);
        return result.build();
      }

      @Override
      public final String toString() {
        return "appendBaggageByKeys(" + keyPattern + ")";
      }
    };
  }
  /**
   * Creates an {@link AttributesProcessor} that appends all baggage values to metric attributes.
   */
  public static AttributesProcessor appendBaggage() {
    return appendBaggageByKeys(Pattern.compile(".*"));
  }
}
