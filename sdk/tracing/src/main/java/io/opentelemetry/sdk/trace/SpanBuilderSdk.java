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

package io.opentelemetry.sdk.trace;

import io.grpc.Context;
import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.common.ReadableAttributes;
import io.opentelemetry.common.ReadableKeyValuePairs.KeyValueConsumer;
import io.opentelemetry.internal.StringUtils;
import io.opentelemetry.internal.Utils;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.MonotonicClock;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.Sampler.SamplingResult;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.data.SpanData.Link;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceState;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** {@link SpanBuilderSdk} is SDK implementation of {@link Span.Builder}. */
final class SpanBuilderSdk implements Span.Builder {
  private static final TraceFlags TRACE_OPTIONS_SAMPLED =
      TraceFlags.builder().setIsSampled(true).build();
  private static final TraceFlags TRACE_OPTIONS_NOT_SAMPLED =
      TraceFlags.builder().setIsSampled(false).build();

  private final String spanName;
  private final InstrumentationLibraryInfo instrumentationLibraryInfo;
  private final SpanProcessor spanProcessor;
  private final TraceConfig traceConfig;
  private final Resource resource;
  private final IdsGenerator idsGenerator;
  private final Clock clock;

  @Nullable private Context parent;
  private Kind spanKind = Kind.INTERNAL;
  @Nullable private AttributesMap attributes;
  @Nullable private List<io.opentelemetry.trace.Link> links;
  private int totalNumberOfLinksAdded = 0;
  private long startEpochNanos = 0;
  private boolean isRootSpan;

  SpanBuilderSdk(
      String spanName,
      InstrumentationLibraryInfo instrumentationLibraryInfo,
      SpanProcessor spanProcessor,
      TraceConfig traceConfig,
      Resource resource,
      IdsGenerator idsGenerator,
      Clock clock) {
    this.spanName = spanName;
    this.instrumentationLibraryInfo = instrumentationLibraryInfo;
    this.spanProcessor = spanProcessor;
    this.traceConfig = traceConfig;
    this.resource = resource;
    this.idsGenerator = idsGenerator;
    this.clock = clock;
  }

  @Override
  public Span.Builder setParent(Context context) {
    Objects.requireNonNull(context, "context");
    this.isRootSpan = false;
    this.parent = context;
    return this;
  }

  @Override
  public Span.Builder setNoParent() {
    this.isRootSpan = true;
    this.parent = null;
    return this;
  }

  @Override
  public Span.Builder setSpanKind(Kind spanKind) {
    this.spanKind = Objects.requireNonNull(spanKind, "spanKind");
    return this;
  }

  @Override
  public Span.Builder addLink(SpanContext spanContext) {
    addLink(Link.create(spanContext));
    return this;
  }

  @Override
  public Span.Builder addLink(SpanContext spanContext, Attributes attributes) {
    int totalAttributeCount = attributes.size();
    addLink(
        Link.create(
            spanContext,
            RecordEventsReadableSpan.copyAndLimitAttributes(
                attributes, traceConfig.getMaxNumberOfAttributesPerLink()),
            totalAttributeCount));
    return this;
  }

  @Override
  public Span.Builder addLink(io.opentelemetry.trace.Link link) {
    Objects.requireNonNull(link, "link");
    totalNumberOfLinksAdded++;
    if (links == null) {
      links = new ArrayList<>(traceConfig.getMaxNumberOfLinks());
    }

    // don't bother doing anything with any links beyond the max.
    if (links.size() == traceConfig.getMaxNumberOfLinks()) {
      return this;
    }

    links.add(link);
    return this;
  }

  @Override
  public Span.Builder setAttribute(String key, String value) {
    return setAttribute(key, AttributeValue.stringAttributeValue(value));
  }

  @Override
  public Span.Builder setAttribute(String key, long value) {
    return setAttribute(key, AttributeValue.longAttributeValue(value));
  }

  @Override
  public Span.Builder setAttribute(String key, double value) {
    return setAttribute(key, AttributeValue.doubleAttributeValue(value));
  }

  @Override
  public Span.Builder setAttribute(String key, boolean value) {
    return setAttribute(key, AttributeValue.booleanAttributeValue(value));
  }

  @Override
  public Span.Builder setAttribute(String key, AttributeValue value) {
    Objects.requireNonNull(key, "key");
    if (value == null
        || (value.getType() == AttributeValue.Type.STRING && value.getStringValue() == null)) {
      if (attributes != null) {
        attributes.remove(key);
      }
      return this;
    }
    if (attributes == null) {
      attributes = new AttributesMap(traceConfig.getMaxNumberOfAttributes());
    }

    if (traceConfig.shouldTruncateStringAttributeValues()) {
      value = StringUtils.truncateToSize(value, traceConfig.getMaxLengthOfAttributeValues());
    }

    attributes.put(key, value);
    return this;
  }

  @Override
  public Span.Builder setStartTimestamp(long startTimestamp) {
    Utils.checkArgument(startTimestamp >= 0, "Negative startTimestamp");
    startEpochNanos = startTimestamp;
    return this;
  }

  @Override
  public Span startSpan() {
    final Context originalParent = parent == null ? Context.current() : parent;
    final Context parentContext =
        isRootSpan
            ? TracingContextUtils.withSpan(DefaultSpan.getInvalid(), originalParent)
            : originalParent;
    final Span parentSpan = TracingContextUtils.getSpan(parentContext);
    final SpanContext parentSpanContext = parentSpan.getContext();
    String traceId;
    String spanId = idsGenerator.generateSpanId();
    TraceState traceState = TraceState.getDefault();
    if (!parentSpanContext.isValid()) {
      // New root span.
      traceId = idsGenerator.generateTraceId();
    } else {
      // New child span.
      traceId = parentSpanContext.getTraceIdAsHexString();
      traceState = parentSpanContext.getTraceState();
    }
    List<io.opentelemetry.trace.Link> immutableLinks =
        links == null
            ? Collections.<io.opentelemetry.trace.Link>emptyList()
            : Collections.unmodifiableList(links);
    // Avoid any possibility to modify the links list by adding links to the Builder after the
    // startSpan is called. If that happens all the links will be added in a new list.
    links = null;
    ReadableAttributes immutableAttributes = attributes == null ? Attributes.empty() : attributes;
    SamplingResult samplingResult =
        traceConfig
            .getSampler()
            .shouldSample(
                parentSpanContext,
                traceId,
                spanName,
                spanKind,
                immutableAttributes,
                immutableLinks);
    Sampler.Decision samplingDecision = samplingResult.getDecision();

    TraceFlags traceFlags =
        Samplers.isSampled(samplingDecision) ? TRACE_OPTIONS_SAMPLED : TRACE_OPTIONS_NOT_SAMPLED;
    SpanContext spanContext = createSpanContext(traceId, spanId, traceState, traceFlags);

    if (!Samplers.isRecording(samplingDecision)) {
      return DefaultSpan.create(spanContext);
    }
    ReadableAttributes samplingAttributes = samplingResult.getAttributes();
    if (!samplingAttributes.isEmpty()) {
      if (attributes == null) {
        attributes = new AttributesMap(traceConfig.getMaxNumberOfAttributes());
      }
      samplingAttributes.forEach(
          new KeyValueConsumer<AttributeValue>() {
            @Override
            public void consume(String key, AttributeValue value) {
              attributes.put(key, value);
            }
          });
    }

    // Avoid any possibility to modify the attributes by adding attributes to the Builder after the
    // startSpan is called. If that happens all the attributes will be added in a new map.
    AttributesMap recordedAttributes = attributes;
    attributes = null;

    return RecordEventsReadableSpan.startSpan(
        spanContext,
        spanName,
        instrumentationLibraryInfo,
        spanKind,
        parentContext,
        traceConfig,
        spanProcessor,
        getClock(parentSpan, clock),
        resource,
        recordedAttributes,
        immutableLinks,
        totalNumberOfLinksAdded,
        startEpochNanos);
  }

  private static SpanContext createSpanContext(
      String traceId, String spanId, TraceState traceState, TraceFlags traceFlags) {
    return SpanContext.create(traceId, spanId, traceFlags, traceState);
  }

  private static Clock getClock(Span parent, Clock clock) {
    if (parent instanceof RecordEventsReadableSpan) {
      RecordEventsReadableSpan parentRecordEventsSpan = (RecordEventsReadableSpan) parent;
      return parentRecordEventsSpan.getClock();
    } else {
      return MonotonicClock.create(clock);
    }
  }
}
