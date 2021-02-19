plugins {
    `java-library`
    `maven-publish`

    id("me.champeau.gradle.jmh")
    id("org.unbroken-dome.test-sets")
    id("ru.vyarus.animalsniffer")
}

description = "OpenTelemetry Protocol Trace Exporter"
extra["moduleName"] = "io.opentelemetry.exporter.otlp.trace"

testSets {
    create("testGrpcNetty")
    create("testGrpcNettyShaded")
    create("testGrpcOkhttp")
}

dependencies {
    api(project(":sdk:trace"))

    compileOnly("io.grpc:grpc-netty")
    compileOnly("io.grpc:grpc-netty-shaded")

    implementation(project(":api:metrics"))
    implementation(project(":proto"))
    implementation("io.grpc:grpc-api")
    implementation("io.grpc:grpc-core")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")
    implementation("com.google.protobuf:protobuf-java")

    testImplementation(project(":sdk-extensions:otproto"))
    testImplementation(project(":sdk:testing"))

    testImplementation("io.grpc:grpc-testing")
    testImplementation("org.slf4j:slf4j-simple")

    add("testGrpcNettyImplementation", "com.linecorp.armeria:armeria-grpc")
    add("testGrpcNettyImplementation", "com.linecorp.armeria:armeria-junit5")

    add("testGrpcNettyShadedImplementation", "com.linecorp.armeria:armeria-grpc")
    add("testGrpcNettyShadedImplementation", "com.linecorp.armeria:armeria-junit5")

    add("testGrpcOkhttpImplementation", "com.linecorp.armeria:armeria-grpc")
    add("testGrpcOkhttpImplementation", "com.linecorp.armeria:armeria-junit5")

    add("testGrpcNettyRuntimeOnly", "io.grpc:grpc-netty")

    add("testGrpcNettyShadedRuntimeOnly", "io.grpc:grpc-netty-shaded")

    add("testGrpcOkhttpRuntimeOnly", "io.grpc:grpc-okhttp")

    jmh(project(":sdk:testing")) {
        isTransitive = false
    }
    jmh(project(":sdk-extensions:otproto")) {
        isTransitive = false
    }
}

tasks {
    named("check") {
        dependsOn("testGrpcNetty", "testGrpcNettyShaded", "testGrpcOkhttp")
    }
}
