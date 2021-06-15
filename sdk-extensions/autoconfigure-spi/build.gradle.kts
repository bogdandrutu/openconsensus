plugins {
    `java-library`
    `maven-publish`
}

description = "OpenTelemetry SDK Auto-configuration SPI"
extra["moduleName"] = "io.opentelemetry.sdk.autoconfigure.spi"

dependencies {
    api(project(":sdk:all"))
    api(project(":sdk:metrics"))
}
