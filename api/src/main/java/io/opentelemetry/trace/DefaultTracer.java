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

package io.opentelemetry.trace;

import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.internal.Utils;
import io.opentelemetry.trace.propagation.ContextUtils;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;

/**
 * No-op implementations of {@link Tracer}.
 *
 * @since 0.1.0
 */
@ThreadSafe
public final class DefaultTracer implements Tracer {
  private static final DefaultTracer INSTANCE = new DefaultTracer();

  /**
   * Returns a {@code Tracer} singleton that is the default implementations for {@link Tracer}.
   *
   * @return a {@code Tracer} singleton that is the default implementations for {@link Tracer}.
   * @since 0.1.0
   */
  public static Tracer getInstance() {
    return INSTANCE;
  }

  @Override
  public Span getCurrentSpan() {
    return ContextUtils.getSpan();
  }

  @Override
  public Scope withSpan(Span span) {
    return ContextUtils.withScopedSpan(span);
  }

  @Override
  public Span.Builder spanBuilder(String spanName) {
    return NoopSpanBuilder.create(spanName);
  }

  private DefaultTracer() {}

  // Noop implementation of Span.Builder.
  private static final class NoopSpanBuilder implements Span.Builder {
    static NoopSpanBuilder create(String spanName) {
      return new NoopSpanBuilder(spanName);
    }

    private boolean isRootSpan;
    private SpanContext spanContext;

    @Override
    public Span startSpan() {
      if (spanContext == null && !isRootSpan) {
        spanContext = getAnySpanContext(Context.current());
      }

      return spanContext != null && !SpanContext.getInvalid().equals(spanContext)
          ? new DefaultSpan(spanContext)
          : DefaultSpan.createRandom();
    }

    @Override
    public NoopSpanBuilder setParent(Span parent) {
      Utils.checkNotNull(parent, "parent");
      spanContext = parent.getContext();
      return this;
    }

    @Override
    public NoopSpanBuilder setParent(SpanContext remoteParent) {
      Utils.checkNotNull(remoteParent, "remoteParent");
      spanContext = remoteParent;
      return this;
    }

    @Override
    public NoopSpanBuilder setParent(Context context) {
      Utils.checkNotNull(context, "context");
      spanContext = getAnySpanContext(context);
      return this;
    }

    private static SpanContext getAnySpanContext(Context context) {
      Span span = ContextUtils.getSpan(context);
      if (span != DefaultSpan.getInvalid()) {
        return span.getContext();
      }

      return ContextUtils.getSpanContext(context);
    }

    @Override
    public NoopSpanBuilder setNoParent() {
      isRootSpan = true;
      return this;
    }

    @Override
    public NoopSpanBuilder addLink(SpanContext spanContext) {
      return this;
    }

    @Override
    public NoopSpanBuilder addLink(
        SpanContext spanContext, Map<String, AttributeValue> attributes) {
      return this;
    }

    @Override
    public NoopSpanBuilder addLink(Link link) {
      return this;
    }

    @Override
    public NoopSpanBuilder setAttribute(String key, String value) {
      Utils.checkNotNull(key, "key");
      Utils.checkNotNull(value, "value");
      return this;
    }

    @Override
    public NoopSpanBuilder setAttribute(String key, long value) {
      Utils.checkNotNull(key, "key");
      return this;
    }

    @Override
    public NoopSpanBuilder setAttribute(String key, double value) {
      Utils.checkNotNull(key, "key");
      return this;
    }

    @Override
    public NoopSpanBuilder setAttribute(String key, boolean value) {
      Utils.checkNotNull(key, "key");
      return this;
    }

    @Override
    public NoopSpanBuilder setAttribute(String key, AttributeValue value) {
      Utils.checkNotNull(key, "key");
      Utils.checkNotNull(value, "value");
      return this;
    }

    @Override
    public NoopSpanBuilder setSpanKind(Span.Kind spanKind) {
      return this;
    }

    @Override
    public NoopSpanBuilder setStartTimestamp(long startTimestamp) {
      Utils.checkArgument(startTimestamp >= 0, "Negative startTimestamp");
      return this;
    }

    private NoopSpanBuilder(String name) {
      Utils.checkNotNull(name, "name");
    }
  }
}
