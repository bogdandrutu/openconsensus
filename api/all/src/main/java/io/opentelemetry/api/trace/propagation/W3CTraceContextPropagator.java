/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.trace.propagation;

import static io.opentelemetry.api.internal.Utils.checkArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Implementation of the W3C TraceContext propagation protocol. See <a
 * href="https://www.w3.org/TR/trace-context-1/">W3C Trace Context</a>.
 *
 * <p>This is the default propagator for {@link SpanContext}s. The {@link SpanContext} type is
 * designed to support all the data propagated via W3C propagation natively.
 */
@Immutable
public final class W3CTraceContextPropagator implements TextMapPropagator {
  private static final Logger logger = Logger.getLogger(W3CTraceContextPropagator.class.getName());

  static final String TRACE_PARENT = "traceparent";
  static final String TRACE_STATE = "tracestate";
  private static final List<String> FIELDS =
      Collections.unmodifiableList(Arrays.asList(TRACE_PARENT, TRACE_STATE));

  private static final String VERSION = "00";
  private static final int VERSION_SIZE = 2;
  private static final char TRACEPARENT_DELIMITER = '-';
  private static final int TRACEPARENT_DELIMITER_SIZE = 1;
  private static final int TRACE_ID_HEX_SIZE = TraceId.getLength();
  private static final int SPAN_ID_HEX_SIZE = SpanId.getLength();
  private static final int TRACE_OPTION_HEX_SIZE = TraceFlags.getLength();
  private static final int TRACE_ID_OFFSET = VERSION_SIZE + TRACEPARENT_DELIMITER_SIZE;
  private static final int SPAN_ID_OFFSET =
      TRACE_ID_OFFSET + TRACE_ID_HEX_SIZE + TRACEPARENT_DELIMITER_SIZE;
  private static final int TRACE_OPTION_OFFSET =
      SPAN_ID_OFFSET + SPAN_ID_HEX_SIZE + TRACEPARENT_DELIMITER_SIZE;
  private static final int TRACEPARENT_HEADER_SIZE = TRACE_OPTION_OFFSET + TRACE_OPTION_HEX_SIZE;
  private static final int TRACESTATE_MAX_SIZE = 512;
  private static final int TRACESTATE_MAX_MEMBERS = 32;
  private static final char TRACESTATE_KEY_VALUE_DELIMITER = '=';
  private static final char TRACESTATE_ENTRY_DELIMITER = ',';
  private static final Pattern TRACESTATE_ENTRY_DELIMITER_SPLIT_PATTERN =
      Pattern.compile("[ \t]*" + TRACESTATE_ENTRY_DELIMITER + "[ \t]*");
  private static final Set<String> VALID_VERSIONS;
  private static final String VERSION_00 = "00";
  private static final W3CTraceContextPropagator INSTANCE = new W3CTraceContextPropagator();

  static {
    // A valid version is 1 byte representing an 8-bit unsigned integer, version ff is invalid.
    VALID_VERSIONS = new HashSet<>();
    for (int i = 0; i < 255; i++) {
      String version = Long.toHexString(i);
      if (version.length() < 2) {
        version = '0' + version;
      }
      VALID_VERSIONS.add(version);
    }
  }

  private W3CTraceContextPropagator() {
    // singleton
  }

  /**
   * Returns a singleton instance of a {@link TextMapPropagator} implementing the W3C TraceContext
   * propagation.
   */
  public static W3CTraceContextPropagator getInstance() {
    return INSTANCE;
  }

  @Override
  public Collection<String> fields() {
    return FIELDS;
  }

  @Override
  public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
    if (context == null || setter == null) {
      return;
    }

    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    if (!spanContext.isValid()) {
      return;
    }

    char[] chars = TemporaryBuffers.chars(TRACEPARENT_HEADER_SIZE);
    chars[0] = VERSION.charAt(0);
    chars[1] = VERSION.charAt(1);
    chars[2] = TRACEPARENT_DELIMITER;

    String traceId = spanContext.getTraceId();
    for (int i = 0; i < traceId.length(); i++) {
      chars[TRACE_ID_OFFSET + i] = traceId.charAt(i);
    }

    chars[SPAN_ID_OFFSET - 1] = TRACEPARENT_DELIMITER;

    String spanId = spanContext.getSpanId();
    for (int i = 0; i < spanId.length(); i++) {
      chars[SPAN_ID_OFFSET + i] = spanId.charAt(i);
    }

    chars[TRACE_OPTION_OFFSET - 1] = TRACEPARENT_DELIMITER;
    String traceFlagsHex = spanContext.getTraceFlags().asHex();
    chars[TRACE_OPTION_OFFSET] = traceFlagsHex.charAt(0);
    chars[TRACE_OPTION_OFFSET + 1] = traceFlagsHex.charAt(1);
    setter.set(carrier, TRACE_PARENT, new String(chars, 0, TRACEPARENT_HEADER_SIZE));
    TraceState traceState = spanContext.getTraceState();
    if (traceState.isEmpty()) {
      // No need to add an empty "tracestate" header.
      return;
    }
    StringBuilder stringBuilder = new StringBuilder(TRACESTATE_MAX_SIZE);
    traceState.forEach(
        (key, value) -> {
          if (stringBuilder.length() != 0) {
            stringBuilder.append(TRACESTATE_ENTRY_DELIMITER);
          }
          stringBuilder.append(key).append(TRACESTATE_KEY_VALUE_DELIMITER).append(value);
        });
    setter.set(carrier, TRACE_STATE, stringBuilder.toString());
  }

  @Override
  public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
    if (context == null) {
      return Context.root();
    }
    if (getter == null) {
      return context;
    }

    SpanContext spanContext = extractImpl(carrier, getter);
    if (!spanContext.isValid()) {
      return context;
    }

    return context.with(Span.wrap(spanContext));
  }

  private static <C> SpanContext extractImpl(@Nullable C carrier, TextMapGetter<C> getter) {
    String traceParent = getter.get(carrier, TRACE_PARENT);
    if (traceParent == null) {
      return SpanContext.getInvalid();
    }

    SpanContext contextFromParentHeader = extractContextFromTraceParent(traceParent);
    if (!contextFromParentHeader.isValid()) {
      return contextFromParentHeader;
    }

    String traceStateHeader = getter.get(carrier, TRACE_STATE);
    if (traceStateHeader == null || traceStateHeader.isEmpty()) {
      return contextFromParentHeader;
    }

    try {
      TraceState traceState = extractTraceState(traceStateHeader);
      return SpanContext.createFromRemoteParent(
          contextFromParentHeader.getTraceId(),
          contextFromParentHeader.getSpanId(),
          contextFromParentHeader.getTraceFlags(),
          traceState);
    } catch (IllegalArgumentException e) {
      logger.fine("Unparseable tracestate header. Returning span context without state.");
      return contextFromParentHeader;
    }
  }

  private static SpanContext extractContextFromTraceParent(String traceparent) {
    // TODO(bdrutu): Do we need to verify that version is hex and that
    // for the version the length is the expected one?
    boolean isValid =
        (traceparent.length() == TRACEPARENT_HEADER_SIZE
                || (traceparent.length() > TRACEPARENT_HEADER_SIZE
                    && traceparent.charAt(TRACEPARENT_HEADER_SIZE) == TRACEPARENT_DELIMITER))
            && traceparent.charAt(TRACE_ID_OFFSET - 1) == TRACEPARENT_DELIMITER
            && traceparent.charAt(SPAN_ID_OFFSET - 1) == TRACEPARENT_DELIMITER
            && traceparent.charAt(TRACE_OPTION_OFFSET - 1) == TRACEPARENT_DELIMITER;
    if (!isValid) {
      logger.fine("Unparseable traceparent header. Returning INVALID span context.");
      return SpanContext.getInvalid();
    }

    String version = traceparent.substring(0, 2);
    if (!VALID_VERSIONS.contains(version)) {
      return SpanContext.getInvalid();
    }
    if (version.equals(VERSION_00) && traceparent.length() > TRACEPARENT_HEADER_SIZE) {
      return SpanContext.getInvalid();
    }

    String traceId = traceparent.substring(TRACE_ID_OFFSET, TRACE_ID_OFFSET + TraceId.getLength());
    String spanId = traceparent.substring(SPAN_ID_OFFSET, SPAN_ID_OFFSET + SpanId.getLength());

    TraceFlags traceFlags = TraceFlags.fromHex(traceparent, TRACE_OPTION_OFFSET);
    return SpanContext.createFromRemoteParent(traceId, spanId, traceFlags, TraceState.getDefault());
  }

  private static TraceState extractTraceState(String traceStateHeader) {
    TraceStateBuilder traceStateBuilder = TraceState.builder();
    String[] listMembers = TRACESTATE_ENTRY_DELIMITER_SPLIT_PATTERN.split(traceStateHeader);
    checkArgument(
        listMembers.length <= TRACESTATE_MAX_MEMBERS, "TraceState has too many elements.");
    // Iterate in reverse order because when call builder set the elements is added in the
    // front of the list.
    for (int i = listMembers.length - 1; i >= 0; i--) {
      String listMember = listMembers[i];
      int index = listMember.indexOf(TRACESTATE_KEY_VALUE_DELIMITER);
      checkArgument(index != -1, "Invalid TraceState list-member format.");
      traceStateBuilder.put(listMember.substring(0, index), listMember.substring(index + 1));
    }
    return traceStateBuilder.build();
  }
}
