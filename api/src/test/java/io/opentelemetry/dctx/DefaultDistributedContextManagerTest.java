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

package io.opentelemetry.dctx;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.dctx.unsafe.ContextUtils;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DefaultDistributedContextManager}. */
@RunWith(JUnit4.class)
public final class DefaultDistributedContextManagerTest {
  private static final DistributedContextManager defaultDistributedContextManager =
      DefaultDistributedContextManager.getInstance();
  private static final AttributeKey KEY = AttributeKey.create("key");
  private static final AttributeValue VALUE = AttributeValue.create("value");

  private static final DistributedContext DIST_CONTEXT =
      new DistributedContext() {

        @Override
        public Iterator<Attribute> getIterator() {
          return Arrays.asList(
                  Attribute.create(KEY, VALUE, Attribute.METADATA_UNLIMITED_PROPAGATION))
              .iterator();
        }

        @Nullable
        @Override
        public AttributeValue getAttributeValue(AttributeKey attrKey) {
          return VALUE;
        }
      };

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void builderMethod() {
    assertThat(asList(defaultDistributedContextManager.contextBuilder().build())).isEmpty();
  }

  @Test
  public void getCurrentContext_DefaultContext() {
    assertThat(defaultDistributedContextManager.getCurrentContext())
        .isSameInstanceAs(EmptyDistributedContext.INSTANCE);
  }

  @Test
  public void getCurrentContext_ContextSetToNull() {
    Context orig = ContextUtils.withValue(null).attach();
    try {
      DistributedContext distContext = defaultDistributedContextManager.getCurrentContext();
      assertThat(distContext).isNotNull();
      assertThat(distContext.getIterator().hasNext()).isFalse();
    } finally {
      Context.current().detach(orig);
    }
  }

  @Test
  public void withContext() {
    assertThat(defaultDistributedContextManager.getCurrentContext())
        .isSameInstanceAs(EmptyDistributedContext.INSTANCE);
    Scope wtm = defaultDistributedContextManager.withContext(DIST_CONTEXT);
    try {
      assertThat(defaultDistributedContextManager.getCurrentContext())
          .isSameInstanceAs(DIST_CONTEXT);
    } finally {
      wtm.close();
    }
    assertThat(defaultDistributedContextManager.getCurrentContext())
        .isSameInstanceAs(EmptyDistributedContext.INSTANCE);
  }

  @Test
  public void withContext_nullDistributedContex() {
    assertThat(defaultDistributedContextManager.getCurrentContext())
        .isSameInstanceAs(EmptyDistributedContext.INSTANCE);
    Scope wtm = defaultDistributedContextManager.withContext(null);
    try {
      assertThat(defaultDistributedContextManager.getCurrentContext())
          .isSameInstanceAs(EmptyDistributedContext.INSTANCE);
    } finally {
      wtm.close();
    }
    assertThat(defaultDistributedContextManager.getCurrentContext())
        .isSameInstanceAs(EmptyDistributedContext.INSTANCE);
  }

  @Test
  public void withContextUsingWrap() {
    Runnable runnable;
    Scope wtm = defaultDistributedContextManager.withContext(DIST_CONTEXT);
    try {
      assertThat(defaultDistributedContextManager.getCurrentContext())
          .isSameInstanceAs(DIST_CONTEXT);
      runnable =
          Context.current()
              .wrap(
                  new Runnable() {
                    @Override
                    public void run() {
                      assertThat(defaultDistributedContextManager.getCurrentContext())
                          .isSameInstanceAs(DIST_CONTEXT);
                    }
                  });
    } finally {
      wtm.close();
    }
    assertThat(defaultDistributedContextManager.getCurrentContext())
        .isSameInstanceAs(EmptyDistributedContext.INSTANCE);
    // When we run the runnable we will have the DistributedContext in the current Context.
    runnable.run();
  }

  @Test
  public void noopDistributedContexBuilder_SetParent_DisallowsNullKey() {
    DistributedContext.Builder noopBuilder = defaultDistributedContextManager.contextBuilder();
    thrown.expect(NullPointerException.class);
    noopBuilder.setParent(null);
  }

  @Test
  public void noopDistributedContexBuilder_Put_DisallowsNullKey() {
    DistributedContext.Builder noopBuilder = defaultDistributedContextManager.contextBuilder();
    thrown.expect(NullPointerException.class);
    noopBuilder.put(null, VALUE, Attribute.METADATA_UNLIMITED_PROPAGATION);
  }

  @Test
  public void noopDistributedContexBuilder_Put_DisallowsNullValue() {
    DistributedContext.Builder noopBuilder = defaultDistributedContextManager.contextBuilder();
    thrown.expect(NullPointerException.class);
    noopBuilder.put(KEY, null, Attribute.METADATA_UNLIMITED_PROPAGATION);
  }

  @Test
  public void noopDistributedContexBuilder_Put_DisallowsNullAttributeMetadata() {
    DistributedContext.Builder noopBuilder = defaultDistributedContextManager.contextBuilder();
    thrown.expect(NullPointerException.class);
    noopBuilder.put(KEY, VALUE, null);
  }

  @Test
  public void noopDistributedContexBuilder_Remove_DisallowsNullKey() {
    DistributedContext.Builder noopBuilder = defaultDistributedContextManager.contextBuilder();
    thrown.expect(NullPointerException.class);
    noopBuilder.remove(null);
  }

  private static List<Attribute> asList(DistributedContext distContext) {
    return Lists.newArrayList(distContext.getIterator());
  }
}
