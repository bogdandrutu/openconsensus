/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

/** An {@link AttributesProcessor} that leverages {@link Baggage}. */
public abstract class BaggageAttributesProcessor implements AttributesProcessor {
  /**
   * Manipulates a set of attributes, returning the desired set.
   *
   * @param incoming Attributes assocaited with an incoming measurement.
   * @param baggage The baggage attached too the current context.
   */
  protected abstract Attributes process(Attributes incoming, Baggage baggage);

  @Override
  public final Attributes process(Attributes incoming, Context context) {
    return process(incoming, Baggage.fromContext(context));
  }
}
