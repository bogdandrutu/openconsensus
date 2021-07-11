/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.SdkMeterProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.view.AttributesProcessors;
import io.opentelemetry.sdk.metrics.view.InstrumentSelectionCriteria;
import io.opentelemetry.sdk.metrics.view.View;
import java.util.regex.Pattern;

public class TestMeterProviderConfigurer implements SdkMeterProviderConfigurer {

  @Override
  @SuppressWarnings("unused")
  public void configure(SdkMeterProviderBuilder meterProviderBuilder) {
    meterProviderBuilder.registerView(
        View.builder()
            // TODO: Reimplement selection criteria.
            .setSelection(
                InstrumentSelectionCriteria.builder()
                    .setInstrumentPattern(Pattern.compile(".*"))
                    .build())
            // TODO: What aggregation do we want?
            .asSum()
            .withDeltaAggregation()
            .addAttributesProcessor(
                AttributesProcessors.appendAttributes(
                    Attributes.builder().put("configured", true).build()))
            .build());
  }
}
