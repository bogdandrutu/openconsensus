/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.baggage;

import io.opentelemetry.api.internal.ImmutableKeyValuePairs;
import io.opentelemetry.api.internal.StringUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
final class ImmutableBaggage extends ImmutableKeyValuePairs<String, BaggageEntry>
    implements Baggage {

  private static final Baggage EMPTY = new ImmutableBaggage.Builder().build();

  private ImmutableBaggage(Object[] data) {
    super(data);
  }

  static Baggage empty() {
    return EMPTY;
  }

  static BaggageBuilder builder() {
    return new Builder();
  }

  @Nullable
  @Override
  public String getEntryValue(String entryKey) {
    BaggageEntry entry = get(entryKey);
    return entry != null ? entry.getValue() : null;
  }

  @Override
  public BaggageBuilder toBuilder() {
    return new Builder(new ArrayList<>(data()));
  }

  private static Baggage sortAndFilterToBaggage(Object[] data) {
    return new ImmutableBaggage(data);
  }

  // TODO: Migrate to AutoValue.Builder
  // @AutoValue.Builder
  static class Builder implements BaggageBuilder {

    private final List<Object> data;

    Builder() {
      this.data = new ArrayList<>();
    }

    Builder(List<Object> data) {
      this.data = data;
    }

    @Override
    public BaggageBuilder put(String key, String value, BaggageEntryMetadata entryMetadata) {
      if (!isKeyValid(key) || !isValueValid(value) || entryMetadata == null) {
        return this;
      }
      data.add(key);
      data.add(ImmutableEntry.create(value, entryMetadata));

      return this;
    }

    @Override
    public BaggageBuilder remove(String key) {
      if (key == null) {
        return this;
      }
      data.add(key);
      data.add(null);
      return this;
    }

    @Override
    public Baggage build() {
      return sortAndFilterToBaggage(data.toArray());
    }
  }

  /**
   * Determines whether the given {@code String} is a valid entry key.
   *
   * @param name the entry key name to be validated.
   * @return whether the name is valid.
   */
  private static boolean isKeyValid(String name) {
    return name != null && !name.trim().isEmpty() && StringUtils.isPrintableString(name);
  }

  /**
   * Determines whether the given {@code String} is a valid entry value.
   *
   * @param value the entry value to be validated.
   * @return whether the value is valid.
   */
  private static boolean isValueValid(String value) {
    return value != null && StringUtils.isPrintableString(value);
  }
}
