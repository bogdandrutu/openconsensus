/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class CollectionHandleTest {

  @Test
  public void created_haveUniqueIdentity() {
    CollectionHandle one = CollectionHandle.create();
    CollectionHandle two = CollectionHandle.create();

    assertThat(one).isEqualTo(one);
    assertThat(one).isNotEqualTo(two);
  }

  @Test
  public void mutableSet_allowsAddAndContains() {
    Set<CollectionHandle> mutable = CollectionHandle.mutableSet();
    CollectionHandle one = CollectionHandle.create();
    assertThat(mutable).hasSize(0);
    assertThat(mutable.contains(one)).isFalse();
    mutable.add(one);
    assertThat(mutable).hasSize(1);
    assertThat(mutable.contains(one)).isTrue();

    CollectionHandle two = CollectionHandle.create();
    assertThat(mutable.contains(two)).isFalse();
    mutable.add(two);
    assertThat(mutable).hasSize(2);
    assertThat(mutable.contains(two)).isTrue();
  }

  @Test
  public void mutableSet_allowsContainsAll() {
    CollectionHandle one = CollectionHandle.create();
    CollectionHandle two = CollectionHandle.create();
    CollectionHandle three = CollectionHandle.create();
    Set<CollectionHandle> mutable = CollectionHandle.mutableSet();
    mutable.add(one);
    mutable.add(two);
    Set<CollectionHandle> mutableCopy = CollectionHandle.of(one, two);
    Set<CollectionHandle> mutablePlus = CollectionHandle.of(one, two, three);

    assertThat(mutable.containsAll(mutableCopy)).isTrue();
    assertThat(mutable.containsAll(mutablePlus)).isFalse();
    assertThat(mutablePlus.containsAll(mutable)).isTrue();
  }

  @Test
  public void mutableSet_iteratingWOrks() {
    CollectionHandle one = CollectionHandle.create();
    CollectionHandle two = CollectionHandle.create();
    CollectionHandle three = CollectionHandle.create();
    Set<CollectionHandle> set = CollectionHandle.of(one, two, three);
    assertThat(set).hasSize(3);
    Iterator<CollectionHandle> iterator = set.iterator();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(one);
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(two);
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(three);
    assertThat(iterator.hasNext()).isFalse();
    // TODO: Verify next throws.
  }
}
