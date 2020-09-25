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

package io.opentelemetry.sdk.trace;

import io.opentelemetry.common.AttributeConsumer;
import io.opentelemetry.common.AttributeKey;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.common.ReadableAttributes;
import java.util.HashMap;
import java.util.Map;

/**
 * A map with a fixed capacity that drops attributes when the map gets full.
 *
 * <p>Note: this doesn't implement the Map interface, but behaves very similarly to one.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class AttributesMap implements ReadableAttributes {
  private final Map<AttributeKey, Object> data = new HashMap<>();

  private final long capacity;
  private int totalAddedValues = 0;

  AttributesMap(long capacity) {
    this.capacity = capacity;
  }

  public <T> void put(AttributeKey<T> key, T value) {
    if (key == null || key.getKey() == null || value == null) {
      return;
    }
    totalAddedValues++;
    if (data.size() >= capacity && !data.containsKey(key)) {
      return;
    }
    data.put(key, value);
  }

  void remove(AttributeKey key) {
    data.remove(key);
  }

  int getTotalAddedValues() {
    return totalAddedValues;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(AttributeKey<T> key) {
    return (T) data.get(key);
  }

  @Override
  public int size() {
    return data.size();
  }

  @Override
  public boolean isEmpty() {
    return data.isEmpty();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public void forEach(AttributeConsumer consumer) {
    for (Map.Entry<AttributeKey, Object> entry : data.entrySet()) {
      AttributeKey key = entry.getKey();
      Object value = entry.getValue();
      consumer.consume(key, value);
    }
  }

  @Override
  public String toString() {
    return "AttributesMap{"
        + "data="
        + data
        + ", capacity="
        + capacity
        + ", totalAddedValues="
        + totalAddedValues
        + '}';
  }

  @SuppressWarnings("rawtypes")
  ReadableAttributes immutableCopy() {
    Attributes.Builder builder = Attributes.newBuilder();
    for (Map.Entry<AttributeKey, Object> entry : data.entrySet()) {
      builder.setAttribute(entry.getKey(), entry.getValue());
    }
    return builder.build();
  }
}
