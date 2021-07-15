/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.resources;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.internal.OtelEncodingUtils;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

/** Factory for {@link Resource} retrieving Container ID information. */
public final class ContainerResource {

  private static final Logger logger = Logger.getLogger(ContainerResource.class.getName());
  private static final String UNIQUE_HOST_NAME_FILE_NAME = "/proc/self/cgroup";
  private static final Resource INSTANCE = buildSingleton(UNIQUE_HOST_NAME_FILE_NAME);

  @IgnoreJRERequirement
  private static Resource buildSingleton(String uniqueHostNameFileName) {
    // can't initialize this statically without running afoul of animalSniffer on paths
    return buildResource(Paths.get(uniqueHostNameFileName));
  }

  // package private for testing
  static Resource buildResource(Path path) {
    String containerId = extractContainerId(path);

    if (containerId == null || containerId.isEmpty()) {
      return Resource.empty();
    } else {
      return Resource.create(Attributes.of(ResourceAttributes.CONTAINER_ID, containerId));
    }
  }

  /** Returns resource with container information. */
  public static Resource get() {
    return INSTANCE;
  }

  /**
   * Each line of cgroup file looks like "14:name=systemd:/docker/.../... A hex string is expected
   * inside the last section separated by '/' Each segment of the '/' can contain metadata separated
   * by either '.' (at beginning) or '-' (at end)
   *
   * @return containerId
   */
  @IgnoreJRERequirement
  @Nullable
  private static String extractContainerId(Path cgroupFilePath) {
    if (!Files.exists(cgroupFilePath) || !Files.isReadable(cgroupFilePath)) {
      return null;
    }
    try (Stream<String> lines = Files.lines(cgroupFilePath)) {
      Optional<String> value =
          lines
              .filter(line -> !line.isEmpty())
              .map(line -> getIdFromLine(line))
              .filter(Objects::nonNull)
              .findFirst();
      if (value.isPresent()) {
        return value.get();
      }
    } catch (IOException e) {
      logger.log(Level.WARNING, "Unable to read file: " + e.getMessage());
    }
    return null;
  }

  private static String getIdFromLine(String line) {
    // This cgroup output line should have the container id in it
    int lastSlashIdx = line.lastIndexOf("/");
    if (lastSlashIdx < 0) {
      return null;
    }

    String lastSection = line.substring(lastSlashIdx + 1);
    int startIdx = lastSection.indexOf("-");
    int endIdx = lastSection.lastIndexOf(".");

    String containerId =
        lastSection.substring(
            startIdx == -1 ? 0 : startIdx + 1, endIdx == -1 ? lastSection.length() : endIdx);
    if (OtelEncodingUtils.isValidBase16String(containerId) && !containerId.isEmpty()) {
      return containerId;
    } else {
      return null;
    }
  }

  private ContainerResource() {}
}
