pluginManagement {
    plugins {
        id("com.diffplug.spotless") version "5.12.5"
        id("com.github.ben-manes.versions") version "0.39.0"
        id("com.github.johnrengelman.shadow") version "7.0.0"
        id("com.google.protobuf") version "0.8.16"
        id("com.gradle.enterprise") version "3.6"
        id("de.marcphilipp.nexus-publish") version "0.4.0"
        id("de.undercouch.download") version "4.1.1"
        id("io.codearte.nexus-staging") version "0.30.0"
        id("io.morethan.jmhreport") version "0.9.0"
        id("me.champeau.jmh") version "0.6.5"
        id("nebula.release") version "15.3.1"
        id("net.ltgt.errorprone") version "2.0.1"
        id("net.ltgt.nullaway") version "1.1.0"
        id("org.checkerframework") version "0.5.20"
        id("org.jetbrains.kotlin.jvm") version "1.5.10"
        id("org.unbroken-dome.test-sets") version "4.0.0"
        id("ru.vyarus.animalsniffer") version "1.5.3"
        id("me.champeau.gradle.japicmp") version "0.2.9"
    }
}

plugins {
    id("com.gradle.enterprise")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "opentelemetry-java"
include(":all")
include(":api:all")
include(":api:metrics")
include(":semconv")
include(":bom")
include(":bom-alpha")
include(":context")
include(":dependencyManagement")
include(":extensions:annotations")
include(":extensions:incubator")
include(":extensions:aws")
include(":extensions:kotlin")
include(":extensions:noop-api")
include(":extensions:trace-propagators")
include(":exporters:jaeger")
include(":exporters:jaeger-thrift")
include(":exporters:logging")
include(":exporters:logging-otlp")
include(":exporters:otlp:all")
include(":exporters:otlp:common")
include(":exporters:otlp:metrics")
include(":exporters:otlp:trace")
include(":exporters:prometheus")
include(":exporters:zipkin")
include(":integration-tests")
include(":integration-tests:tracecontext")
include(":opencensus-shim")
include(":opentracing-shim")
include(":perf-harness")
include(":proto")
include(":sdk:all")
include(":sdk:common")
include(":sdk:metrics")
include(":sdk:testing")
include(":sdk:trace")
include(":sdk:trace-shaded-deps")
include(":sdk-extensions:async-processor")
include(":sdk-extensions:autoconfigure")
include(":sdk-extensions:autoconfigure-spi")
include(":sdk-extensions:aws")
include(":sdk-extensions:logging")
include(":sdk-extensions:resources")
include(":sdk-extensions:tracing-incubator")
include(":sdk-extensions:jaeger-remote-sampler")
include(":sdk-extensions:jfr-events")
include(":sdk-extensions:zpages")

val isCI = System.getenv("CI") != null
gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"

        if (isCI) {
            publishAlways()
            tag("CI")
        }
    }
}
