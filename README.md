# OpenTelemetry
![Continuous Build](https://github.com/open-telemetry/opentelemetry-java/workflows/Continuous%20Build/badge.svg)
[![Coverage Status][codecov-image]][codecov-url]
[![Maven Central][maven-image]][maven-url]

We hold regular meetings. See details at [community page](https://github.com/open-telemetry/community#java-sdk).

We use [GitHub Discussions](https://github.com/open-telemetry/opentelemetry-java/discussions)
  for support or general questions. Feel free to drop us a line.

## Overview

OpenTelemetry is the merging of OpenCensus and OpenTracing into one
project.

This project contains the following top level components:

* [OpenTelemetry API](api/):
  * [stable apis](api/all/src/main/java/io/opentelemetry/api/) including `Tracer`, `Span`, `SpanContext`, and `Baggage`
  * [semantic conventions](semconv/) Generated code for the OpenTelemetry semantic conventions.
  * [context api](api/context/src/main/java/io/opentelemetry/context/) The OpenTelmetry Context implementation.
  * [metrics api](api/metrics/src/main/java/io/opentelemetry/api/metrics/) alpha code for the metrics API.
* [extensions](extensions/) define additional API extensions, which are not part of the core API.
* [sdk](sdk/) defines the implementation of the OpenTelemetry API.
* [sdk-extensions](sdk-extensions/) define additional SDK extensions, which are not part of the core SDK.
* [OpenTracing shim](opentracing-shim/) defines a bridge layer from OpenTracing to the OpenTelemetry API.
* [examples](examples/) on how to use the APIs, SDK, and standard exporters.

We would love to hear from the larger community: please provide feedback proactively.

## Requirements

Unless otherwise noted, all published artifacts support Java 8 or higher. See [CONTRIBUTING.md](./CONTRIBUTING.md)
for additional instructions for building this project for development.

### Note about extensions

Both API and SDK extensions consist of various additional components which are excluded from the core artifacts
to keep them from growing too large.
We still aim to provide the same level of quality and guarantee for them as for the core components.
Please don't hesitate to use them if you find them useful. 

## Project setup and contribute

Please refer to the [contribution guide](CONTRIBUTING.md)
on how to setup and contribute!

## Quick Start

Please refer to the [quick start guide](QUICKSTART.md) on how use the OpenTelemetry API.

## Published Releases

Published releases are available on maven central. We strongly recommend using our published BOM to keep all
dependency versions in sync.

### Maven

```xml
<project>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-bom</artifactId>
        <version>1.0.0</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-sdk</artifactId>
    </dependency>
  </dependencies>
</project>
```

### Gradle

```groovy
dependencies {
  implementation platform("io.opentelemetry:opentelemetry-bom:1.0.0")
  implementation('io.opentelemetry:opentelemetry-sdk')
}
```

## Snapshots

Snapshots based out the `main` branch are available for `opentelemetry-api`, `opentelemetry-sdk` and the rest of the artifacts.
We strongly recommend using our published BOM to keep all dependency versions in sync.

### Maven

```xml
<project>
  <repositories>
    <repository>
      <id>oss.sonatype.org-snapshot</id>
      <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    </repository>
  </repositories>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-bom</artifactId>
        <version>1.1.0-SNAPSHOT</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-sdk</artifactId>
    </dependency>
  </dependencies>
</project>
```

### Gradle

```groovy
repositories {
	maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }
}

dependencies {
  implementation platform("io.opentelemetry:opentelemetry-bom:1.1.0-SNAPSHOT")
  implementation('io.opentelemetry:opentelemetry-sdk')
}
```

Libraries will usually only need `opentelemetry-api`, so should not depend on `opentelemetry-sdk`.

## Releases

OpenTelemetry Java is under active development. Releases aren't guaranteed
to conform to a specific version of the specification. Future releases will
not attempt to maintain backwards compatibility with previous releases.

Check out information about the [latest release](https://github.com/open-telemetry/opentelemetry-java/releases).

This is a **current** feature status list:

| Component                   | Version |
| --------------------------- | ------- |
| Trace API                   | v<!--VERSION_STABLE-->1.0.0<!--/VERSION_STABLE-->  |
| Trace SDK                   | v<!--VERSION_STABLE-->1.0.0<!--/VERSION_STABLE-->  |
| Context                     | v<!--VERSION_STABLE-->1.0.0<!--/VERSION_STABLE-->  |
| Baggage                     | v<!--VERSION_STABLE-->1.0.0<!--/VERSION_STABLE-->  |
| Jaeger Trace Exporter       | v<!--VERSION_STABLE-->1.0.0<!--/VERSION_STABLE-->  |
| Zipkin Trace Exporter       | v<!--VERSION_STABLE-->1.0.0<!--/VERSION_STABLE-->  |
| OTLP Exporter (Spans)       | v<!--VERSION_STABLE-->1.0.0<!--/VERSION_STABLE-->  |
| OTLP Exporter (Metrics)     | v<!--VERSION_UNSTABLE-->1.0.0-alpha<!--/VERSION_UNSTABLE-->  |
| Metrics API                 | v<!--VERSION_UNSTABLE-->1.0.0-alpha<!--/VERSION_UNSTABLE-->  |
| Metrics SDK                 | v<!--VERSION_UNSTABLE-->1.0.0-alpha<!--/VERSION_UNSTABLE-->  |
| Prometheus Metrics Exporter | v<!--VERSION_UNSTABLE-->1.0.0-alpha<!--/VERSION_UNSTABLE-->  |
| OpenTracing Bridge          | v<!--VERSION_UNSTABLE-->1.0.0-alpha<!--/VERSION_UNSTABLE-->  |
| OpenCensus Bridge           | v<!--VERSION_UNSTABLE-->1.0.0-alpha<!--/VERSION_UNSTABLE-->  |

See the project [milestones](https://github.com/open-telemetry/opentelemetry-java/milestones)
for details on upcoming releases. The dates and features described in issues
and milestones are estimates, and subject to change.

### Summary

We plan to merge projects and pave the path for future improvements as a unified
community of tracing vendors, users and library authors who wants apps be
managed better. We are open to feedback and suggestions from all of you!

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

Approvers ([@open-telemetry/java-approvers](https://github.com/orgs/open-telemetry/teams/java-approvers)):

- [Armin Ruech](https://github.com/arminru), Dynatrace
- [Pavol Loffay](https://github.com/pavolloffay), Traceable.ai
- [Tyler Benson](https://github.com/tylerbenson), DataDog
- [Giovanni Liva](https://github.com/thisthat), Dynatrace
- [Christian Neumüller](https://github.com/Oberon00), Dynatrace
- [Carlos Alberto](https://github.com/carlosalberto), LightStep

*Find more about the approver role in [community repository](https://github.com/open-telemetry/community/blob/master/community-membership.md#approver).*

Maintainers ([@open-telemetry/java-maintainers](https://github.com/orgs/open-telemetry/teams/java-maintainers)):

- [Bogdan Drutu](https://github.com/BogdanDrutu), Splunk
- [John Watson](https://github.com/jkwatson), Splunk
- [Anuraag Agrawal](https://github.com/anuraaga), AWS

*Find more about the maintainer role in [community repository](https://github.com/open-telemetry/community/blob/master/community-membership.md#maintainer).*

### Thanks to all the people who have contributed

[![contributors](https://contributors-img.web.app/image?repo=open-telemetry/opentelemetry-java)](https://github.com/open-telemetry/opentelemetry-java/graphs/contributors)

[circleci-image]: https://circleci.com/gh/open-telemetry/opentelemetry-java.svg?style=svg 
[circleci-url]: https://circleci.com/gh/open-telemetry/opentelemetry-java
[codecov-image]: https://codecov.io/gh/open-telemetry/opentelemetry-java/branch/main/graph/badge.svg
[codecov-url]: https://codecov.io/gh/open-telemetry/opentelemetry-java/branch/main/
[maven-image]: https://maven-badges.herokuapp.com/maven-central/io.opentelemetry/opentelemetry-api/badge.svg
[maven-url]: https://maven-badges.herokuapp.com/maven-central/io.opentelemetry/opentelemetry-api
