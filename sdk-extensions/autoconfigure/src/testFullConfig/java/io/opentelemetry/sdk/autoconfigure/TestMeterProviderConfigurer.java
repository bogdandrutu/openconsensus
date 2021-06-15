/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.SdkMeterProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.processor.LabelsProcessorFactory;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.metrics.view.View;

public class TestMeterProviderConfigurer implements SdkMeterProviderConfigurer {

  @Override
  public void configure(SdkMeterProviderBuilder meterProviderBuilder, ConfigProperties config) {
    LabelsProcessorFactory labelsProcessorFactory =
        (resource, instrumentationLibraryInfo, descriptor) ->
            (ctx, labels) -> labels.toBuilder().put("configured", "true").build();

    for (InstrumentType instrumentType : InstrumentType.values()) {
      meterProviderBuilder.registerView(
          InstrumentSelector.builder().setInstrumentType(instrumentType).build(),
          View.builder()
              .setAggregatorFactory(AggregatorFactory.count(AggregationTemporality.DELTA))
              .setLabelsProcessorFactory(labelsProcessorFactory)
              .build());
    }
  }
}
