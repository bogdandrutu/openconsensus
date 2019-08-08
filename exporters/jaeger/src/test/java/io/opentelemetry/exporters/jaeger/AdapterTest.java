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

package io.opentelemetry.exporters.jaeger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.opentelemetry.exporters.jaeger.proto.api_v2.Model;
import io.opentelemetry.proto.trace.v1.AttributeValue;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class AdapterTest {

  @Test
  public void testProtoSpans() {
    long duration = 900; // ms
    long startMs = System.currentTimeMillis();
    long endMs = startMs + duration;
    Timestamp startTime = toTimestamp(startMs);
    Timestamp endTime = toTimestamp(endMs);

    Span span = getProtoSpan(startTime, endTime);
    List<Span> spans = Collections.singletonList(span);

    Collection<Model.Span> jaegerSpans = Adapter.toJaeger(spans);

    // the span contents are checked somewhere else
    assertEquals(1, jaegerSpans.size());
  }

  @Test
  public void testProtoSpan() {
    long duration = 900; // ms
    long startMs = System.currentTimeMillis();
    long endMs = startMs + duration;
    Timestamp startTime = toTimestamp(startMs);
    Timestamp endTime = toTimestamp(endMs);

    Span span = getProtoSpan(startTime, endTime);

    // test
    Model.Span jaegerSpan = Adapter.toJaeger(span);
    assertEquals("abc123", jaegerSpan.getTraceId().toStringUtf8());
    assertEquals("def456", jaegerSpan.getSpanId().toStringUtf8());
    assertEquals("GET /api/endpoint", jaegerSpan.getOperationName());
    assertEquals(startTime, jaegerSpan.getStartTime());
    assertEquals(duration, jaegerSpan.getDuration().getNanos() / 1000000);

    assertEquals(2, jaegerSpan.getTagsCount());

    boolean foundSpanKind = false;
    boolean foundAttribute = false;
    for (Model.KeyValue kv : jaegerSpan.getTagsList()) {
      if (kv.getKey().equals("span.kind")) {
        foundSpanKind = true;
        // TODO(jpkroehling) check if it's OK to have a different case here
        // most of the current instrumentation libraries use lower case
        assertEquals("SERVER", kv.getVStr());
      }
      if (kv.getKey().equals("valueB")) {
        foundAttribute = true;
        assertTrue(kv.getVBool());
      }
    }
    assertTrue("tag 'valueB' wasn't set", foundAttribute);
    assertTrue("tag 'span.kind' wasn't set", foundSpanKind);

    assertEquals(1, jaegerSpan.getLogsCount());
    Model.Log log = jaegerSpan.getLogs(0);
    boolean foundMessage = false;
    boolean foundTag = false;
    for (Model.KeyValue kv : log.getFieldsList()) {
      if (kv.getKey().equals("message")) {
        foundMessage = true;
        assertEquals("the log message", kv.getVStr());
      }
      if (kv.getKey().equals("foo")) {
        foundTag = true;
        assertEquals("bar", kv.getVStr());
      }
    }
    assertTrue("log message wasn't set", foundMessage);
    assertTrue("log tag 'foo' wasn't set", foundTag);

    assertEquals(1, jaegerSpan.getReferencesCount());
    assertEquals(Model.SpanRefType.CHILD_OF, jaegerSpan.getReferences(0).getRefType());
    assertEquals("parent123", jaegerSpan.getReferences(0).getTraceId().toStringUtf8());
    assertEquals("parent456", jaegerSpan.getReferences(0).getSpanId().toStringUtf8());
  }

  @Test
  public void testJaegerLogs() {
    // prepare
    Span.TimedEvents timedEvents = getTimedEvents();

    // test
    Collection<Model.Log> logs = Adapter.toJaegerLogs(timedEvents);

    // verify
    assertEquals(1, logs.size());
  }

  @Test
  public void testJaegerLog() {
    // prepare
    Span.TimedEvent timedEvent = getTimedEvent();

    // test
    Model.Log log = Adapter.toJaegerLog(timedEvent);

    // verify
    assertEquals(2, log.getFieldsCount());

    boolean foundMessage = false;
    boolean foundTag = false;
    for (Model.KeyValue kv : log.getFieldsList()) {
      if (kv.getKey().equals("message")) {
        foundMessage = true;
        assertEquals("the log message", kv.getVStr());
      }

      if (kv.getKey().equals("foo")) {
        foundTag = true;
        assertEquals("bar", kv.getVStr());
      }
    }

    assertTrue("Could not find the 'name' key", foundMessage);
    assertTrue("Could not find the 'foo' key", foundTag);
  }

  @Test
  public void testKeyValues() {
    // prepare
    AttributeValue valueB = AttributeValue.newBuilder().setBoolValue(true).build();
    Span.Attributes attributes =
        Span.Attributes.newBuilder().putAttributeMap("valueB", valueB).build();

    // test
    Collection<Model.KeyValue> keyValues = Adapter.toKeyValues(attributes);

    // verify
    // the actual content is checked in some other test
    assertEquals(1, keyValues.size());
  }

  @Test
  public void testKeyValue() {
    // prepare
    AttributeValue valueB = AttributeValue.newBuilder().setBoolValue(true).build();
    AttributeValue valueD = AttributeValue.newBuilder().setDoubleValue(1.).build();
    AttributeValue valueI = AttributeValue.newBuilder().setIntValue(2).build();
    AttributeValue valueS = AttributeValue.newBuilder().setStringValue("foobar").build();

    // test
    Model.KeyValue kvB = Adapter.toKeyValue("valueB", valueB);
    Model.KeyValue kvD = Adapter.toKeyValue("valueD", valueD);
    Model.KeyValue kvI = Adapter.toKeyValue("valueI", valueI);
    Model.KeyValue kvS = Adapter.toKeyValue("valueS", valueS);

    // verify
    assertTrue(kvB.getVBool());
    assertEquals(1., kvD.getVFloat64(), 0);
    assertEquals(2, kvI.getVInt64());
    assertEquals("foobar", kvS.getVStr());
    assertEquals("foobar", kvS.getVStrBytes().toStringUtf8());
  }

  @Test
  public void testSpanRefs() {
    // prepare
    Span.Link link =
        Span.Link.newBuilder()
            .setSpanId(ByteString.copyFromUtf8("abc123"))
            .setTraceId(ByteString.copyFromUtf8("def456"))
            .build();

    Span.Links links = Span.Links.newBuilder().addLink(link).build();

    // test
    Collection<Model.SpanRef> spanRefs = Adapter.toSpanRefs(links);

    // verify
    assertEquals(1, spanRefs.size()); // the actual span ref is tested in another test
  }

  @Test
  public void testSpanRef() {
    // prepare
    Span.Link link =
        Span.Link.newBuilder()
            .setSpanId(ByteString.copyFromUtf8("abc123"))
            .setTraceId(ByteString.copyFromUtf8("def456"))
            .build();

    // test
    Model.SpanRef spanRef = Adapter.toSpanRef(link);

    // verify
    assertEquals("abc123", spanRef.getSpanId().toStringUtf8());
    assertEquals("def456", spanRef.getTraceId().toStringUtf8());
    assertEquals(Model.SpanRefType.CHILD_OF, spanRef.getRefType());
  }

  @Test
  public void testDurationUnderOneSec() {
    // prepare
    long startM = System.currentTimeMillis();
    long endM = startM + 900; // 900ms after...
    Timestamp start = toTimestamp(startM);
    Timestamp end = toTimestamp(endM);

    // test
    Duration duration = Adapter.getDuration(start, end);

    // verify
    assertEquals(900, duration.getNanos() / 1000000);
    assertEquals(0, duration.getSeconds());
  }

  @Test
  public void testDurationOverOneSec() {
    // prepare
    long startM = System.currentTimeMillis();
    long endM = startM + 1900; // 1900ms after...
    Timestamp start = toTimestamp(startM);
    Timestamp end = toTimestamp(endM);

    // test
    Duration duration = Adapter.getDuration(start, end);

    // verify
    assertEquals(900, duration.getNanos() / 1000000);
    assertEquals(1, duration.getSeconds());
  }

  @Test
  public void testNegativeDuration() {
    // prepare
    // this test is just to ensure the logic inside the duration:
    // "negative duration" should never happen in real life
    long endM = System.currentTimeMillis();
    long startM = endM + 900;
    Timestamp start = toTimestamp(startM);
    Timestamp end = toTimestamp(endM);

    // test
    Duration duration = Adapter.getDuration(start, end);

    // verify
    assertEquals(-900, duration.getNanos() / 1000000);
    assertEquals(0, duration.getSeconds());
  }

  public Span.TimedEvents getTimedEvents() {
    Span.TimedEvent timedEvent = getTimedEvent();
    return Span.TimedEvents.newBuilder().addTimedEvent(timedEvent).build();
  }

  private Span.TimedEvent getTimedEvent() {
    long ms = System.currentTimeMillis();
    Timestamp ts = toTimestamp(ms);
    AttributeValue valueS = AttributeValue.newBuilder().setStringValue("bar").build();
    Span.Attributes attributes =
        Span.Attributes.newBuilder().putAttributeMap("foo", valueS).build();

    return Span.TimedEvent.newBuilder()
        .setTime(ts)
        .setEvent(
            Span.TimedEvent.Event.newBuilder()
                .setName("the log message")
                .setAttributes(attributes)
                .build())
        .build();
  }

  private Span getProtoSpan(Timestamp startTime, Timestamp endTime) {
    AttributeValue valueB = AttributeValue.newBuilder().setBoolValue(true).build();
    Span.Attributes attributes =
        Span.Attributes.newBuilder().putAttributeMap("valueB", valueB).build();

    Span.Link link =
        Span.Link.newBuilder()
            .setTraceId(ByteString.copyFromUtf8("parent123"))
            .setSpanId(ByteString.copyFromUtf8("parent456"))
            .build();

    Span.Links links = Span.Links.newBuilder().addLink(link).build();

    return Span.newBuilder()
        .setTraceId(ByteString.copyFromUtf8("abc123"))
        .setSpanId(ByteString.copyFromUtf8("def456"))
        .setName("GET /api/endpoint")
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setAttributes(attributes)
        .setTimeEvents(getTimedEvents())
        .setLinks(links)
        .setKind(Span.SpanKind.SERVER)
        .build();
  }

  Timestamp toTimestamp(long ms) {
    return Timestamp.newBuilder()
        .setSeconds(ms / 1000)
        .setNanos((int) ((ms % 1000) * 1000000))
        .build();
  }
}
