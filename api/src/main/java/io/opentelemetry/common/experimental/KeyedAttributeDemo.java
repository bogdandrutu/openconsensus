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

package io.opentelemetry.common.experimental;

import static io.opentelemetry.common.experimental.KeyedAttributes.booleanKey;
import static io.opentelemetry.common.experimental.KeyedAttributes.compoundKey;
import static io.opentelemetry.common.experimental.KeyedAttributes.stringArrayKey;
import static io.opentelemetry.common.experimental.KeyedAttributes.stringKey;

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.experimental.KeyedAttributes.BooleanArrayKey;
import io.opentelemetry.common.experimental.KeyedAttributes.BooleanKey;
import io.opentelemetry.common.experimental.KeyedAttributes.CompoundKey;
import io.opentelemetry.common.experimental.KeyedAttributes.KeyImpl;
import io.opentelemetry.common.experimental.KeyedAttributes.MultiAttribute;
import io.opentelemetry.common.experimental.KeyedAttributes.StringArrayKey;
import io.opentelemetry.common.experimental.KeyedAttributes.StringKey;
import io.opentelemetry.common.experimental.ReadableKeyedAttributes.AttributeConsumer;
import io.opentelemetry.common.experimental.ReadableKeyedAttributes.RawAttributeConsumer;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

@SuppressWarnings("JavadocMethod")
public class KeyedAttributeDemo {
  private KeyedAttributeDemo() {}

  // here are some semantic attributes examples:
  static final StringKey HTTP_METHOD = stringKey("http.method");
  static final StringKey HTTP_SCHEME = stringKey("http.scheme");
  static final StringKey HTTP_HOST = stringKey("http.host");
  static final StringKey NET_HOST_NAME = stringKey("net.host.name");
  static final BooleanKey ERROR_HINT = booleanKey("error.hint");
  static final StringArrayKey HTTP_HEADERS = stringArrayKey("http.headers");
  static final CompoundKey REQUEST_URL = compoundKey("request.url");

  static final KeyedAttributes.Key<BigDecimal> MONEY_VALUE =
      new KeyImpl<BigDecimal>("money.value") {};

  public static void main(String[] args) {
    // here's how you build a full set of attributes. You can imagine the Span/Builder would look
    // similar
    KeyedAttributes keyedAttributes =
        KeyedAttributes.newBuilder()
            .set(HTTP_HEADERS, Arrays.asList("x-stuff", "x-api-key"))
            .set(ERROR_HINT, false)
            .set(NET_HOST_NAME, "localhost")
            .set(REQUEST_URL, new UrlAttributes("https://opentelemetry.io"))
            .setCustom(MONEY_VALUE, new BigDecimal(1_000_000L))
            .build();

    keyedAttributes.forEachRaw(
        new RawAttributeConsumer() {
          @Override
          public <T> void consume(KeyedAttributes.Key<T> key, AttributeValue.Type type, T value) {
            System.out.println(key.get() + " = " + value);
          }
        });

    // iterate over them like this, using the readable interface.
    keyedAttributes.forEach(
        new AttributeConsumer() {
          @Override
          public void consume(StringKey key, String value) {
            System.out.println(key + " = " + value);
          }

          @Override
          public void consume(StringArrayKey key, List<String> value) {
            System.out.println(key + " = " + value);
          }

          @Override
          public void consume(BooleanKey key, boolean value) {
            System.out.println(key + " = " + value);
          }

          @Override
          public void consume(BooleanArrayKey key, List<Boolean> value) {
            System.out.println(key + " = " + value);
          }

          @Override
          public void consume(CompoundKey key, MultiAttribute value) {
            System.out.println(key + " = " + value.getAttributes());
          }

          @Override
          public <T> void consumeCustom(KeyedAttributes.Key<T> key, T value) {
            System.out.println(key + " = " + value);
          }
        });
  }

  private static class UrlAttributes implements MultiAttribute {
    private final String url;
    @Nullable private volatile KeyedAttributes parsedAttributes = null;

    public UrlAttributes(String url) {
      this.url = url;
    }

    @Override
    public KeyedAttributes getAttributes() {
      if (parsedAttributes != null) {
        return parsedAttributes;
      }
      try {
        URL url = URI.create(this.url).toURL();
        KeyedAttributes attributes =
            KeyedAttributes.newBuilder()
                .set(HTTP_SCHEME, url.getProtocol())
                .set(HTTP_HOST, url.getHost())
                // etc
                .build();
        parsedAttributes = attributes;
        return attributes;
      } catch (MalformedURLException e) {
        return KeyedAttributes.empty();
      }
    }

    @Override
    public String toString() {
      return getAttributes().toString();
    }
  }
}
