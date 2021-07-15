/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.context;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.List;
import javax.annotation.Nullable;

public class BraveContextStorageProvider implements ContextStorageProvider {

  @Override
  public ContextStorage get() {
    return BraveContextStorage.INSTANCE;
  }

  @SuppressWarnings("ReferenceEquality")
  private enum BraveContextStorage implements ContextStorage {
    INSTANCE;

    @Override
    public Scope attach(Context toAttach) {
      CurrentTraceContext currentTraceContext = Tracing.current().currentTraceContext();
      TraceContext currentBraveContext = currentTraceContext.get();

      Context currentContext = fromBraveContext(currentBraveContext);
      if (currentContext == toAttach) {
        return Scope.noop();
      }

      TraceContext newBraveContext;
      if (toAttach instanceof BraveContextWrapper) {
        newBraveContext = ((BraveContextWrapper) toAttach).toBraveContext();
      } else {
        newBraveContext = toBraveContext(currentBraveContext, toAttach);
      }

      if (currentBraveContext == newBraveContext) {
        return Scope.noop();
      }
      CurrentTraceContext.Scope braveScope = currentTraceContext.newScope(newBraveContext);
      return braveScope::close;
    }

    @Override
    public Context current() {
      return new BraveContextWrapper(Tracing.current().currentTraceContext().get());
    }

    @Override
    public Context root() {
      return BraveContextWrapper.ROOT;
    }
  }

  // Need to wrap the Context because brave findExtra searches for perfect match of the class.
  static final class BraveContextWrapper implements Context {

    static final BraveContextWrapper ROOT = new BraveContextWrapper(null, ArrayBasedContext.root());

    @Nullable private final TraceContext baseBraveContext;
    private final Context delegate;

    BraveContextWrapper(@Nullable TraceContext baseBraveContext) {
      this(baseBraveContext, fromBraveContext(baseBraveContext));
    }

    BraveContextWrapper(@Nullable TraceContext baseBraveContext, Context delegate) {
      this.baseBraveContext = baseBraveContext;
      this.delegate = delegate;
    }

    TraceContext toBraveContext() {
      if (fromBraveContext(baseBraveContext) == delegate) {
        return baseBraveContext;
      }
      return BraveContextStorageProvider.toBraveContext(baseBraveContext, delegate);
    }

    @Nullable
    @Override
    public <V> V get(ContextKey<V> key) {
      return delegate.get(key);
    }

    @Override
    public <V> Context with(ContextKey<V> k1, V v1) {
      return new BraveContextWrapper(baseBraveContext, delegate.with(k1, v1));
    }
  }

  static TraceContext toBraveContext(@Nullable TraceContext braveContext, Context context) {
    TraceContext.Builder builder =
        braveContext == null ? TraceContext.newBuilder() : braveContext.toBuilder();
    return builder.addExtra(new ContextWrapper(context)).build();
  }

  private static Context fromBraveContext(@Nullable TraceContext braveContext) {
    if (braveContext == null) {
      return BraveContextWrapper.ROOT.delegate;
    }
    List<Object> extra = braveContext.extra();
    for (int i = extra.size() - 1; i >= 0; i--) {
      Object nextExtra = extra.get(i);
      if (nextExtra.getClass() == ContextWrapper.class) {
        return ((ContextWrapper) nextExtra).context;
      }
    }
    return Context.root();
  }

  private static final class ContextWrapper {
    private final Context context;

    private ContextWrapper(Context context) {
      this.context = context;
    }
  }
}
