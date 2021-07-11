/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import io.opentelemetry.sdk.metrics.instrument.Measurement;
import io.opentelemetry.sdk.metrics.state.StorageHandle;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * SynchronousHandle represents the abstract class that is used for synchronous instruments. It must
 * be thread-safe and avoid locking when possible, because values are recorded synchronously on the
 * calling thread.
 *
 * <p>A {@link StorageHandle} must be created for every unique {@code Attributes} recorded, and can
 * be referenced by the bound instruments.
 *
 * <p><i>Note: {@code SynchronousHandle} needs an efficient mechanism of sampling exemplars</i>
 *
 * <p>It atomically counts the number of references (usages) while also keeping a state of
 * mapped/unmapped into an external map. It uses an atomic value where the least significant bit is
 * used to keep the state of mapping ('1' is used for unmapped and '0' is for mapped) and the rest
 * of the bits are used for reference (usage) counting.
 */
@ThreadSafe
public abstract class SynchronousHandle<T> implements StorageHandle {
  // Atomically counts the number of references (usages) while also keeping a state of
  // mapped/unmapped into a registry map.
  private final AtomicLong refCountMapped;
  // Note: This is not 100% thread-safe. There is a race condition where recordings can
  // be made in the moment between the reset and the setting of this field's value. In those
  // cases, it is possible that a recording could be missed in a given recording interval, but
  // it should be picked up in the next, assuming that more recordings are being made.
  private volatile boolean hasRecordings = false;
  // Determine if a measurement should go into exemplar pool.
  private final ExemplarSampler sampler;
  private final ExemplarList exemplars;

  protected SynchronousHandle(ExemplarSampler sampler) {
    // Start with this binding already bound.
    this.refCountMapped = new AtomicLong(2);
    this.sampler = sampler;
    this.exemplars = new ExemplarList();
  }

  /**
   * Acquires this {@code Aggregator} for use. Returns {@code true} if the entry is still mapped and
   * increases the reference usages, if unmapped returns {@code false}.
   *
   * @return {@code true} if successful.
   */
  public final boolean acquire() {
    // Every reference adds/removes 2 instead of 1 to avoid changing the mapping bit.
    return (refCountMapped.addAndGet(2L) & 1L) == 0;
  }

  /** Release this {@code Aggregator}. It decreases the reference usage. */
  @Override
  public final void release() {
    // Every reference adds/removes 2 instead of 1 to avoid changing the mapping bit.
    refCountMapped.getAndAdd(-2L);
  }

  /**
   * Flips the mapped bit to "unmapped" state and returns true if both of the following conditions
   * are true upon entry to this function: 1) There are no active references; 2) The mapped bit is
   * in "mapped" state; otherwise no changes are done to mapped bit and false is returned.
   *
   * @return {@code true} if successful.
   */
  public final boolean tryUnmap() {
    if (refCountMapped.get() != 0) {
      // Still references (usages) to this bound or already unmapped.
      return false;
    }
    return refCountMapped.compareAndSet(0L, 1L);
  }

  /**
   * Returns the current value into as {@link T} and resets the current value in this {@code
   * Aggregator}.
   */
  @Nullable
  public final T accumulateThenReset() {
    if (!hasRecordings) {
      return null;
    }
    hasRecordings = false;
    return doAccumulateThenReset(exemplars.collectAndReset());
  }

  /**
   * Implementation of the {@code accumulateThenReset}.
   *
   * <p>Note: There's a bit of a race condition (today) where exemplars MAY be reported from the
   * previous collection period.
   */
  protected abstract T doAccumulateThenReset(Iterable<Measurement> exemplars);

  /**
   * Updates the current aggregator with a newly recorded {@link Measurement}.
   *
   * @param value the new {@link Measurement} value to be added.
   */
  @Override
  public final void record(Measurement value) {
    doRecord(value);
    if (sampler.shouldSample(value)) {
      exemplars.add(value);
    }
    hasRecordings = true;
  }

  /**
   * Concrete Aggregator instances should implement this method in order support recordings of
   * measurements.
   */
  protected abstract void doRecord(Measurement value);
}
