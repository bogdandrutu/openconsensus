/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.context.propagation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeTextMapPropagatorTest {

  private static final ContextKey<String> KEY = ContextKey.named("key");

  @Mock private TextMapPropagator propagator1;
  @Mock private TextMapPropagator propagator2;
  @Mock private TextMapPropagator propagator3;

  private static final TextMapGetter<Map<String, String>> getter =
      new TextMapGetter<Map<String, String>>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Nullable
        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier.get(key);
        }
      };

  @Test
  void new_composite_with_null_list() {
    assertThrows(
        NullPointerException.class,
        () -> new CompositeTextMapPropagator((List<TextMapPropagator>) null));
  }

  @Test
  void fields() {
    when(propagator1.fields()).thenReturn(Arrays.asList("foo", "bar"));
    when(propagator2.fields()).thenReturn(Arrays.asList("hello", "world"));
    TextMapPropagator prop = new CompositeTextMapPropagator(propagator1, propagator2);

    Collection<String> fields = prop.fields();
    assertThat(fields).containsExactly("foo", "bar", "hello", "world");
  }

  @Test
  void fields_duplicates() {
    when(propagator1.fields()).thenReturn(Arrays.asList("foo", "bar", "foo"));
    when(propagator2.fields()).thenReturn(Arrays.asList("hello", "world", "world", "bar"));
    TextMapPropagator prop = new CompositeTextMapPropagator(propagator1, propagator2);

    Collection<String> fields = prop.fields();
    assertThat(fields).containsExactly("foo", "bar", "hello", "world");
  }

  @Test
  void fields_readOnly() {
    when(propagator1.fields()).thenReturn(Arrays.asList("rubber", "baby"));
    when(propagator2.fields()).thenReturn(Arrays.asList("buggy", "bumpers"));
    TextMapPropagator prop = new CompositeTextMapPropagator(propagator1, propagator2);
    Collection<String> fields = prop.fields();
    assertThrows(UnsupportedOperationException.class, () -> fields.add("hi"));
  }

  @Test
  void inject_allDelegated() {
    Map<String, String> carrier = new HashMap<>();
    Context context = mock(Context.class);
    TextMapSetter<Map<String, String>> setter = Map::put;

    TextMapPropagator prop = new CompositeTextMapPropagator(propagator1, propagator2, propagator3);
    prop.inject(context, carrier, setter);
    verify(propagator1).inject(context, carrier, setter);
    verify(propagator2).inject(context, carrier, setter);
    verify(propagator3).inject(context, carrier, setter);
  }

  @Test
  void extract_noPropagators() {
    Map<String, String> carrier = new HashMap<>();
    Context context = mock(Context.class);

    TextMapPropagator prop = new CompositeTextMapPropagator();
    Context resContext = prop.extract(context, carrier, getter);
    assertThat(context).isSameAs(resContext);
  }

  @Test
  void extract_found_all() {
    Map<String, String> carrier = new HashMap<>();
    TextMapPropagator prop = new CompositeTextMapPropagator(propagator1, propagator2, propagator3);
    Context context1 = mock(Context.class);
    Context context2 = mock(Context.class);
    Context context3 = mock(Context.class);
    Context expectedContext = mock(Context.class);

    when(propagator1.extract(context1, carrier, getter)).thenReturn(context2);
    when(propagator2.extract(context2, carrier, getter)).thenReturn(context3);
    when(propagator3.extract(context3, carrier, getter)).thenReturn(expectedContext);

    assertThat(prop.extract(context1, carrier, getter)).isEqualTo(expectedContext);
  }

  @Test
  void extract_notFound() {
    Map<String, String> carrier = new HashMap<>();
    Context context = mock(Context.class);
    when(propagator1.extract(context, carrier, getter)).thenReturn(context);
    when(propagator2.extract(context, carrier, getter)).thenReturn(context);

    TextMapPropagator prop = new CompositeTextMapPropagator(propagator1, propagator2);
    Context result = prop.extract(context, carrier, getter);

    assertThat(result).isSameAs(context);
  }

  @Test
  void extract_nullContext() {
    assertThat(
            new CompositeTextMapPropagator(propagator1, propagator2)
                .extract(null, Collections.emptyMap(), getter))
        .isSameAs(Context.root());
  }

  @Test
  void extract_nullGetter() {
    Context context = Context.current().with(KEY, "treasure");
    assertThat(
            new CompositeTextMapPropagator(propagator1, propagator2)
                .extract(context, Collections.emptyMap(), null))
        .isSameAs(context);
  }

  @Test
  void inject_nullContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    new CompositeTextMapPropagator(propagator1, propagator2).inject(null, carrier, Map::put);
    assertThat(carrier).isEmpty();
  }

  @Test
  void inject_nullSetter() {
    Map<String, String> carrier = new LinkedHashMap<>();
    Context context = Context.current().with(KEY, "treasure");
    new CompositeTextMapPropagator(propagator1, propagator2).inject(context, carrier, null);
    assertThat(carrier).isEmpty();
  }

  @Test
  void different_inject_and_extract() {
    Map<String, String> carrier = new HashMap<>();

    TextMapPropagator prop =
        TextMapPropagator.builder()
            .addInjector(propagator1)
            .addExtractor(propagator2)
            .addPropagator(propagator3)
            .build();

    Context context1 = mock(Context.class);
    Context context2 = mock(Context.class);
    Context expectedContext = mock(Context.class);

    when(propagator2.extract(context1, carrier, getter)).thenReturn(context2);
    when(propagator3.extract(context2, carrier, getter)).thenReturn(expectedContext);

    assertThat(prop.extract(context1, carrier, getter)).isEqualTo(expectedContext);

    Context context = mock(Context.class);
    TextMapSetter<Map<String, String>> setter = Map::put;

    prop.inject(context, carrier, setter);
    verify(propagator1).inject(context, carrier, setter);
    verify(propagator2, never()).inject(context, carrier, setter);
    verify(propagator3).inject(context, carrier, setter);
  }

  @Test
  void addPropagator_null() {
    assertThrows(NullPointerException.class, () -> TextMapPropagator.builder().addPropagator(null));
  }

  @Test
  void addExtractor_null() {
    assertThrows(NullPointerException.class, () -> TextMapPropagator.builder().addExtractor(null));
  }

  @Test
  void addInjector_null() {
    assertThrows(NullPointerException.class, () -> TextMapPropagator.builder().addInjector(null));
  }

  @Test
  void empty_builder_returns_noop() {
    assertThat(TextMapPropagator.builder().build()).isSameAs(NoopTextMapPropagator.getInstance());
  }
}
