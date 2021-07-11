/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.instrument;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import javax.annotation.concurrent.Immutable;

/**
 * Identifier for an instrument.
 *
 * <p>Note: Two instruments with the same name but different {@link InstrumentType} or {@link
 * InstrumentValueType} are not allowed to be registered against the same {@code Meter}.
 */
@AutoValue
@Immutable
public abstract class InstrumentDescriptor {
  public static InstrumentDescriptor create(
      String name,
      String description,
      String unit,
      InstrumentType type,
      InstrumentValueType valueType) {
    return new AutoValue_InstrumentDescriptor(name, description, unit, type, valueType);
  }

  public abstract String getName();

  public abstract String getDescription();

  public abstract String getUnit();

  public abstract InstrumentType getType();

  public abstract InstrumentValueType getValueType();

  /**
   * Returns true if we can treat two isntrument descriptors as the same.
   *
   * <p>By specification, an instrument is identified by its name and instrument type, with the
   * abilitty for SDKs to also limit to the same data value being reported. If description or unit
   * differ, we can ignore that. Also note, names are considered case insensitve for the purpose of
   * Instrument identity.
   */
  public boolean isCompatibleWith(InstrumentDescriptor other) {
    // Note we are meant to do case insenstive name comparison.
    // This is not super locale-friendly (and will fail in some locales),
    // but is consistent with all other implementations leveraging case in the SDK.
    return getName().equalsIgnoreCase(other.getName())
        && (getType() == other.getType())
        && (getValueType() == other.getValueType());
  }

  @Memoized
  @Override
  public abstract int hashCode();
}
