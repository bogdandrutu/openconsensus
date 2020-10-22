/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opencensusshim;

import io.opencensus.trace.ContextHandle;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class OpenTelemetryCtx implements ContextHandle {

  private final Context context;
  private Scope scope;

  public OpenTelemetryCtx(Context context) {
    this.context = context;
  }

  Context getContext() {
    return context;
  }

  @Override
  public ContextHandle attach() {
    scope = context.makeCurrent();
    return this;
  }

  @Override
  public void detach(ContextHandle ctx) {
    OpenTelemetryCtx impl = (OpenTelemetryCtx) ctx;
    impl.scope.close();
  }
}
