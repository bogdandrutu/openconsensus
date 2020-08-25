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

import com.google.auto.value.AutoValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.concurrent.Immutable;

@Immutable
@AutoValue
abstract class AttributeValueStringArray extends BaseAttributeValue {

  private static final AttributeValue EMPTY =
      new AutoValue_AttributeValueStringArray(Collections.<String>emptyList());

  AttributeValueStringArray() {}

  static AttributeValue create(String... stringValues) {
    if (stringValues == null) {
      return EMPTY;
    }
    return new AutoValue_AttributeValueStringArray(
        Collections.unmodifiableList(Arrays.asList(stringValues)));
  }

  @Override
  public final Type getType() {
    return Type.STRING_ARRAY;
  }

  @Override
  public boolean isNull() {
    return this == EMPTY;
  }

  @Override
  public abstract List<String> getStringArrayValue();
}
