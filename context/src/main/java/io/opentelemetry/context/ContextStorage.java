/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2020 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.opentelemetry.context;

import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * The storage for storing and retrieving the current {@link Context}.
 *
 * <p>If you want to implement your own storage or add some hooks when a {@link Context} is attached
 * and restored, you should use {@link ContextStorageProvider}. Here's an example that sets MDC
 * before {@link Context} is attached:
 *
 * <pre>{@code
 * > public class MyStorage implements ContextStorageProvider {
 * >
 * >   @Override
 * >   public ContextStorage get() {
 * >     ContextStorage threadLocalStorage = ContextStorage.defaultStorage();
 * >     return new RequestContextStorage() {
 * >       @Override
 * >       public Scope T attach(Context toAttach) {
 * >         Context current = current();
 * >         setMdc(toAttach);
 * >         Scope scope = threadLocalStorage.attach(toAttach);
 * >         return () -> {
 * >           clearMdc();
 * >           setMdc(current);
 * >           scope.close();
 * >         }
 * >       }
 * >
 * >       @Override
 * >       public Context current() {
 * >         return threadLocalStorage.current();
 * >       }
 * >     }
 * >   }
 * > }
 * }</pre>
 */
public interface ContextStorage {

  /**
   * Returns the {@link ContextStorage} being used by this application. This is only for use when
   * integrating with other context propagation mechanisms and not meant for direct use. To attach
   * or detach a {@link Context} in an application, use {@link Context#makeCurrent()} and {@link
   * Scope#close()}.
   */
  static ContextStorage get() {
    return LazyStorage.get();
  }

  /**
   * Returns the default {@link ContextStorage} which stores {@link Context} using a threadlocal.
   */
  static ContextStorage defaultStorage() {
    return ThreadLocalContextStorage.INSTANCE;
  }

  /**
   * Adds the {@code wrapper}, which will be executed with the {@link ContextStorage} is first used,
   * i.e., by calling {@link Context#makeCurrent()}. This must be called as early in your
   * application as possible to have effect, often as part of a static initialization block in your
   * main class.
   */
  static void addWrapper(Function<? super ContextStorage, ? extends ContextStorage> wrapper) {
    ContextStorageWrappers.addWrapper(wrapper);
  }

  /**
   * Sets the specified {@link Context} as the current {@link Context} and returns a {@link Scope}
   * representing the scope of execution. {@link Scope#close()} must be called when the current
   * {@link Context} should be restored to what it was before attaching {@code toAttach}.
   */
  Scope attach(Context toAttach);

  /**
   * Returns the current {@link Context}. If no {@link Context} has been attached yet, this will
   * return {@code null}.
   */
  @Nullable
  Context current();

  /**
   * Returns the root {@link Context} which all other {@link Context} are derived from.
   *
   * <p>The default implementation returns the root {@link ArrayBasedContext}, but subclasses can
   * override this method to return a root instance of a different {@link Context} implementation.
   */
  default Context root() {
    return ArrayBasedContext.root();
  }
}
