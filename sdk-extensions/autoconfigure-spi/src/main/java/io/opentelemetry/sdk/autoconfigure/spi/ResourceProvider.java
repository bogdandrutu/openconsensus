/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure.spi;

import io.opentelemetry.sdk.resources.Resource;

/**
 * A service provider interface (SPI) for providing a {@link Resource} that is merged into the
 * {@linkplain Resource#getDefault() default resource}.
 */
public interface ResourceProvider {

  Resource createResource(ConfigProperties config);
}
