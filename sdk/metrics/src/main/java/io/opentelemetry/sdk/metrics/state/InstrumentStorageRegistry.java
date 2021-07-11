/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.state;

import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Basic registry class for metrics instruments. The current implementation allows instruments to be
 * registered only once for a given name.
 *
 * <p>This class is intended to match specification restrictions on Meter, providing a shared-state
 * backing for Meters.
 *
 * <p>Going forward, this registry needs to be modified to support/allow the following: 1. The
 * InstrumentStorage APi should be passed to the synchronous instrument for writing. 2. For
 * Asynchronous instruments, we want to make sure metrics are collected appropriately. 3. When a
 * view exists, we replace the normal InstrumentStorage returned with a "view" version. This should:
 * aggreate appropriate instruments returned + join async builders if appropriate, etc.
 */
public final class InstrumentStorageRegistry {
  private final ConcurrentMap<String, InstrumentStorage> registry = new ConcurrentHashMap<>();

  /**
   * Registers the given {@code instrument} to this registry. Returns the registered instrument if
   * no other instrument with the same name is registered or a previously registered instrument with
   * same name and equal with the current instrument, otherwise throws an exception.
   *
   * @param descriptor the instrument description.
   * @param instrumentBuilder factory for a new {@link InstrumentStorage} for this instrument.
   * @return the given instrument if no instrument with same name already registered, otherwise the
   *     previous registered instrument.
   * @throws IllegalArgumentException if instrument cannot be registered.
   */
  @SuppressWarnings("unchecked")
  public <I extends InstrumentStorage> I register(
      InstrumentDescriptor descriptor, Supplier<I> instrumentBuilder) {
    InstrumentStorage oldOrNewInstrument =
        registry.computeIfAbsent(
            descriptor.getName().toLowerCase(), (key) -> instrumentBuilder.get());
    // TODO: We want to amke sure the storage for this instrument is compatible with the previous.
    // TODO: We also want to allow "views" to present writable interfaces for instruments.
    return (I) oldOrNewInstrument;
  }

  /**
   * Returns a {@code Collection} view of the registered instruments.
   *
   * @return a {@code Collection} view of the registered instruments.
   */
  public Collection<InstrumentStorage> getInstruments() {
    return Collections.unmodifiableCollection(new ArrayList<>(registry.values()));
  }
}
