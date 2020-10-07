/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.baggage.propagation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.grpc.Context;
import io.opentelemetry.baggage.Baggage;
import io.opentelemetry.baggage.Baggage.Builder;
import io.opentelemetry.baggage.BaggageManager;
import io.opentelemetry.baggage.BaggageUtils;
import io.opentelemetry.baggage.EmptyBaggage;
import io.opentelemetry.baggage.Entry;
import io.opentelemetry.baggage.EntryMetadata;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class W3CBaggagePropagatorTest {

  @Mock
  private BaggageManager baggageManager;

  @Test
  void fields() {
    assertThat(W3CBaggagePropagator.getInstance().fields()).containsExactly("baggage");
  }

  @Test
  void extract_noBaggageHeader() {
    W3CBaggagePropagator propagator = new W3CBaggagePropagator(baggageManager);

    Context result = propagator
        .extract(Context.ROOT, ImmutableMap.<String, String>of(), ImmutableMap::get);

    assertThat(result).isEqualTo(Context.ROOT);
  }

  @Test
  void extract_emptyBaggageHeader() {
    W3CBaggagePropagator propagator = new W3CBaggagePropagator(baggageManager);

    Context result = propagator
        .extract(Context.ROOT, ImmutableMap.of("baggage", ""), ImmutableMap::get);

    assertThat(BaggageUtils.getBaggage(result))
        .isEqualTo(EmptyBaggage.getInstance());
  }

  @Test
  void extract_singleEntry() {
    when(baggageManager.baggageBuilder()).thenReturn(new TestBaggageBuilder());

    W3CBaggagePropagator propagator = new W3CBaggagePropagator(baggageManager);

    Context result = propagator
        .extract(Context.ROOT, ImmutableMap.of("baggage", "key=value"), ImmutableMap::get);

    Baggage expectedBaggage = new TestBaggage(ImmutableMap.of("key", Entry.create("key", "value")));
    assertThat(BaggageUtils.getBaggage(result)).isEqualTo(expectedBaggage);
  }

  @Test
  void extract_multiEntry() {
    when(baggageManager.baggageBuilder()).thenReturn(new TestBaggageBuilder());

    W3CBaggagePropagator propagator = new W3CBaggagePropagator(baggageManager);

    Context result = propagator
        .extract(Context.ROOT, ImmutableMap.of("baggage", "key1=value1,key2=value2"),
            ImmutableMap::get);

    Baggage expectedBaggage = new TestBaggage(
        ImmutableMap
            .of("key1", Entry.create("key1", "value1"), "key2", Entry.create("key2", "value2")));
    assertThat(BaggageUtils.getBaggage(result)).isEqualTo(expectedBaggage);
  }

  @Test
  void extract_duplicateKeys() {
    when(baggageManager.baggageBuilder()).thenReturn(new TestBaggageBuilder());

    W3CBaggagePropagator propagator = new W3CBaggagePropagator(baggageManager);

    Context result = propagator
        .extract(Context.ROOT, ImmutableMap.of("baggage", "key=value1,key=value2"),
            ImmutableMap::get);

    Baggage expectedBaggage = new TestBaggage(
        ImmutableMap.of("key", Entry.create("key", "value2")));
    assertThat(BaggageUtils.getBaggage(result)).isEqualTo(expectedBaggage);
  }

  @Test
  void extract_withMetadata() {
    when(baggageManager.baggageBuilder()).thenReturn(new TestBaggageBuilder());

    W3CBaggagePropagator propagator = new W3CBaggagePropagator(baggageManager);

    Context result = propagator
        .extract(Context.ROOT,
            ImmutableMap.of("baggage", "key=value;metadata-key=value;othermetadata"),
            ImmutableMap::get);

    Baggage expectedBaggage = new TestBaggage(
        ImmutableMap.of("key", Entry
            .create("key", "value", EntryMetadata.create("metadata-key=value;othermetadata"))));
    assertThat(BaggageUtils.getBaggage(result)).isEqualTo(expectedBaggage);
  }

  @Test
  void extract_fullComplexities() {
    when(baggageManager.baggageBuilder()).thenReturn(new TestBaggageBuilder());

    W3CBaggagePropagator propagator = new W3CBaggagePropagator(baggageManager);

    Context result = propagator
        .extract(Context.ROOT,
            ImmutableMap.of("baggage",
                "key1= value1; metadata-key = value; othermetadata, key2 =value2 , key3 =\tvalue3 ; "),
            ImmutableMap::get);

    Baggage expectedBaggage = new TestBaggage(
        ImmutableMap.of(
            "key1", Entry.create("key1", "value1",
                EntryMetadata.create("metadata-key = value; othermetadata")),
            "key2", Entry.create("key2", "value2", EntryMetadata.EMPTY),
            "key3", Entry.create("key3", "value3")));
    assertThat(BaggageUtils.getBaggage(result)).isEqualTo(expectedBaggage);
  }

  /**
   * It would be cool if we could replace this with a fuzzer to generate tons of crud data, to make sure we don't blow up with it.
   */
  @Test
  void extract_invalidHeader() {
    when(baggageManager.baggageBuilder()).thenReturn(new TestBaggageBuilder());

    W3CBaggagePropagator propagator = new W3CBaggagePropagator(baggageManager);

    Context result = propagator
        .extract(Context.ROOT,
            ImmutableMap.of("baggage",
                "key1= v;alsdf;-asdflkjasdf===asdlfkjadsf ,,a sdf9asdf-alue1; metadata-key = value; othermetadata, key2 =value2 , key3 =\tvalue3 ; "),
            ImmutableMap::get);

    assertThat(BaggageUtils.getBaggage(result)).isEqualTo(EmptyBaggage.getInstance());
  }

  private static class TestBaggageBuilder implements Builder {

    private final Map<String, Entry> values = new HashMap<>();

    @Override
    public Builder setParent(Context context) {
      return this;
    }

    @Override
    public Builder setNoParent() {
      return this;
    }

    @Override
    public Builder put(String key, String value, EntryMetadata entryMetadata) {
      values.put(key, Entry.create(key, value, entryMetadata));
      return this;
    }

    @Override
    public Builder remove(String key) {
      values.remove(key);
      return this;
    }

    @Override
    public Baggage build() {
      return new TestBaggage(values);
    }

  }

  private static class TestBaggage implements Baggage {

    private final Map<String, Entry> values;

    public TestBaggage(Map<String, Entry> values) {
      this.values = values;
    }

    @Override
    public Collection<Entry> getEntries() {
      return values.values();
    }

    @Override
    public String getEntryValue(String entryKey) {
      Entry entry = values.get(entryKey);
      return entry == null ? null : entry.getValue();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      TestBaggage that = (TestBaggage) o;

      return values != null ? values.equals(that.values) : that.values == null;
    }

    @Override
    public int hashCode() {
      return values != null ? values.hashCode() : 0;
    }

    @Override
    public String toString() {
      return "TestBaggage{" +
          "values=" + values +
          '}';
    }
  }
}