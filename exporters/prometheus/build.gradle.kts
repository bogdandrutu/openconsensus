import org.gradle.api.plugins.JavaPlugin.*

plugins {
    id("otel.java-conventions")
    id("otel.publish-conventions")

    id("otel.animalsniffer-conventions")
}

description = "OpenTelemetry Prometheus Exporter"
otelJava.moduleName.set("io.opentelemetry.exporter.prometheus")

dependencies {
    api(project(":sdk:metrics"))

    api("io.prometheus:simpleclient")

    testImplementation("io.prometheus:simpleclient_common")
    testImplementation("com.google.guava:guava")
}

tasks {
    withType(JavaCompile::class) {
        // Prometheus exporter still used deprecated Metrics SDK.
        // TODO (issue?): Migrate to new SDK.
        options.compilerArgs.add("-Xlint:-deprecation")
    }
}
