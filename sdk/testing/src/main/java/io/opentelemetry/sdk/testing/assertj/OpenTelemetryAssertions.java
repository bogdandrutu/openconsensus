/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.assertj;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;

/**
 * Entry point for assertion methods for OpenTelemetry types. To use type-specific assertions,
 * static import any {@code assertThat} method in this class instead of {@code
 * Assertions.assertThat}.
 */
public final class OpenTelemetryAssertions extends Assertions {

  /** Returns an assertion for {@link Attributes}. */
  public static AttributesAssert assertThat(Attributes attributes) {
    return new AttributesAssert(attributes);
  }

  /** Returns an assertion for {@link SpanData}. */
  public static SpanDataAssert assertThat(SpanData spanData) {
    return new SpanDataAssert(spanData);
  }

  /** Returns an assertion for {@link EventData}. */
  public static EventDataAssert assertThat(EventData eventData) {
    return new EventDataAssert(eventData);
  }

  /**
   * Returns an attribute entry with a String value for use with {@link
   * AttributesAssert#containsOnly(java.util.Map.Entry[])}.
   */
  public static Map.Entry<AttributeKey<String>, String> attributeEntry(String key, String value) {
    return new AbstractMap.SimpleImmutableEntry<>(AttributeKey.stringKey(key), value);
  }

  /**
   * Returns an attribute entry with a boolean value for use with {@link
   * AttributesAssert#containsOnly(java.util.Map.Entry[])}.
   */
  public static Map.Entry<AttributeKey<Boolean>, Boolean> attributeEntry(
      String key, boolean value) {
    return new AbstractMap.SimpleImmutableEntry<>(AttributeKey.booleanKey(key), value);
  }

  /**
   * Returns an attribute entry with a long value for use with {@link
   * AttributesAssert#containsOnly(java.util.Map.Entry[])}.
   */
  public static Map.Entry<AttributeKey<Long>, Long> attributeEntry(String key, long value) {
    return new AbstractMap.SimpleImmutableEntry<>(AttributeKey.longKey(key), value);
  }

  /**
   * Returns an attribute entry with a double value for use with {@link
   * AttributesAssert#containsOnly(java.util.Map.Entry[])}.
   */
  public static Map.Entry<AttributeKey<Double>, Double> attributeEntry(String key, double value) {
    return new AbstractMap.SimpleImmutableEntry<>(AttributeKey.doubleKey(key), value);
  }

  /**
   * Returns an attribute entry with a String array value for use with {@link
   * AttributesAssert#containsOnly(java.util.Map.Entry[])}.
   */
  public static Map.Entry<AttributeKey<List<String>>, List<String>> attributeEntry(
      String key, String... value) {
    return new AbstractMap.SimpleImmutableEntry<>(
        AttributeKey.stringArrayKey(key), Arrays.asList(value));
  }

  /**
   * Returns an attribute entry with a boolean array value for use with {@link
   * AttributesAssert#containsOnly(java.util.Map.Entry[])}.
   */
  public static Map.Entry<AttributeKey<List<Boolean>>, List<Boolean>> attributeEntry(
      String key, boolean... value) {
    return new AbstractMap.SimpleImmutableEntry<>(AttributeKey.booleanArrayKey(key), toList(value));
  }

  /**
   * Returns an attribute entry with a long array value for use with {@link
   * AttributesAssert#containsOnly(java.util.Map.Entry[])}.
   */
  public static Map.Entry<AttributeKey<List<Long>>, List<Long>> attributeEntry(
      String key, long... value) {
    return new AbstractMap.SimpleImmutableEntry<>(
        AttributeKey.longArrayKey(key), Arrays.stream(value).boxed().collect(Collectors.toList()));
  }

  /**
   * Returns an attribute entry with a double array value for use with {@link
   * AttributesAssert#containsOnly(java.util.Map.Entry[])}.
   */
  public static Map.Entry<AttributeKey<List<Double>>, List<Double>> attributeEntry(
      String key, double... value) {
    return new AbstractMap.SimpleImmutableEntry<>(
        AttributeKey.doubleArrayKey(key),
        Arrays.stream(value).boxed().collect(Collectors.toList()));
  }

  private static List<Boolean> toList(boolean... values) {
    Boolean[] boxed = new Boolean[values.length];
    for (int i = 0; i < values.length; i++) {
      boxed[i] = values[i];
    }
    return Arrays.asList(boxed);
  }

  private OpenTelemetryAssertions() {}
}
