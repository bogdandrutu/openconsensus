/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;

class GlobalOpenTelemetryTest {

  @RegisterExtension
  LogCapturer logs = LogCapturer.create().captureForType(GlobalOpenTelemetry.class);

  @Test
  void logsWarningOnAccessWithoutSdk() {
    GlobalOpenTelemetry.get();
    LoggingEvent log =
        logs.assertContains(
            "Attempt to access GlobalOpenTelemetry.get before OpenTelemetrySdk has been "
                + "initialized.");
    assertThat(log.getLevel()).isEqualTo(Level.ERROR);
  }
}
