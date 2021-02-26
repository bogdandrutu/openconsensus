/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link TraceFlags}. */
class TraceFlagsTest {

  @Test
  void defaultInstances() {
    assertThat(TraceFlags.getDefault().asHex()).isEqualTo("00");
    assertThat(TraceFlags.getSampled().asHex()).isEqualTo("01");
  }

  @Test
  void isSampled() {
    assertThat(TraceFlags.fromByte((byte) 0xff).isSampled()).isTrue();
    assertThat(TraceFlags.fromByte((byte) 0x01).isSampled()).isTrue();
    assertThat(TraceFlags.fromByte((byte) 0x05).isSampled()).isTrue();
    assertThat(TraceFlags.fromByte((byte) 0x00).isSampled()).isFalse();
  }

  @Test
  void toFromHex() {
    for (int i = 0; i < 256; i++) {
      String hex = Integer.toHexString(i);
      if (hex.length() == 1) {
        hex = "0" + hex;
      }
      assertThat(TraceFlags.fromHex(hex, 0).asHex()).isEqualTo(hex);
    }
  }

  @Test
  void toFromHex_Invalid() {
    assertThat(TraceFlags.fromHex(null, 0).isValid()).isFalse();
    assertThat(TraceFlags.fromHex("hex", 0).isValid()).isFalse();
    assertThat(TraceFlags.fromHex("aa", 1).isValid()).isFalse();
  }

  @Test
  void toFromByte() {
    for (int i = 0; i < 256; i++) {
      assertThat(TraceFlags.fromByte((byte) i).asByte()).isEqualTo((byte) i);
    }
  }
}
