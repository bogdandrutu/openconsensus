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

package io.opentelemetry.sdk.tags;

import static com.google.common.truth.Truth.assertThat;
import static io.opentelemetry.sdk.tags.TagMapTestUtil.tagMapToList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.testing.EqualsTester;
import io.opentelemetry.tags.Tag;
import io.opentelemetry.tags.TagKey;
import io.opentelemetry.tags.TagMap;
import io.opentelemetry.tags.TagMetadata;
import io.opentelemetry.tags.TagValue;
import io.opentelemetry.tags.Tagger;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TagMapSdk} and {@link TagMapSdk.Builder}.
 *
 * <p>Tests for {@link TagMapSdk.Builder#buildScoped()} are in {@link ScopedTagMapTest}.
 */
@RunWith(JUnit4.class)
public class TagMapSdkTest {
  private final Tagger tagger = new TaggerSdk();

  private static final TagMetadata TMD =
      TagMetadata.create(TagMetadata.TagTtl.UNLIMITED_PROPAGATION);

  private static final TagKey K1 = TagKey.create("k1");
  private static final TagKey K2 = TagKey.create("k2");

  private static final TagValue V1 = TagValue.create("v1");
  private static final TagValue V2 = TagValue.create("v2");

  private static final Tag T1 = Tag.create(K1, V1, TMD);
  private static final Tag T2 = Tag.create(K2, V2, TMD);

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void getIterator_empty() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.<TagKey, Tag>of()).build();
    assertThat(tagMapToList(tags)).isEmpty();
  }

  @Test
  public void getIterator_nonEmpty() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1, K2, T2)).build();
    assertThat(tagMapToList(tags)).containsExactly(T1, T2);
  }

  @Test
  public void put_newKey() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1)).build();
    assertThat(tagMapToList(tagger.toBuilder(tags).put(K2, V2, TMD).build()))
        .containsExactly(T1, T2);
  }

  @Test
  public void put_existingKey() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1)).build();
    assertThat(tagMapToList(tagger.toBuilder(tags).put(K1, V2, TMD).build()))
        .containsExactly(Tag.create(K1, V2, TMD));
  }

  @Test
  public void put_nullKey() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1)).build();
    TagMap.Builder builder = tagger.toBuilder(tags);
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("key");
    builder.put(null, V2, TMD);
  }

  @Test
  public void put_nullValue() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1)).build();
    TagMap.Builder builder = tagger.toBuilder(tags);
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("value");
    builder.put(K2, null, TMD);
  }

  @Test
  public void remove_existingKey() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1, K2, T2)).build();
    assertThat(tagMapToList(tagger.toBuilder(tags).remove(K1).build())).containsExactly(T2);
  }

  @Test
  public void remove_differentKey() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1, K2, T2)).build();
    assertThat(tagMapToList(tagger.toBuilder(tags).remove(K2).build())).containsExactly(T1);
  }

  @Test
  public void remove_nullKey() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1)).build();
    TagMap.Builder builder = tagger.toBuilder(tags);
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("key");
    builder.remove(null);
  }

  @Test
  public void testIterator() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1, K2, T2)).build();
    Iterator<Tag> i = tags.getIterator();
    assertTrue(i.hasNext());
    Tag tag1 = i.next();
    assertTrue(i.hasNext());
    Tag tag2 = i.next();
    assertFalse(i.hasNext());
    assertThat(Arrays.asList(tag1, tag2))
        .containsExactly(Tag.create(K1, V1, TMD), Tag.create(K2, V2, TMD));
    thrown.expect(NoSuchElementException.class);
    i.next();
  }

  @Test
  public void disallowCallingRemoveOnIterator() {
    TagMapSdk tags = new TagMapSdk.Builder(ImmutableMap.of(K1, T1, K2, T2)).build();
    Iterator<Tag> i = tags.getIterator();
    i.next();
    thrown.expect(UnsupportedOperationException.class);
    i.remove();
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(
            tagger.emptyBuilder().put(K1, V1, TMD).put(K2, V2, TMD).build(),
            tagger.emptyBuilder().put(K1, V1, TMD).put(K2, V2, TMD).build(),
            tagger.emptyBuilder().put(K2, V2, TMD).put(K1, V1, TMD).build())
        .addEqualityGroup(tagger.emptyBuilder().put(K1, V1, TMD).put(K2, V1, TMD).build())
        .addEqualityGroup(tagger.emptyBuilder().put(K1, V2, TMD).put(K2, V1, TMD).build())
        .testEquals();
  }
}
