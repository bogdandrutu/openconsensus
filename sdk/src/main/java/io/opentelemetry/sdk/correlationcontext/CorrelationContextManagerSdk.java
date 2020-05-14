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

package io.opentelemetry.sdk.correlationcontext;

import io.opentelemetry.correlationcontext.CorrelationContext;
import io.opentelemetry.correlationcontext.CorrelationContextManager;
import io.opentelemetry.scope.DefaultScopeManager;
import io.opentelemetry.scope.ScopeManager;

/**
 * {@link CorrelationContextManagerSdk} is SDK implementation of {@link CorrelationContextManager}.
 */
public class CorrelationContextManagerSdk implements CorrelationContextManager {

  // TODO (trask) should be injected
  private final ScopeManager scopeManager = DefaultScopeManager.getInstance();

  @Override
  public CorrelationContext.Builder contextBuilder() {
    return new CorrelationContextSdk.Builder(scopeManager);
  }
}
