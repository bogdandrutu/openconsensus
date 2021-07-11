/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A handle for a collection-pipeline of metrics.
 *
 * <p>This class provides an efficient means of leasing and tracking collection handles.
 */
public final class CollectionHandle {
  /** The index of this handle. */
  private final int index;

  private CollectionHandle(int index) {
    this.index = index;
  }

  /** Construct a new (efficient) mutable set for tracking collection handles. */
  public static Set<CollectionHandle> mutableSet() {
    return new CollectionHandleSet();
  }

  /** Construct a new (mutable) set consistenting of the passed in collection handles. */
  public static Set<CollectionHandle> of(CollectionHandle... handles) {
    Set<CollectionHandle> result = mutableSet();
    for (CollectionHandle handle : handles) {
      result.add(handle);
    }
    return result;
  }

  // TODO: Per-MeterProvider version?
  public static CollectionHandle create() {
    return new CollectionHandle(nextIndex.getAndIncrement());
  }

  @Override
  public int hashCode() {
    return index;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null) {
      return false;
    }
    if (!(other instanceof CollectionHandle)) {
      return false;
    }
    return index == ((CollectionHandle) other).index;
  }

  @Override
  public String toString() {
    return "CollectionHandle(" + index + ")";
  }

  // TODO: Better mechanism of this?
  private static final AtomicInteger nextIndex = new AtomicInteger(1);

  /** An optimised bitset version of {@code Set<CollectionHandle>}. */
  private static class CollectionHandleSet extends AbstractSet<CollectionHandle> {
    private final BitSet storage = new BitSet();

    @Override
    public Iterator<CollectionHandle> iterator() {
      return new MyIterator();
    }

    @Override
    public boolean add(CollectionHandle handle) {
      if (storage.get(handle.index)) {
        return false;
      }
      storage.set(handle.index);
      return true;
    }

    @Override
    public boolean contains(Object handle) {
      if (handle instanceof CollectionHandle) {
        return storage.get(((CollectionHandle) handle).index);
      }
      return false;
    }

    @Override
    public boolean containsAll(Collection<?> other) {
      if (other instanceof CollectionHandleSet) {
        BitSet result = new BitSet();
        BitSet otherStorage = ((CollectionHandleSet) other).storage;
        result.or(storage);
        result.and(otherStorage);
        return result.equals(otherStorage);
      }
      return super.containsAll(other);
    }

    private class MyIterator implements Iterator<CollectionHandle> {
      private int currentIndex = 0;

      @Override
      public boolean hasNext() {
        return (currentIndex != -1) && storage.nextSetBit(currentIndex) != -1;
      }

      @Override
      public CollectionHandle next() {
        int result = storage.nextSetBit(currentIndex);
        if (result != -1) {
          // Start checking next bit next time.
          currentIndex = result + 1;
          return new CollectionHandle(result);
        }
        throw new NoSuchElementException("Called `.next` on iterator with no remaining values.");
      }
    }

    @Override
    public int size() {
      return storage.cardinality();
    }
  }
}
