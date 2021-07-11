/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import java.util.Arrays;
import javax.annotation.concurrent.Immutable;

/**
 * An {@code AttributesProcessor} is used by {@code View}s to define the actual recorded set of
 * attributes.
 *
 * <p>An AttributesProcessor is used to define the actual set of attributes that will be used in a
 * Metric vs. the inbound set of attributes from a measurement.
 */
@FunctionalInterface
@Immutable
public interface AttributesProcessor {

  /**
   * Manipulates a set of attributes, returning the desired set.
   *
   * @param incoming Attributes assocaited with an incoming measurement.
   * @param context The context associated with the measurement.
   */
  Attributes process(Attributes incoming, Context context);

  /**
   * If true, this ensures the `Context` argument of the attributes processor is always acurate.
   * This will prevents bound instruments from pre-locking their metric-attributes and defer until
   * context is available.
   */
  default boolean usesContext() {
    return true;
  }

  /** Joins this attribute processor with another that operates after this one. */
  default AttributesProcessor then(AttributesProcessor other) {
    if (other instanceof JoinedAttribtuesProcessor) {
      return ((JoinedAttribtuesProcessor) other).prepend(this);
    }
    return new JoinedAttribtuesProcessor(Arrays.asList(this, other));
  }

  /** No-op version of attributes processer, returns what it gets. */
  public static AttributesProcessor NOOP =
      new AttributesProcessor() {
        @Override
        public Attributes process(Attributes incoming, Context context) {
          return incoming;
        }

        @Override
        public boolean usesContext() {
          return false;
        }

        @Override
        public String toString() {
          return "NoopAttributesProcessor";
        }
      };
}
