/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure.spi;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Properties used for auto-configuration of the OpenTelemetry SDK components. */
public interface ConfigProperties {

  /**
   * Returns a string-valued configuration property, or {@code null} if the property has not been
   * configured.
   */
  @Nullable
  String getString(String name);

  /**
   * Returns a integer-valued configuration property, or {@code null} if the property has not been
   * configured.
   */
  @Nullable
  Integer getInt(String name);

  /**
   * Returns a long-valued configuration property, or {@code null} if the property has not been
   * configured.
   */
  @Nullable
  Long getLong(String name);

  /**
   * Returns a double-valued configuration property, or {@code null} if the property has not been
   * configured.
   */
  @Nullable
  Double getDouble(String name);

  /**
   * Returns a list-valued configuration property. The format of the original value must be
   * comma-separated. Empty values will be removed. If the property has not been configured, an *
   * empty list is returned.
   */
  List<String> getCommaSeparatedValues(String name);

  /**
   * Returns a duration property from the map, or {@code null} if it cannot be found or it has a
   * wrong type.
   *
   * <p>Durations can be of the form "{number}{unit}", where unit is one of:
   *
   * <ul>
   *   <li>ms
   *   <li>s
   *   <li>m
   *   <li>h
   *   <li>d
   * </ul>
   *
   * <p>If no unit is specified, milliseconds is the assumed duration unit.
   */
  @Nullable
  @SuppressWarnings("UnusedException")
  Duration getDuration(String name);

  /**
   * Returns a map-valued configuration property. The format of the original value must be
   * comma-separated for each key, with an '=' separating the key and value. For instance, <code>
   * service.name=Greatest Service,host.name=localhost</code> Empty values will be removed. If the
   * property has not been configured, an empty map is returned.
   */
  Map<String, String> getCommaSeparatedMap(String name);

  /**
   * Returns a boolean-valued configuration property. Uses the same rules as {@link
   * Boolean#parseBoolean(String)} for handling the values. If the property has not been configured,
   * returns {@code false}.
   */
  boolean getBoolean(String name);
}
