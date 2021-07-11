/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.logging.otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.netmikey.logunit.api.LogCapturer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.DoubleSumData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.event.Level;

class OtlpJsonLoggingMetricExporterTest {

  private static final Resource RESOURCE =
      Resource.create(Attributes.builder().put("key", "value").build());

  private static final AttributeKey<String> CAT_KEY = AttributeKey.stringKey("cat");

  private static final MetricData METRIC1 =
      MetricData.createDoubleSum(
          RESOURCE,
          InstrumentationLibraryInfo.create("instrumentation", "1"),
          "metric1",
          "metric1 description",
          "m",
          DoubleSumData.create(
              true,
              AggregationTemporality.CUMULATIVE,
              Arrays.asList(DoublePointData.create(1, 2, Attributes.of(CAT_KEY, "meow"), 4))));

  private static final MetricData METRIC2 =
      MetricData.createDoubleSum(
          RESOURCE,
          InstrumentationLibraryInfo.create("instrumentation2", "2"),
          "metric2",
          "metric2 description",
          "s",
          DoubleSumData.create(
              true,
              AggregationTemporality.CUMULATIVE,
              Arrays.asList(DoublePointData.create(1, 2, Attributes.of(CAT_KEY, "meow"), 4))));

  @RegisterExtension
  LogCapturer logs = LogCapturer.create().captureForType(OtlpJsonLoggingMetricExporter.class);

  private MetricExporter exporter;

  @BeforeEach
  void setUp() {
    exporter = OtlpJsonLoggingMetricExporter.create();
  }

  @Test
  void log() throws Exception {
    exporter.export(Arrays.asList(METRIC1, METRIC2));

    assertThat(logs.getEvents())
        .hasSize(1)
        .allSatisfy(log -> assertThat(log.getLevel()).isEqualTo(Level.INFO));
    JSONAssert.assertEquals(
        "{"
            + "  \"resource\": {"
            + "    \"attributes\": [{"
            + "      \"key\": \"key\","
            + "      \"value\": {"
            + "        \"stringValue\": \"value\""
            + "      }"
            + "    }]"
            + "  },"
            + "  \"instrumentationLibraryMetrics\": [{"
            + "    \"instrumentationLibrary\": {"
            + "      \"name\": \"instrumentation2\","
            + "      \"version\": \"2\""
            + "    },"
            + "    \"metrics\": [{"
            + "      \"name\": \"metric2\","
            + "      \"description\": \"metric2 description\","
            + "      \"unit\": \"s\","
            + "      \"sum\": {"
            + "        \"dataPoints\": [{"
            + "          \"attributes\": [{"
            + "            \"key\": \"cat\","
            + "            \"value\": {\"stringValue\": \"meow\"}"
            + "          }],"
            + "          \"startTimeUnixNano\": \"1\","
            + "          \"timeUnixNano\": \"2\","
            + "          \"asDouble\": 4.0"
            + "        }],"
            + "        \"aggregationTemporality\": \"AGGREGATION_TEMPORALITY_CUMULATIVE\","
            + "        \"isMonotonic\": true"
            + "      }"
            + "    }]"
            + "  }, {"
            + "    \"instrumentationLibrary\": {"
            + "      \"name\": \"instrumentation\","
            + "      \"version\": \"1\""
            + "    },"
            + "    \"metrics\": [{"
            + "      \"name\": \"metric1\","
            + "      \"description\": \"metric1 description\","
            + "      \"unit\": \"m\","
            + "      \"sum\": {"
            + "        \"dataPoints\": [{"
            + "          \"attributes\": [{"
            + "            \"key\": \"cat\","
            + "            \"value\": {\"stringValue\": \"meow\"}"
            + "          }],"
            + "          \"startTimeUnixNano\": \"1\","
            + "          \"timeUnixNano\": \"2\","
            + "          \"asDouble\": 4.0"
            + "        }],"
            + "        \"aggregationTemporality\": \"AGGREGATION_TEMPORALITY_CUMULATIVE\","
            + "        \"isMonotonic\": true"
            + "      }"
            + "    }]"
            + "  }]"
            + "}",
        logs.getEvents().get(0).getMessage(),
        /* strict= */ true);
    assertThat(logs.getEvents().get(0).getMessage()).doesNotContain("\n");
  }

  @Test
  void flush() {
    assertThat(exporter.flush().isSuccess()).isTrue();
  }

  @Test
  void shutdown() {
    assertThat(exporter.shutdown().isSuccess()).isTrue();
  }
}
