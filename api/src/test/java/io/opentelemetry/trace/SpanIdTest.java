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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link SpanId}. */
class SpanIdTest {
  private static final byte[] firstBytes = new byte[] {0, 0, 0, 0, 0, 0, 0, 'a'};
  private static final byte[] secondBytes = new byte[] {(byte) 0xFF, 0, 0, 0, 0, 0, 0, 'A'};

  @Test
  void isValid() {
    assertThat(SpanId.isValid(SpanId.getInvalid())).isFalse();
    assertThat(SpanId.isValid(SpanId.bytesToHex(firstBytes))).isTrue();
    assertThat(SpanId.isValid(SpanId.bytesToHex(secondBytes))).isTrue();
    assertThat(SpanId.isValid("000000000000z000")).isFalse();
  }

  @Test
  void fromLowerBase16() {
    assertThat(SpanId.bytesToHex(SpanId.bytesFromHex("0000000000000000", 0)))
        .isEqualTo(SpanId.getInvalid());
    assertThat(SpanId.bytesFromHex("0000000000000061", 0)).isEqualTo(firstBytes);
    assertThat(SpanId.bytesFromHex("ff00000000000041", 0)).isEqualTo(secondBytes);
  }

  @Test
  void fromLowerBase16_WithOffset() {
    assertThat(SpanId.bytesToHex(SpanId.bytesFromHex("XX0000000000000000AA", 2)))
        .isEqualTo(SpanId.getInvalid());
    assertThat(SpanId.bytesFromHex("YY0000000000000061BB", 2)).isEqualTo(firstBytes);
    assertThat(SpanId.bytesFromHex("ZZff00000000000041CC", 2)).isEqualTo(secondBytes);
  }

  @Test
  public void toLowerBase16() {
    assertThat(SpanId.getInvalid()).isEqualTo("0000000000000000");
    assertThat(SpanId.bytesToHex(firstBytes)).isEqualTo("0000000000000061");
    assertThat(SpanId.bytesToHex(secondBytes)).isEqualTo("ff00000000000041");
  }

  @Test
  void spanId_ToString() {
    assertThat(SpanId.getInvalid()).contains("0000000000000000");
    assertThat(SpanId.bytesToHex(firstBytes)).contains("0000000000000061");
    assertThat(SpanId.bytesToHex(secondBytes)).contains("ff00000000000041");
  }
}
