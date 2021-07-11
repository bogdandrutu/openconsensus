/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.Collection;
import javax.annotation.concurrent.Immutable;

/** A {@link AttributesProcessor} that runs a sequence of processors. */
@Immutable
public final class JoinedAttribtuesProcessor implements AttributesProcessor {
  private final Collection<AttributesProcessor> processors;
  private final boolean usesContextCache;

  JoinedAttribtuesProcessor(Collection<AttributesProcessor> processors) {
    this.processors = processors;
    this.usesContextCache =
        processors.stream().map(AttributesProcessor::usesContext).reduce(false, (l, r) -> l || r);
  }

  @Override
  public Attributes process(Attributes incoming, Context context) {
    Attributes result = incoming;
    for (AttributesProcessor processor : processors) {
      result = processor.process(result, context);
    }
    return result;
  }

  @Override
  public boolean usesContext() {
    return usesContextCache;
  }

  @Override
  public AttributesProcessor then(AttributesProcessor other) {
    ArrayList<AttributesProcessor> newList = new ArrayList<>(processors);
    if (other instanceof JoinedAttribtuesProcessor) {
      newList.addAll(((JoinedAttribtuesProcessor) other).processors);
    } else {
      newList.add(other);
    }
    return new JoinedAttribtuesProcessor(newList);
  }

  AttributesProcessor prepend(AttributesProcessor other) {
    ArrayList<AttributesProcessor> newList = new ArrayList<>(processors.size() + 1);
    newList.add(other);
    newList.addAll(processors);
    return new JoinedAttribtuesProcessor(newList);
  }
}
