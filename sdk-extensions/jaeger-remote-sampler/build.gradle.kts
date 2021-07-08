plugins {
    id("otel.protobuf-conventions")
    id("otel.publish-conventions")

    id("otel.animalsniffer-conventions")
}

description = "OpenTelemetry - Jaeger Remote sampler"
otelJava.moduleName.set("io.opentelemetry.sdk.extension.trace.jaeger")

dependencies {
    api(project(":sdk:all"))
    compileOnly(project(":sdk-extensions:autoconfigure"))

    implementation(project(":sdk:all"))
    implementation("io.grpc:grpc-api")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")
    implementation("com.google.protobuf:protobuf-java")

    testImplementation(project(":sdk-extensions:autoconfigure"))
    testImplementation("io.grpc:grpc-testing")
    testImplementation("org.testcontainers:junit-jupiter")

    testRuntimeOnly("io.grpc:grpc-netty-shaded")
}
