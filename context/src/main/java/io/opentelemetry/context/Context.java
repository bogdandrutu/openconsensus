/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2015 The gRPC Authors
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

package io.opentelemetry.context;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;

/**
 * A context propagation mechanism which can carry scoped-values across API boundaries and between
 * threads.
 *
 * <p>A Context object can be {@linkplain #makeCurrent set} to the {@link ContextStorage}, which
 * effectively forms a <b>scope</b> for the context. The scope is bound to the current thread.
 * Within a scope, its Context is accessible even across API boundaries, through {@link #current}.
 * The scope is later exited by {@link Scope#close()} closing} the scope.
 *
 * <p>Context objects are immutable and inherit state from their parent. To add or overwrite the
 * current state a new context object must be created and then attached, replacing the previously
 * bound context. For example:
 *
 * <pre>{@code
 * Context withCredential = Context.current().with(CRED_KEY, cred);
 * withCredential.wrap(new Runnable() {
 *   public void run() {
 *      readUserRecords(userId, CRED_KEY.get());
 *   }
 * }).run();
 * }</pre>
 *
 * <p>Notes and cautions on use:
 *
 * <ul>
 *   <li>Every {@link #makeCurrent()} must be followed by a {@link Scope#close()}. Breaking these
 *       rules may lead to memory leaks and incorrect scoping.
 *   <li>While Context objects are immutable they do not place such a restriction on the state they
 *       store.
 *   <li>Context is not intended for passing optional parameters to an API and developers should
 *       take care to avoid excessive dependence on context when designing an API.
 *   <li>Attaching Context from a different ancestor will cause information in the current Context
 *       to be lost. This should generally be avoided.
 * </ul>
 *
 * <p>Context propagation is not trivial, and when done incorrectly can lead to broken traces or
 * even mixed traces. We provide a debug mechanism for context propagation, which can be enabled by
 * setting {@code -Dio.opentelemetry.context.enableStrictContext=true} in your JVM args. This will
 * enable a strict checker that makes sure that {@link Scope}s are closed on the correct thread and
 * that they are not garbage collected before being closed. This is done with some relatively
 * expensive stack trace walking. It is highly recommended to enable this in unit tests and staging
 * environments, and you may consider enabling it in production if you have the CPU budget or have
 * very strict requirements on context being propagated correctly (i.e., because you use context in
 * a multi-tenant system). For kotlin coroutine users, this will also detect invalid usage of {@link
 * #makeCurrent()} from coroutines and suspending functions. This detection relies on internal APIs
 * of kotlin coroutines and may not function across all versions - let us know if you find a version
 * of kotlin coroutines where this mechanism does not function.
 *
 * @see StrictContextStorage
 */
public interface Context {

  /** Return the context associated with the current {@link Scope}. */
  static Context current() {
    Context current = ContextStorage.get().current();
    return current != null ? current : root();
  }

  /**
   * Returns the root {@link Context} which all other {@link Context} are derived from.
   *
   * <p>It should generally not be required to use the root {@link Context} directly - instead, use
   * {@link Context#current()} to operate on the current {@link Context}. Only use this method if
   * you are absolutely sure you need to disregard the current {@link Context} - this almost always
   * is only a workaround hiding an underlying context propagation issue.
   */
  static Context root() {
    return ContextStorage.get().root();
  }

  /**
   * Returns an {@link Executor} which delegates to the provided {@code executor}, wrapping all
   * invocations of {@link Executor#execute(Runnable)} with the {@linkplain Context#current()
   * current context} at the time of invocation.
   *
   * <p>This is generally used to create an {@link Executor} which will forward the {@link Context}
   * during an invocation to another thread. For example, you may use something like {@code Executor
   * dbExecutor = Context.wrapTasks(threadPool)} to ensure calls like {@code dbExecutor.execute(()
   * -> database.query())} have {@link Context} available on the thread executing database queries.
   *
   * @since 1.1.0
   */
  static Executor taskWrapping(Executor executor) {
    return command -> executor.execute(Context.current().wrap(command));
  }

  /**
   * Returns an {@link ExecutorService} which delegates to the provided {@code executorService},
   * wrapping all invocations of {@link ExecutorService} methods such as {@link
   * ExecutorService#execute(Runnable)} or {@link ExecutorService#submit(Runnable)} with the
   * {@linkplain Context#current() current context} at the time of invocation.
   *
   * <p>This is generally used to create an {@link ExecutorService} which will forward the {@link
   * Context} during an invocation to another thread. For example, you may use something like {@code
   * ExecutorService dbExecutor = Context.wrapTasks(threadPool)} to ensure calls like {@code
   * dbExecutor.execute(() -> database.query())} have {@link Context} available on the thread
   * executing database queries.
   *
   * @since 1.1.0
   */
  static ExecutorService taskWrapping(ExecutorService executorService) {
    return new CurrentContextExecutorService(executorService);
  }

  /**
   * Returns the value stored in this {@link Context} for the given {@link ContextKey}, or {@code
   * null} if there is no value for the key in this context.
   */
  @Nullable
  <V> V get(ContextKey<V> key);

  /**
   * Returns a new context with the given key value set.
   *
   * <pre>{@code
   * Context withCredential = Context.current().with(CRED_KEY, cred);
   * withCredential.wrap(new Runnable() {
   *   public void run() {
   *      readUserRecords(userId, CRED_KEY.get());
   *   }
   * }).run();
   * }</pre>
   *
   * <p>Note that multiple calls to {@link #with(ContextKey, Object)} can be chained together.
   *
   * <pre>{@code
   * context.with(K1, V1).with(K2, V2);
   * }</pre>
   *
   * <p>Nonetheless, {@link Context} should not be treated like a general purpose map with a large
   * number of keys and values — combine multiple related items together into a single key instead
   * of separating them. But if the items are unrelated, have separate keys for them.
   */
  <V> Context with(ContextKey<V> k1, V v1);

  /** Returns a new {@link Context} with the given {@link ImplicitContextKeyed} set. */
  default Context with(ImplicitContextKeyed value) {
    return value.storeInContext(this);
  }

  /**
   * Makes this the {@linkplain Context#current() current context} and returns a {@link Scope} which
   * corresponds to the scope of execution this context is current for. {@link Context#current()}
   * will return this {@link Context} until {@link Scope#close()} is called. {@link Scope#close()}
   * must be called to properly restore the previous context from before this scope of execution or
   * context will not work correctly. It is recommended to use try-with-resources to call {@link
   * Scope#close()} automatically.
   *
   * <p>The default implementation of this method will store the {@link Context} in a {@link
   * ThreadLocal}. Kotlin coroutine users SHOULD NOT use this method as the {@link ThreadLocal} will
   * not be properly synced across coroutine suspension and resumption. Instead, use {@code
   * withContext(context.asContextElement())} provided by the {@code opentelemetry-extension-kotlin}
   * library.
   *
   * <pre>{@code
   * Context prevCtx = Context.current();
   * try (Scope ignored = ctx.makeCurrent()) {
   *   assert Context.current() == ctx;
   *   ...
   * }
   * assert Context.current() == prevCtx;
   * }</pre>
   */
  @MustBeClosed
  default Scope makeCurrent() {
    return ContextStorage.get().attach(this);
  }

  /**
   * Returns a {@link Runnable} that makes this the {@linkplain Context#current() current context}
   * and then invokes the input {@link Runnable}.
   */
  default Runnable wrap(Runnable runnable) {
    return () -> {
      try (Scope ignored = makeCurrent()) {
        runnable.run();
      }
    };
  }

  /**
   * Returns a {@link Runnable} that makes this the {@linkplain Context#current() current context}
   * and then invokes the input {@link Runnable}.
   */
  default <T> Callable<T> wrap(Callable<T> callable) {
    return () -> {
      try (Scope ignored = makeCurrent()) {
        return callable.call();
      }
    };
  }

  /**
   * Returns an {@link Executor} that will execute callbacks in the given {@code executor}, making
   * this the {@linkplain Context#current() current context} before each execution.
   */
  default Executor wrap(Executor executor) {
    return command -> executor.execute(wrap(command));
  }

  /**
   * Returns an {@link ExecutorService} that will execute callbacks in the given {@code executor},
   * making this the {@linkplain Context#current() current context} before each execution.
   */
  default ExecutorService wrap(ExecutorService executor) {
    return new ContextExecutorService(this, executor);
  }

  /**
   * Returns an {@link ScheduledExecutorService} that will execute callbacks in the given {@code
   * executor}, making this the {@linkplain Context#current() current context} before each
   * execution.
   */
  default ScheduledExecutorService wrap(ScheduledExecutorService executor) {
    return new ContextScheduledExecutorService(this, executor);
  }
}
