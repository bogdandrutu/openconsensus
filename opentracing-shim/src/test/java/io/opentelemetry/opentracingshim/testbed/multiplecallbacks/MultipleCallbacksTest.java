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

package io.opentelemetry.opentracingshim.testbed.multiplecallbacks;

import static io.opentelemetry.opentracingshim.testbed.TestUtils.finishedSpansSize;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.opentelemetry.opentracingshim.InMemoryTracer;
import io.opentelemetry.opentracingshim.TraceShim;
import io.opentelemetry.trace.SpanData;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

@SuppressWarnings("FutureReturnValueIgnored")
public class MultipleCallbacksTest {
  private final InMemoryTracer mockTracer = new InMemoryTracer();
  private final Tracer tracer = TraceShim.createTracerShim(mockTracer);

  @Test
  public void test() throws Exception {
    Client client = new Client(tracer);
    Span span = tracer.buildSpan("parent").start();
    try (Scope scope = tracer.activateSpan(span)) {
      client.send("task1", 300);
      client.send("task2", 200);
      client.send("task3", 100);
    } finally {
      span.finish();
    }

    await().atMost(15, TimeUnit.SECONDS).until(finishedSpansSize(mockTracer), equalTo(4));

    List<SpanData> spans = mockTracer.getFinishedSpanDataItems();
    assertEquals(4, spans.size());
    assertEquals("parent", spans.get(0).getName());

    SpanData parentSpan = spans.get(0);
    for (int i = 1; i < 4; i++) {
      assertEquals(parentSpan.getContext().getTraceId(), spans.get(i).getContext().getTraceId());
      assertEquals(parentSpan.getContext().getSpanId(), spans.get(i).getParentSpanId());
    }

    assertNull(tracer.scopeManager().activeSpan());
  }
}
