/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.state;

import com.google.auto.value.AutoValue;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import javax.annotation.concurrent.Immutable;

@AutoValue
@Immutable
public abstract class MeterSharedState {
  public static MeterSharedState create(InstrumentationLibraryInfo instrumentationLibraryInfo) {
    return new AutoValue_MeterSharedState(
        instrumentationLibraryInfo, new InstrumentStorageRegistry());
  }

  public abstract InstrumentationLibraryInfo getInstrumentationLibraryInfo();

  public abstract InstrumentStorageRegistry getInstrumentStorageRegistry();
}
