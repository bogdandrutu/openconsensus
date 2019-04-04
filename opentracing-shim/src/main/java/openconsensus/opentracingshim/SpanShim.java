/*
 * Copyright 2019, OpenConsensus Authors
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

package openconsensus.opentracingshim;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;
import java.util.HashMap;
import java.util.Map;
import openconsensus.trace.data.AttributeValue;

final class SpanShim implements Span {
  private final openconsensus.trace.Span span;
  private final SpanContextShim contextShim;

  public SpanShim(openconsensus.trace.Span span) {
    this.span = span;
    this.contextShim = new SpanContextShim(span.getContext());
  }

  openconsensus.trace.Span getSpan() {
    return span;
  }

  @Override
  public SpanContext context() {
    return contextShim;
  }

  @Override
  public Span setTag(String key, String value) {
    span.putAttribute(key, AttributeValue.stringAttributeValue(value));
    return this;
  }

  @Override
  public Span setTag(String key, boolean value) {
    span.putAttribute(key, AttributeValue.booleanAttributeValue(value));
    return this;
  }

  @Override
  public Span setTag(String key, Number value) {
    // TODO - Verify only the 'basic' types are supported/used.
    if (value instanceof Integer || value instanceof Long) {
      span.putAttribute(key, AttributeValue.longAttributeValue(value.longValue()));
    } else if (value instanceof Float || value instanceof Double) {
      span.putAttribute(key, AttributeValue.doubleAttributeValue(value.doubleValue()));
    } else {
      throw new IllegalArgumentException("Number type not supported");
    }

    return this;
  }

  @Override
  public <T> Span setTag(Tag<T> tag, T value) {
    tag.set(this, value);
    return this;
  }

  @Override
  public Span log(Map<String, ?> fields) {
    // TODO - verify 'null' for 'event' is valid.
    span.addEvent(null, convertToAttributes(fields));
    return this;
  }

  @Override
  public Span log(long timestampMicroseconds, Map<String, ?> fields) {
    // TODO - use timestampMicroseconds
    span.addEvent(null, convertToAttributes(fields));
    return this;
  }

  @Override
  public Span log(String event) {
    span.addEvent(event);
    return this;
  }

  @Override
  public Span log(long timestampMicroseconds, String event) {
    // TODO - use timestampMicroseconds
    span.addEvent(event);
    return this;
  }

  @Override
  public Span setBaggageItem(String key, String value) {
    // TODO
    return this;
  }

  @Override
  @SuppressWarnings("ReturnMissingNullable")
  public String getBaggageItem(String key) {
    // TODO
    return null;
  }

  @Override
  public Span setOperationName(String operationName) {
    // TODO
    return this;
  }

  @Override
  public void finish() {
    span.end();
  }

  @Override
  public void finish(long finishMicros) {
    // TODO: Take finishMicros into account
    span.end();
  }

  static Map<String, AttributeValue> convertToAttributes(Map<String, ?> fields) {
    Map<String, AttributeValue> attrMap = new HashMap<String, AttributeValue>();

    for (Map.Entry<String, ?> entry : fields.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      // TODO - verify null values are NOT allowed.
      if (value == null) {
        continue;
      }

      if (value instanceof Short || value instanceof Integer || value instanceof Long) {
        attrMap.put(key, AttributeValue.longAttributeValue(((Number) value).longValue()));
      } else if (value instanceof Float || value instanceof Double) {
        attrMap.put(key, AttributeValue.doubleAttributeValue(((Number) value).doubleValue()));
      } else if (value instanceof Boolean) {
        attrMap.put(key, AttributeValue.booleanAttributeValue((Boolean) value));
      } else {
        attrMap.put(key, AttributeValue.stringAttributeValue(value.toString()));
      }
    }

    return attrMap;
  }
}
