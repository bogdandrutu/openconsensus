/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.aws.resource;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/** {@link ResourceProvider} for automatically configuring {@link BeanstalkResource}. */
public final class BeanstalkResourceProvider implements ResourceProvider {
  @Override
  public Resource createResource(ConfigProperties config) {
    return BeanstalkResource.get();
  }
}
