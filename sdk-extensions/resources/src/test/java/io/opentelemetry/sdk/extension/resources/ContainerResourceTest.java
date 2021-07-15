/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.resources;

import static io.opentelemetry.sdk.extension.resources.ContainerResource.buildResource;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ContainerResourceTest {

  // Invalid because ID is not a hex string
  private static final String INVALID_CGROUP_LINE_1 =
      "13:name=systemd:/podruntime/docker/kubepods/ac679f8a8319c8cf7d38e1adf263bc08d23zzzz";

  // with suffix
  private static final String CGROUP_LINE_1 =
      "13:name=systemd:/podruntime/docker/kubepods/ac679f8a8319c8cf7d38e1adf263bc08d23.aaaa";
  private static final String EXPECTED_CGROUP_1 = "ac679f8a8319c8cf7d38e1adf263bc08d23";

  // with prefix and suffix
  private static final String CGROUP_LINE_2 =
      "13:name=systemd:/podruntime/docker/kubepods/crio-dc679f8a8319c8cf7d38e1adf263bc08d23.stuff";
  private static final String EXPECTED_CGROUP_2 = "dc679f8a8319c8cf7d38e1adf263bc08d23";

  // just container id
  private static final String CGROUP_LINE_3 =
      "13:name=systemd:/pod/d86d75589bf6cc254f3e2cc29debdf85dde404998aa128997a819ff991827356";
  private static final String EXPECTED_CGROUP_3 =
      "d86d75589bf6cc254f3e2cc29debdf85dde404998aa128997a819ff991827356";

  // with prefix
  private static final String CGROUP_LINE_4 =
      "//\n"
          + "1:name=systemd:/podruntime/docker/kubepods/docker-dc579f8a8319c8cf7d38e1adf263bc08d23"
          + "2:name=systemd:/podruntime/docker/kubepods/docker-dc579f8a8319c8cf7d38e1adf263bc08d23"
          + "3:name=systemd:/podruntime/docker/kubepods/docker-dc579f8a8319c8cf7d38e1adf263bc08d23";

  private static final String EXPECTED_CGROUP_4 = "dc579f8a8319c8cf7d38e1adf263bc08d23";

  @Test
  public void testNegativeCases(@TempDir Path tempFolder) throws IOException {
    // invalid containerId (non-hex)
    Path cgroup = createCGroup(tempFolder.resolve("cgroup1"), INVALID_CGROUP_LINE_1);
    assertThat(buildResource(cgroup)).isEqualTo(Resource.empty());

    // test invalid file
    cgroup = tempFolder.resolve("DoesNotExist");
    assertThat(buildResource(cgroup)).isEqualTo(Resource.empty());
  }

  @Test
  public void testContainer(@TempDir Path tempFolder) throws IOException {
    Path cgroup = createCGroup(tempFolder.resolve("cgroup1"), CGROUP_LINE_1);
    assertThat(getContainerId(buildResource(cgroup))).isEqualTo(EXPECTED_CGROUP_1);

    Path cgroup2 = createCGroup(tempFolder.resolve("cgroup2"), CGROUP_LINE_2);
    assertThat(getContainerId(buildResource(cgroup2))).isEqualTo(EXPECTED_CGROUP_2);

    Path cgroup3 = createCGroup(tempFolder.resolve("cgroup3"), CGROUP_LINE_3);
    assertThat(getContainerId(buildResource(cgroup3))).isEqualTo(EXPECTED_CGROUP_3);

    Path cgroup4 = createCGroup(tempFolder.resolve("cgroup4"), CGROUP_LINE_4);
    assertThat(getContainerId(buildResource(cgroup4))).isEqualTo(EXPECTED_CGROUP_4);
  }

  private static String getContainerId(Resource resource) {
    return resource.getAttributes().get(ResourceAttributes.CONTAINER_ID);
  }

  private static Path createCGroup(Path path, String line) throws IOException {
    return Files.write(path, line.getBytes(StandardCharsets.UTF_8));
  }
}
