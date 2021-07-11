/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

/** An {@link AttributesProcessor} that dos not need access to context. */
public abstract class SimpleAttributesProcessor implements AttributesProcessor {

  /**
   * Manipulates a set of attributes, returning the desired set.
   *
   * @param incoming Attributes assocaited with an incoming measurement.
   */
  protected abstract Attributes proocess(Attributes incoming);

  @Override
  public final Attributes process(Attributes incoming, Context context) {
    return proocess(incoming);
  }

  @Override
  public final boolean usesContext() {
    return false;
  }
}
