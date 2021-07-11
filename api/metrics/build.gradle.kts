plugins {
    id("otel.java-conventions")
    id("otel.publish-conventions")

    id("otel.jmh-conventions")
    id("otel.animalsniffer-conventions")
}

description = "OpenTelemetry API"
otelJava.moduleName.set("io.opentelemetry.api.metrics")

dependencies {
    api(project(":api:all"))
    api(project(":context"))

    annotationProcessor("com.google.auto.value:auto-value")

    testImplementation("edu.berkeley.cs.jqf:jqf-fuzz")
    testImplementation("com.google.guava:guava-testlib")
}
