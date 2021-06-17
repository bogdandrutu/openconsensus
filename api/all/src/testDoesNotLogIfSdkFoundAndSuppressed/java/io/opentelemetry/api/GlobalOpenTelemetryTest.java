/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class GlobalOpenTelemetryTest {

  @RegisterExtension
  LogCapturer logs = LogCapturer.create().captureForType(GlobalOpenTelemetry.class);

  @Test
  void logIsSuppressed() {
    GlobalOpenTelemetry.get();
    logs.assertDoesNotContain(
        "Attempt to access GlobalOpenTelemetry.get before OpenTelemetrySdk has been "
            + "initialized.");
  }
}
