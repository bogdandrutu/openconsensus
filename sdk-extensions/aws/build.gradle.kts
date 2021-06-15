plugins {
    `java-library`
    `maven-publish`

    id("me.champeau.jmh")
}

description = "OpenTelemetry SDK AWS Instrumentation Support"
extra["moduleName"] = "io.opentelemetry.sdk.extension.trace.aws"

dependencies {
    api(project(":api:all"))
    api(project(":sdk:all"))

    compileOnly(project(":sdk-extensions:autoconfigure-spi"))

    implementation(project(":semconv"))

    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation(project(":sdk-extensions:autoconfigure"))

    testImplementation("com.linecorp.armeria:armeria-junit5")
    testImplementation("com.google.guava:guava")
}
