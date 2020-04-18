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

package io.opentelemetry.sdk.common;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link ThreadFactory} that delegates to {@code MoreExecutors.platformThreadFactory()} and marks
 * all threads as daemon.
 */
public class DaemonThreadFactory implements ThreadFactory {
  @Override
  public Thread newThread(Runnable runnable) {
    Thread t = MoreExecutors.platformThreadFactory().newThread(runnable);
    try {
      t.setDaemon(true);
    } catch (SecurityException e) {
      // Well, we tried.
    }
    return t;
  }
}
