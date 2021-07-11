/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import io.opentelemetry.sdk.metrics.instrument.Measurement;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/** A highly-concurrent mechanism of storing exemplars. */
@ThreadSafe
public class ExemplarList {
  private final AtomicReference<Node> head = new AtomicReference<>(null);

  /** Atomic-add of the measurement to the exemplar list. */
  public void add(Measurement measurement) {
    Node previous = head.get();
    Node next = new Node(measurement, previous);
    // Spin-lock to append to list.
    while (!head.compareAndSet(previous, next)) {
      previous = head.get();
      next = new Node(measurement, previous);
    }
  }

  /** Atomically clear the current exemplar list, and return the collection of exemplars. */
  public Iterable<Measurement> collectAndReset() {
    // After this atomic operation, new exemplars will be reported in next time-set.
    Node collection = head.getAndSet(null);
    if (collection == null) {
      return Collections.emptyList();
    }
    return collection;
  }

  private static class Node implements Iterable<Measurement> {
    final Measurement value;
    @Nullable final Node next;

    Node(Measurement value) {
      this.value = value;
      this.next = null;
    }

    Node(Measurement value, Node next) {
      this.value = value;
      this.next = next;
    }

    @Override
    public Iterator<Measurement> iterator() {
      return new MyIterator(this);
    }

    private static class MyIterator implements Iterator<Measurement> {
      private Node current;

      MyIterator(Node current) {
        this.current = current;
      }

      @Override
      public Measurement next() {
        if (current == null) {
          throw new NoSuchElementException();
        }
        Measurement result = current.value;
        current = current.next;
        return result;
      }

      @Override
      public boolean hasNext() {
        return current != null;
      }
    }
  }
}
