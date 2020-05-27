/*
 * Copyright 2020, OpenTelemetry Authors
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

package io.opentelemetry.common;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** javadoc me. */
@Immutable
public abstract class Attributes implements Iterable<Entry<String, AttributeValue>> {
  private static final Attributes EMPTY = new EmptyAttributes();

  /** javadoc me. */
  public abstract Set<String> keys();

  /** javadoc me. */
  @Nullable
  public abstract AttributeValue getValue(String key);

  /** javadoc me. */
  public static Attributes fromMap(Map<String, AttributeValue> values) {
    List<Object> data = new ArrayList<>(values.size() * 2);
    for (Entry<String, AttributeValue> entry : values.entrySet()) {
      data.add(entry.getKey());
      data.add(entry.getValue());
    }
    return sortAndFilter(data);
  }

  /** TODO: Implement sorting and filtering out duplicate keys and null keys. */
  private static Attributes sortAndFilter(List<Object> data) {
    return new AutoValue_Attributes_ArrayBackedAttributes(data);
  }

  /** javadoc me. */
  public static Attributes empty() {
    return EMPTY;
  }

  /** javadoc me. */
  public static Attributes of(String key, AttributeValue value) {
    return new SingleAttributes(key, value);
  }

  /** javadoc me. */
  public static Attributes of(
      String key1, AttributeValue value1, String key2, AttributeValue value2) {
    return sortAndFilter(
        Arrays.asList(
            key1, value1,
            key2, value2));
  }

  /** javadoc me. */
  public static Attributes of(
      String key1,
      AttributeValue value1,
      String key2,
      AttributeValue value2,
      String key3,
      AttributeValue value3) {
    return sortAndFilter(
        Arrays.asList(
            key1, value1,
            key2, value2,
            key3, value3));
  }

  /** javadoc me. */
  public static Attributes of(
      String key1,
      AttributeValue value1,
      String key2,
      AttributeValue value2,
      String key3,
      AttributeValue value3,
      String key4,
      AttributeValue value4) {
    return sortAndFilter(
        Arrays.asList(
            key1, value1,
            key2, value2,
            key3, value3,
            key4, value4));
  }

  /** javadoc me. */
  public static Attributes of(
      String key1,
      AttributeValue value1,
      String key2,
      AttributeValue value2,
      String key3,
      AttributeValue value3,
      String key4,
      AttributeValue value4,
      String key5,
      AttributeValue value5) {
    return sortAndFilter(
        Arrays.asList(
            key1, value1,
            key2, value2,
            key3, value3,
            key4, value4,
            key5, value5));
  }

  /** javadoc me. */
  public static Builder newBuilder() {
    return new Builder();
  }

  @AutoValue
  @Immutable
  abstract static class ArrayBackedAttributes extends Attributes {
    abstract List<Object> data();

    ArrayBackedAttributes() {}

    @Override
    @Memoized
    public Set<String> keys() {
      List<Object> data = data();
      Set<String> results = new HashSet<>();
      for (int i = 0; i < data.size(); i++) {
        results.add((String) data.get(i++));
      }
      return results;
    }

    @Nullable
    @Override
    public AttributeValue getValue(String key) {
      List<Object> data = data();
      for (int i = 0; i < data.size(); i++) {
        if (data.get(i++).equals(key)) {
          return (AttributeValue) data.get(i);
        }
      }
      return null;
    }

    @Override
    public Iterator<Entry<String, AttributeValue>> iterator() {
      return new ArrayIterator();
    }

    private class ArrayIterator implements Iterator<Entry<String, AttributeValue>> {
      private int currentIndex = 0;

      @Override
      public boolean hasNext() {
        return data().size() > currentIndex;
      }

      @Override
      public Entry<String, AttributeValue> next() {
        if (!hasNext()) {
          throw new IllegalStateException("no more");
        }
        List<Object> data = data();
        return new SimpleImmutableEntry<>(
            (String) data.get(currentIndex++), (AttributeValue) data.get(currentIndex++));
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("remove");
      }
    }
  }

  /** javadoc me. */
  public static class Builder {
    private final List<Object> data = new ArrayList<>();

    /** javadoc me. */
    public Attributes build() {
      return sortAndFilter(Arrays.asList(data.toArray()));
    }

    /** javadoc me. */
    public Builder addAttribute(String key, String value) {
      data.add(key);
      data.add(AttributeValue.stringAttributeValue(value));
      return this;
    }

    /** javadoc me. */
    public Builder addAttribute(String key, long value) {
      data.add(key);
      data.add(AttributeValue.longAttributeValue(value));
      return this;
    }

    /** javadoc me. */
    public Builder addAttribute(String key, double value) {
      data.add(key);
      data.add(AttributeValue.doubleAttributeValue(value));
      return this;
    }

    /** javadoc me. */
    public Builder addAttribute(String key, boolean value) {
      data.add(key);
      data.add(AttributeValue.booleanAttributeValue(value));
      return this;
    }

    /** javadoc me. */
    public Builder addAttribute(String key, String... value) {
      data.add(key);
      data.add(AttributeValue.arrayAttributeValue(value));
      return this;
    }

    /** javadoc me. */
    public Builder addAttribute(String key, Long... value) {
      data.add(key);
      data.add(AttributeValue.arrayAttributeValue(value));
      return this;
    }

    /** javadoc me. */
    public Builder addAttribute(String key, Double... value) {
      data.add(key);
      data.add(AttributeValue.arrayAttributeValue(value));
      return this;
    }

    /** javadoc me. */
    public Builder addAttribute(String key, Boolean... value) {
      data.add(key);
      data.add(AttributeValue.arrayAttributeValue(value));
      return this;
    }
  }

  private static class EmptyAttributes extends Attributes {

    @Override
    public Set<String> keys() {
      return emptySet();
    }

    @Nullable
    @Override
    public AttributeValue getValue(String key) {
      return null;
    }

    @Override
    public Iterator<Entry<String, AttributeValue>> iterator() {
      return emptyIterator();
    }
  }

  private static class SingleAttributes extends Attributes {
    private final String key;
    private final AttributeValue value;

    private SingleAttributes(String key, AttributeValue value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public Set<String> keys() {
      return singleton(key);
    }

    @Nullable
    @Override
    public AttributeValue getValue(String key) {
      return this.key.equals(key) ? value : null;
    }

    @Override
    public Iterator<Entry<String, AttributeValue>> iterator() {
      return Collections.<Entry<String, AttributeValue>>singleton(
              new SimpleImmutableEntry<>(key, value))
          .iterator();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SingleAttributes)) {
        return false;
      }

      SingleAttributes that = (SingleAttributes) o;

      if (key != null ? !key.equals(that.key) : that.key != null) {
        return false;
      }
      return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
      int result = key != null ? key.hashCode() : 0;
      result = 31 * result + (value != null ? value.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "SingleAttributes{" + "key=" + key + ", value=" + value + '}';
    }
  }
}
