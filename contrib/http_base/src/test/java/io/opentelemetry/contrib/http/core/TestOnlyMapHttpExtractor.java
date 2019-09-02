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

package io.opentelemetry.contrib.http.core;

import java.util.Map;
import javax.annotation.Nullable;

/** Only used for testing to pull values from a map constructed in a test case. */
class TestOnlyMapHttpExtractor extends HttpExtractor<Map<String, String>, Map<String, String>> {

  public static final String METHOD = "METHOD";
  public static final String URL = "URL";
  public static final String PATH = "PATH";
  public static final String ROUTE = "ROUTE";
  public static final String STATUS = "STATUS";

  @Nullable
  @Override
  public String getMethod(Map<String, String> request) {
    return request.get(METHOD);
  }

  @Nullable
  @Override
  public String getUrl(Map<String, String> request) {
    return request.get(URL);
  }

  @Nullable
  @Override
  public String getPath(Map<String, String> request) {
    return request.get(PATH);
  }

  @Nullable
  @Override
  public String getRoute(Map<String, String> request) {
    return request.get(ROUTE);
  }

  @Override
  public int getStatusCode(@Nullable Map<String, String> response) {
    if (response == null) {
      return 0;
    }
    String status = response.get(STATUS);
    if (status == null) {
      return 0;
    } else {
      return Integer.parseInt(status);
    }
  }
}
