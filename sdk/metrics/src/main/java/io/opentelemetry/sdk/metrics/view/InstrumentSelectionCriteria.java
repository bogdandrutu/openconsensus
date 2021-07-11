/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.view;

import com.google.auto.value.AutoValue;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.instrument.InstrumentDescriptor;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** Selection criteria for construction aggregation views of metrics. */
@AutoValue
@Immutable
public abstract class InstrumentSelectionCriteria {

  /** The `name` of the Meter (optional). */
  @Nullable
  public abstract String getInstrumentationLibraryName();
  /** The `version` of the Meter (optional). */
  @Nullable
  public abstract String getInstrumentationLibraryVersion();
  /** The `schema_url` of the Meter (optional). */
  @Nullable
  public abstract String getInstrumentationSchemaUrl();

  // TODO: name can be a regex?
  /** The `name` of the Instrument (required). */
  public abstract Pattern getInstrumentPattern();

  /** Returns true if a given instrument matches this filter. */
  public boolean matches(
      InstrumentDescriptor instrument, InstrumentationLibraryInfo instrumentationLibrary) {
    if (!getInstrumentPattern().matcher(instrument.getName()).matches()) {
      return false;
    }
    if (getInstrumentationLibraryName() != null
        && !getInstrumentationLibraryName().equals(instrumentationLibrary.getName())) {
      return false;
    }
    if (getInstrumentationLibraryVersion() != null
        && !getInstrumentationLibraryVersion().equals(instrumentationLibrary.getVersion())) {
      return false;
    }
    if (getInstrumentationSchemaUrl() != null
        && !getInstrumentationSchemaUrl().equals(instrumentationLibrary.getSchemaUrl())) {
      return false;
    }
    return true;
  }

  /** Returns a new builder for instrument selection. */
  public static Builder builder() {
    return new AutoValue_InstrumentSelectionCriteria.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public final Builder setInstrumentName(String name) {
      return setInstrumentPattern(Pattern.compile(Pattern.quote(name)));
    }

    public abstract Builder setInstrumentPattern(Pattern namePattern);

    public abstract Builder setInstrumentationLibraryName(String name);

    public abstract Builder setInstrumentationLibraryVersion(String version);

    public abstract Builder setInstrumentationSchemaUrl(String url);

    public abstract InstrumentSelectionCriteria build();
  }
}
