plugins {
    `java-library`
    `maven-publish`

    id("ru.vyarus.animalsniffer")
}

description = "OpenTelemetry SDK Resource Providers"
extra["moduleName"] = "io.opentelemetry.sdk.extension.resources"

val mrJarVersions = listOf(11)

dependencies {
    api(project(":sdk:common"))
    
    implementation(project(":semconv"))

    compileOnly(project(":sdk-extensions:autoconfigure-spi"))

    compileOnly("org.codehaus.mojo:animal-sniffer-annotations")

    testImplementation("org.junit-pioneer:junit-pioneer")
}

sourceSets {
    main {
        output.dir("build/generated/properties", "builtBy" to "generateVersionResource")
    }
}

tasks {
    register("generateVersionResource") {
        val propertiesDir = file("build/generated/properties/io/opentelemetry/sdk/extension/resources")
        outputs.dir(propertiesDir)

        doLast {
            File(propertiesDir, "version.properties").writeText("sdk.version=${project.version}")
        }
    }
}

for (version in mrJarVersions) {
    sourceSets {
        create("java${version}") {
            java {
                setSrcDirs(listOf("src/main/java${version}"))
            }
        }
    }

    tasks {
        named<JavaCompile>("compileJava${version}Java") {
            sourceCompatibility = "${version}"
            targetCompatibility = "${version}"
            options.release.set(version)
        }
    }

    configurations {
        named("java${version}Implementation") {
            extendsFrom(configurations["implementation"])
        }
        named("java${version}CompileOnly") {
            extendsFrom(configurations["compileOnly"])
        }
    }

    dependencies {
        // Common to reference classes in main sourceset from Java 9 one (e.g., to return a common interface)
        add("java${version}Implementation", files(sourceSets.main.get().output.classesDirs))
    }
}

tasks {
    withType(Jar::class) {
        for (version in mrJarVersions) {
            into("META-INF/versions/${version}") {
                from(sourceSets["java${version}"].output)
            }
        }
        manifest.attributes(
                "Multi-Release" to "true"
        )
    }
}
