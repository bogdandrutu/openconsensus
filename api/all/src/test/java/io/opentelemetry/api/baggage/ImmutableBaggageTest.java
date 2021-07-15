/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.baggage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.google.common.testing.EqualsTester;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Baggage} and {@link BaggageBuilder}.
 *
 * <p>Tests for scope management with {@link Baggage} are in {@link ScopedBaggageTest}.
 */
class ImmutableBaggageTest {

  private static final BaggageEntryMetadata TMD = BaggageEntryMetadata.create("tmd");

  private static final String K1 = "k1";
  private static final String K2 = "k2";

  private static final String V1 = "v1";
  private static final String V2 = "v2";

  private static final Baggage ONE_ENTRY = Baggage.builder().put(K1, V1, TMD).build();
  private static final Baggage TWO_ENTRIES = ONE_ENTRY.toBuilder().put(K2, V2, TMD).build();

  @Test
  void getEntryValue() {
    assertThat(ONE_ENTRY.getEntryValue(K1)).isEqualTo(V1);
  }

  @Test
  void getEntryValue_nullKey() {
    assertThat(ONE_ENTRY.getEntryValue(null)).isNull();
  }

  @Test
  void getEntries_empty() {
    Baggage baggage = Baggage.empty();
    assertThat(baggage.size()).isZero();
    assertThat(baggage.isEmpty()).isTrue();
  }

  @Test
  void getEntries_nonEmpty() {
    Baggage baggage = TWO_ENTRIES;
    assertThat(baggage.asMap())
        .containsOnly(
            entry(K1, ImmutableEntry.create(V1, TMD)), entry(K2, ImmutableEntry.create(V2, TMD)));
    assertThat(baggage.size()).isEqualTo(2);
    assertThat(baggage.isEmpty()).isFalse();
  }

  @Test
  void getEntries_chain() {
    Baggage baggage = TWO_ENTRIES.toBuilder().put(K1, V2, TMD).build();
    assertThat(baggage.asMap())
        .containsOnly(
            entry(K1, ImmutableEntry.create(V2, TMD)), entry(K2, ImmutableEntry.create(V2, TMD)));
  }

  @Test
  void put_newKey() {
    assertThat(ONE_ENTRY.toBuilder().put(K2, V2, TMD).build().asMap())
        .containsOnly(
            entry(K1, ImmutableEntry.create(V1, TMD)), entry(K2, ImmutableEntry.create(V2, TMD)));
  }

  @Test
  void put_existingKey() {
    assertThat(ONE_ENTRY.toBuilder().put(K1, V2, TMD).build().asMap())
        .containsOnly(entry(K1, ImmutableEntry.create(V2, TMD)));
  }

  @Test
  void put_nullKey() {
    BaggageBuilder builder = ONE_ENTRY.toBuilder();
    Baggage built = builder.build();
    builder.put(null, V2, TMD);
    assertThat(builder.build()).isEqualTo(built);
  }

  @Test
  void put_nullValue() {
    BaggageBuilder builder = ONE_ENTRY.toBuilder();
    Baggage built = builder.build();
    builder.put(K2, null, TMD);
    assertThat(builder.build()).isEqualTo(built);
  }

  @Test
  void put_nonZeroLengthEmptyKey() {
    BaggageBuilder builder = ONE_ENTRY.toBuilder();
    Baggage built = builder.build();
    builder.put(" ", V2, TMD);
    assertThat(builder.build()).isEqualTo(built);
  }

  @Test
  void put_nullMetadata() {
    BaggageBuilder builder = ONE_ENTRY.toBuilder();
    Baggage built = builder.build();
    builder.put(K2, V2, null);
    assertThat(builder.build()).isEqualTo(built);
  }

  @Test
  void put_keyUnprintableChars() {
    BaggageBuilder builder = ONE_ENTRY.toBuilder();
    Baggage built = builder.build();
    builder.put("\2ab\3cd", "value");
    assertThat(builder.build()).isEqualTo(built);
  }

  @Test
  void put_keyEmpty() {
    BaggageBuilder builder = ONE_ENTRY.toBuilder();
    Baggage built = builder.build();
    builder.put("", "value");
    assertThat(builder.build()).isEqualTo(built);
  }

  @Test
  void put_valueUnprintableChars() {
    BaggageBuilder builder = ONE_ENTRY.toBuilder();
    Baggage built = builder.build();
    builder.put(K2, "\2ab\3cd");
    assertThat(builder.build()).isEqualTo(built);
  }

  @Test
  void remove_existingKey() {
    BaggageBuilder builder = Baggage.builder();
    builder.put(K1, V1, TMD);
    builder.put(K2, V2, TMD);

    assertThat(builder.remove(K1).build().asMap())
        .containsOnly(entry(K2, ImmutableEntry.create(V2, TMD)));
  }

  @Test
  void remove_differentKey() {
    BaggageBuilder builder = Baggage.builder();
    builder.put(K1, V1, TMD);
    builder.put(K2, V2, TMD);

    assertThat(builder.remove(K2).build().asMap())
        .containsOnly(entry(K1, ImmutableEntry.create(V1, TMD)));
  }

  @Test
  void remove_keyFromParent() {
    assertThat(TWO_ENTRIES.toBuilder().remove(K1).build().asMap())
        .containsOnly(entry(K2, ImmutableEntry.create(V2, TMD)));
  }

  @Test
  void remove_nullKey() {
    BaggageBuilder builder = Baggage.builder();
    builder.put(K2, V2);
    Baggage built = builder.build();
    builder.remove(null);
    assertThat(builder.build()).isEqualTo(built);
  }

  @Test
  void toBuilder_keepsOriginalState() {
    assertThat(Baggage.empty().toBuilder().build()).isEqualTo(Baggage.empty());

    Baggage originalBaggage = Baggage.builder().put("key", "value").build();
    assertThat(originalBaggage.toBuilder().build()).isEqualTo(originalBaggage);
  }

  @Test
  void toBuilder_allowChanges() {
    Baggage singleItemNoParent = Baggage.builder().put("key1", "value1").build();
    Baggage singleItemWithParent = Baggage.builder().put("key1", "value1").build();

    assertThat(Baggage.empty().toBuilder().put("key1", "value1").build())
        .isEqualTo(singleItemNoParent);
    assertThat(singleItemNoParent.toBuilder().put("key2", "value2").build())
        .isEqualTo(Baggage.builder().put("key1", "value1").put("key2", "value2").build());
    assertThat(singleItemNoParent.toBuilder().put("key1", "value2").build())
        .isEqualTo(Baggage.builder().put("key1", "value2").build());

    assertThat(singleItemWithParent.toBuilder().put("key1", "value2").build())
        .isEqualTo(Baggage.builder().put("key1", "value2").build());
  }

  @Test
  void testEquals() {
    Baggage baggage1 = Baggage.builder().put(K1, V1).build();
    Baggage baggage2 = baggage1.toBuilder().put(K1, V2).build();
    Baggage baggage3 = Baggage.builder().put(K1, V2).build();
    new EqualsTester()
        .addEqualityGroup(
            Baggage.builder().put(K1, V1, TMD).put(K2, V2, TMD).build(),
            Baggage.builder().put(K1, V1, TMD).put(K2, V2, TMD).build(),
            Baggage.builder().put(K2, V2, TMD).put(K1, V1, TMD).build())
        .addEqualityGroup(Baggage.builder().put(K1, V1, TMD).put(K2, V1, TMD).build())
        .addEqualityGroup(Baggage.builder().put(K1, V2, TMD).put(K2, V1, TMD).build())
        .addEqualityGroup(baggage2, baggage3)
        .testEquals();
  }
}
