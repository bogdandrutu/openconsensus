/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.context;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
      TraceContext braveContextToAttach = ((BraveContextWrapper) toAttach).braveContext;

      CurrentTraceContext currentTraceContext = Tracing.current().currentTraceContext();
      TraceContext currentBraveContext = currentTraceContext.get();
      if (currentBraveContext == braveContextToAttach) {
        return Scope.noop();
      }

      CurrentTraceContext.Scope braveScope = currentTraceContext.newScope(braveContextToAttach);
      return braveScope::close;
    }

    @Override
    public Context current() {
      TraceContext current = Tracing.current().currentTraceContext().get();
      if (current != null) {
        return new BraveContextWrapper(current);
      }
      return root();
    }

    @Override
    public Context root() {
      return BraveContextWrapper.ROOT;
    }
  }

  private static class BraveContextValues {
    private final Object[] values;

    BraveContextValues(Object key, Object value) {
      this.values = new Object[] {key, value};
    }

    BraveContextValues(Object[] values) {
      this.values = values;
    }

    Object getValue(Object key) {
      for (int i = 0; i < values.length; i += 2) {
        if (values[i] == key) {
          return values[i + 1];
        }
      }
      return null;
    }

    BraveContextValues with(Object key, Object value) {
      final Object[] copy;
      for (int i = 0; i < values.length; i += 2) {
        if (values[i] == key) {
          copy = values.clone();
          copy[i + 1] = value;
          return new BraveContextValues(copy);
        }
      }

      copy = Arrays.copyOf(values, values.length + 2);
      copy[values.length - 2] = key;
      copy[values.length - 1] = value;
      return new BraveContextValues(copy);
    }
  }

  private static class BraveContextWrapper implements Context {

    private static final BraveContextWrapper ROOT =
        new BraveContextWrapper(TraceContext.newBuilder().traceId(1).spanId(1).build());

    private final TraceContext braveContext;

    private BraveContextWrapper(TraceContext braveContext) {
      this.braveContext = braveContext;
    }

    @Override
    public <V> V get(ContextKey<V> key) {
      BraveContextValues values = braveContext.findExtra(BraveContextValues.class);
      if (values == null) {
        return null;
      }
      @SuppressWarnings("unchecked")
      V value = (V) values.getValue(key);
      return value;
    }

    @Override
    public <V> Context with(ContextKey<V> k1, V v1) {
      List<Object> extras = braveContext.extra();
      BraveContextValues values = null;
      int existingValuesIndex = -1;
      for (int i = 0; i < extras.size(); i++) {
        Object extra = extras.get(i);
        if (extra instanceof BraveContextValues) {
          values = (BraveContextValues) extra;
          existingValuesIndex = i;
          break;
        }
      }
      final List<Object> newExtras;
      if (values == null) {
        values = new BraveContextValues(k1, v1);
        newExtras = new ArrayList<>(extras.size() + 1);
        newExtras.addAll(extras);
        newExtras.add(values);
      } else {
        newExtras = new ArrayList<>(extras);
        newExtras.set(existingValuesIndex, values.with(k1, v1));
      }

      TraceContext.Builder builder = braveContext.toBuilder();
      builder.clearExtra();
      newExtras.forEach(builder::addExtra);
      return new BraveContextWrapper(builder.build());
    }
  }
}
