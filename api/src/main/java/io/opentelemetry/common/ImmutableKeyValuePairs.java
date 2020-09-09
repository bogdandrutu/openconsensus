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

import static io.opentelemetry.internal.Utils.checkArgument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable set of key-value pairs. Keys are only {@link String} typed.
 *
 * <p>Key-value pairs are dropped for {@code null} or empty keys.
 *
 * @param <V> The type of the values contained in this.
 * @see Labels
 * @see Attributes
 */
@SuppressWarnings("rawtypes")
@Immutable
abstract class ImmutableKeyValuePairs<K, V> {

  List<Object> data() {
    return Collections.emptyList();
  }

  public int size() {
    return data().size() / 2;
  }

  public boolean isEmpty() {
    return data().isEmpty();
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public V get(K key) {
    for (int i = 0; i < data().size(); i += 2) {
      if (key.equals(data().get(i))) {
        return (V) data().get(i + 1);
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  static List<Object> sortAndFilter(Object[] data) {
    checkArgument(
        data.length % 2 == 0, "You must provide an even number of key/value pair arguments.");

    quickSort(data, 0, data.length - 2);
    return dedupe(data);
  }

  @SuppressWarnings("unchecked")
  private static <K extends Comparable<K>> void quickSort(
      Object[] data, int leftIndex, int rightIndex) {
    if (leftIndex >= rightIndex) {
      return;
    }

    K pivotKey = (data[rightIndex] == null) ? null : (K) data[rightIndex];
    int counter = leftIndex;

    for (int i = leftIndex; i <= rightIndex; i += 2) {
      K value = data[i] == null ? null : (K) data[i];

      if (compareTo(value, pivotKey) <= 0) {
        swap(data, counter, i);
        counter += 2;
      }
    }

    quickSort(data, leftIndex, counter - 4);
    quickSort(data, counter, rightIndex);
  }

  private static <K extends Comparable<K>> int compareTo(K value, K pivotKey) {
    if (value == null) {
      return pivotKey == null ? 0 : -1;
    }
    if (pivotKey == null) {
      return 1;
    }
    return value.compareTo(pivotKey);
  }

  private static List<Object> dedupe(Object[] data) {
    List<Object> result = new ArrayList<>(data.length);
    Object previousKey = null;

    for (int i = 0; i < data.length; i += 2) {
      Object key = data[i];
      Object value = data[i + 1];
      if (key == null) {
        continue;
      }
      // TODO: this is super ugly. It needs to be fixed.
      // Obviously, this isn't truly generic if we have to peek into the key types for these empty
      // checks.
      if (key instanceof String) {
        if ("".equals(key)) {
          continue;
        }
      } else if (key instanceof AttributeKey) {
        if ("".equals(((AttributeKey) key).get())) {
          continue;
        }
      }
      if (key.equals(previousKey)) {
        continue;
      }
      previousKey = key;
      result.add(key);
      result.add(value);
    }
    return result;
  }

  private static void swap(Object[] data, int a, int b) {
    Object keyA = data[a];
    Object valueA = data[a + 1];
    data[a] = data[b];
    data[a + 1] = data[b + 1];

    data[b] = keyA;
    data[b + 1] = valueA;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("{");
    List<Object> data = data();
    for (int i = 0; i < data.size(); i += 2) {
      sb.append(data.get(i)).append("=").append(data.get(i + 1)).append(", ");
    }
    // get rid of that last pesky comma
    if (sb.length() > 1) {
      sb.setLength(sb.length() - 2);
    }
    sb.append("}");
    return sb.toString();
  }
}
