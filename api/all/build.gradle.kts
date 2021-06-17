plugins {
    id("otel.java-conventions")
    id("maven-publish")

    id("me.champeau.jmh")
    id("org.unbroken-dome.test-sets")
    id("ru.vyarus.animalsniffer")
}

description = "OpenTelemetry API"
otelJava.moduleName.set("io.opentelemetry.api")
base.archivesBaseName = "opentelemetry-api"

testSets {
    create("testLogsIfSdkFound")
    create("testDoesNotLogIfSdkFoundAndSuppressed")
}

dependencies {
    api(project(":context"))

    annotationProcessor("com.google.auto.value:auto-value")

    testImplementation("edu.berkeley.cs.jqf:jqf-fuzz")
    testImplementation("com.google.guava:guava-testlib")

    add("testLogsIfSdkFoundImplementation", project(":sdk:all"))
    add("testDoesNotLogIfSdkFoundAndSuppressedImplementation", project(":sdk:all"))
}

tasks {
    val testLogsIfSdkFound by existing(Test::class) {
    }

    val testDoesNotLogIfSdkFoundAndSuppressed by existing(Test::class) {
        jvmArgs("-Dotel.sdk.suppress-sdk-initialized-warning=true")
    }

    named("check") {
        dependsOn(testLogsIfSdkFound, testDoesNotLogIfSdkFoundAndSuppressed)
    }
}