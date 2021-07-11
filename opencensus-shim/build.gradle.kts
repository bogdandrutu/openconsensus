import org.gradle.api.plugins.JavaPlugin.*

plugins {
    id("otel.java-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry OpenCensus Shim"
otelJava.moduleName.set("io.opentelemetry.opencensusshim")

dependencies {
    api(project(":api:all"))
    api(project(":extensions:trace-propagators"))
    api(project(":sdk:all"))
    api(project(":sdk:metrics"))

    api("io.opencensus:opencensus-api")
    api("io.opencensus:opencensus-impl-core")
    api("io.opencensus:opencensus-exporter-metrics-util")

    testImplementation(project(":sdk:all"))
    testImplementation(project(":sdk:metrics-testing"))

    testImplementation("org.slf4j:slf4j-simple")
    testImplementation("io.opencensus:opencensus-impl")
}

tasks {
    withType(JavaCompile::class) {
        // Ignore usage of deprecated Summary metric type.
        options.compilerArgs.add("-Xlint:-deprecation")
    }
}