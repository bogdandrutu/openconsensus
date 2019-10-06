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

package io.opentelemetry.trace;

import com.google.auto.value.AutoValue;
import io.opentelemetry.common.Timestamp;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A class that enables overriding the default values used when ending a {@link Span}. Allows
 * overriding the {@link Timestamp endTimestamp}.
 *
 * @since 0.1
 */
@Immutable
@AutoValue
public abstract class EndSpanOptions {
  private static final EndSpanOptions DEFAULT = builder().build();

  /**
   * The default {@code EndSpanOptions}.
   *
   * @since 0.1
   */
  static EndSpanOptions getDefault() {
    return DEFAULT;
  }

  /**
   * Returns a new {@link Builder} with default options.
   *
   * @return a new {@code Builder} with default options.
   * @since 0.1
   */
  public static Builder builder() {
    return new AutoValue_EndSpanOptions.Builder();
  }

  /**
   * Returns the end {@link Timestamp}.
   *
   * @return the end timestamp.
   * @since 0.1
   */
  @Nullable
  public abstract Timestamp getEndTimestamp();

  /**
   * Builder class for {@link EndSpanOptions}.
   *
   * @since 0.1
   */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the end {@link Timestamp} for the {@link Span}.
     *
     * @param endTimestamp the end {@link Timestamp}.
     * @return this.
     * @since 0.1
     */
    public abstract Builder setEndTimestamp(@Nullable Timestamp endTimestamp);

    /**
     * Builds and returns a {@code EndSpanOptions} with the desired settings.
     *
     * @return a {@code EndSpanOptions} with the desired settings.
     * @since 0.1
     */
    public abstract EndSpanOptions build();

    Builder() {}
  }

  EndSpanOptions() {}
}
