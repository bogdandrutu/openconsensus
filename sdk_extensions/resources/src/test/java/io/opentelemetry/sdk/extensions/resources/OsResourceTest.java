/*
 * Copyright 2020, OpenTelemetry Authors
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

package io.opentelemetry.sdk.extensions.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.opentelemetry.common.Attributes;
import io.opentelemetry.common.ReadableAttributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceAttributes;
import org.junit.jupiter.api.Test;

class OsResourceTest {

  private static final OsResource RESOURCE = new OsResource();

  @Test
  void linux() {
    assumeThat(System.getProperty("os.name").toLowerCase()).startsWith("linux");
    Attributes attributes = RESOURCE.getAttributes();
    assertThat(attributes.get(ResourceAttributes.OS_NAME)).isEqualTo("LINUX");
    assertThat(attributes.get(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  void macos() {
    assumeThat(System.getProperty("os.name").toLowerCase()).startsWith("mac");
    Attributes attributes = RESOURCE.getAttributes();
    assertThat(attributes.get(ResourceAttributes.OS_NAME)).isEqualTo("DARWIN");
    assertThat(attributes.get(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  void windows() {
    assumeThat(System.getProperty("os.name").toLowerCase()).startsWith("windows");
    Attributes attributes = RESOURCE.getAttributes();
    assertThat(attributes.get(ResourceAttributes.OS_NAME)).isEqualTo("WINDOWS");
    assertThat(attributes.get(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  void inDefault() {
    ReadableAttributes attributes = Resource.getDefault().getAttributes();
    assertThat(attributes.get(ResourceAttributes.OS_NAME)).isNotNull();
    assertThat(attributes.get(ResourceAttributes.OS_DESCRIPTION)).isNotNull();
  }
}
