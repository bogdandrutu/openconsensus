/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.context.propagation;

import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

final class CompositeTextMapPropagator implements TextMapPropagator {
  private final TextMapPropagator[] extractors;
  private final TextMapPropagator[] injectors;
  private final Collection<String> allFields;

  CompositeTextMapPropagator(TextMapPropagator... textPropagators) {
    this(Arrays.asList(textPropagators));
  }

  CompositeTextMapPropagator(List<TextMapPropagator> textPropagators) {
    this.extractors = new TextMapPropagator[textPropagators.size()];
    textPropagators.toArray(this.extractors);
    this.injectors = this.extractors; // No need to copy
    this.allFields = Collections.unmodifiableList(getAllFields(this.injectors));
  }

  CompositeTextMapPropagator(
      List<TextMapPropagator> extractors, List<TextMapPropagator> injectors) {
    this.extractors = new TextMapPropagator[extractors.size()];
    extractors.toArray(this.extractors);

    this.injectors = new TextMapPropagator[injectors.size()];
    injectors.toArray(this.injectors);

    this.allFields = Collections.unmodifiableList(getAllFields(this.injectors));
  }

  @Override
  public Collection<String> fields() {
    return allFields;
  }

  private static List<String> getAllFields(TextMapPropagator[] textPropagators) {
    Set<String> fields = new LinkedHashSet<>();
    for (TextMapPropagator textPropagator : textPropagators) {
      fields.addAll(textPropagator.fields());
    }

    return new ArrayList<>(fields);
  }

  @Override
  public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
    if (context == null || setter == null) {
      return;
    }
    for (TextMapPropagator textPropagator : injectors) {
      textPropagator.inject(context, carrier, setter);
    }
  }

  @Override
  public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
    if (context == null) {
      return Context.root();
    }
    if (getter == null) {
      return context;
    }
    for (TextMapPropagator textPropagator : extractors) {
      context = textPropagator.extract(context, carrier, getter);
    }
    return context;
  }
}
