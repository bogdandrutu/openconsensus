import com.diffplug.gradle.spotless.SpotlessExtension
import com.google.protobuf.gradle.*
import de.marcphilipp.gradle.nexus.NexusPublishExtension
import io.morethan.jmhreport.gradle.JmhReportExtension
import me.champeau.jmh.JmhParameters
import me.champeau.gradle.japicmp.JapicmpTask
import nebula.plugin.release.git.opinion.Strategies
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.ErrorPronePlugin
import net.ltgt.gradle.nullaway.NullAwayOptions
import org.gradle.api.plugins.JavaPlugin.*
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferPlugin
import java.time.Duration

plugins {
    id("com.diffplug.spotless")
    id("com.github.ben-manes.versions")
    id("io.codearte.nexus-staging")
    id("nebula.release")

    id("com.google.protobuf") apply false
    id("de.marcphilipp.nexus-publish") apply false
    id("io.morethan.jmhreport") apply false
    id("me.champeau.jmh") apply false
    id("net.ltgt.errorprone") apply false
    id("net.ltgt.nullaway") apply false
    id("ru.vyarus.animalsniffer") apply false
    id("me.champeau.gradle.japicmp") apply false
}


/**
 * Locate the project's artifact of a particular version.
 */
fun Project.findArtifact(version: String) : File {
    val existingGroup = this.group
    try {
        // Temporarily change the group name because we want to fetch an artifact with the same
        // Maven coordinates as the project, which Gradle would not allow otherwise.
        this.group = "virtual_group"
        val depModule = "io.opentelemetry:${base.archivesBaseName}:$version@jar"
        val depJar = "${base.archivesBaseName}-${version}.jar"
        val configuration: Configuration = configurations.detachedConfiguration(
                dependencies.create(depModule)
        )
        return files(configuration.files).filter {
            it.name.equals(depJar)
        }.singleFile
    } finally {
        this.group = existingGroup
    }
}

/**
 * The latest *released* version of the project. Evaluated lazily so the work is only done if necessary.
 */
val latestReleasedVersion : String by lazy {
    // hack to find the current released version of the project
    val temp: Configuration = project.configurations.create("tempConfig")
    // pick the api, since it's always there.
    dependencies.add("tempConfig", "io.opentelemetry:opentelemetry-api:latest.release")
    val moduleVersion = project.configurations["tempConfig"].resolvedConfiguration.firstLevelModuleDependencies.elementAt(0).moduleVersion
    project.configurations.remove(temp)
    println("Discovered latest release version: " + moduleVersion)
    moduleVersion
}

if (!JavaVersion.current().isJava11Compatible()) {
    throw GradleException("JDK 11 or higher is required to build. " +
            "One option is to download it from https://adoptopenjdk.net/. If you believe you already " +
            "have it, please check that the JAVA_HOME environment variable is pointing at the " +
            "JDK 11 installation.")
}

// Nebula plugin will not configure if .git doesn't exist, let's allow building on it by stubbing it
// out. This supports building from the zip archive downloaded from GitHub.
var releaseTask: TaskProvider<Task>
if (file(".git").exists()) {
    release {
        defaultVersionStrategy = Strategies.getSNAPSHOT()
    }

    nebulaRelease {
        addReleaseBranchPattern("""v\d+\.\d+\.x""")
    }

    releaseTask = tasks.named("release")
    releaseTask.configure {
        mustRunAfter("snapshotSetup", "finalSetup")
    }
} else {
    releaseTask = tasks.register("release")
}

nexusStaging {
    packageGroup = "io.opentelemetry"
    username = System.getenv("SONATYPE_USER")
    password = System.getenv("SONATYPE_KEY")

    // We have many artifacts so Maven Central takes a long time on its compliance checks. This sets
    // the timeout for waiting for the repository to close to a comfortable 50 minutes.
    numberOfRetries = 300
    delayBetweenRetriesInMillis = 10000
}

val enableNullaway: String? by project

subprojects {
    group = "io.opentelemetry"

    plugins.withId("java") {
        plugins.apply("checkstyle")
        plugins.apply("eclipse")
        plugins.apply("idea")
        plugins.apply("jacoco")

        plugins.apply("com.diffplug.spotless")
        plugins.apply("net.ltgt.errorprone")
        plugins.apply("net.ltgt.nullaway")

        configure<BasePluginConvention> {
            archivesBaseName = "opentelemetry-${name}"
        }

        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(11))
            }

            withJavadocJar()
            withSourcesJar()
        }

        configure<CheckstyleExtension> {
            configDirectory.set(file("$rootDir/buildscripts/"))
            toolVersion = "8.12"
            isIgnoreFailures = false
            configProperties["rootDir"] = rootDir
        }

        configure<JacocoPluginExtension> {
            toolVersion = "0.8.7"
        }

        val javaToolchains = the<JavaToolchainService>()

        tasks {
            val testJava8 by registering(Test::class) {
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(8))
                })

                configure<JacocoTaskExtension> {
                    enabled = false
                }
            }

            val testAdditionalJavaVersions: String? by rootProject
            if (testAdditionalJavaVersions == "true") {
                named("check") {
                    dependsOn(testJava8)
                }
            }

            withType(JavaCompile::class) {
                options.release.set(8)

                if (name != "jmhCompileGeneratedClasses") {
                    options.compilerArgs.addAll(listOf(
                            "-Xlint:all",
                            // We suppress the "try" warning because it disallows managing an auto-closeable with
                            // try-with-resources without referencing the auto-closeable within the try block.
                            "-Xlint:-try",
                            // We suppress the "processing" warning as suggested in
                            // https://groups.google.com/forum/#!topic/bazel-discuss/_R3A9TJSoPM
                            "-Xlint:-processing",
                            // We suppress the "options" warning because it prevents compilation on modern JDKs
                            "-Xlint:-options",

                            // Fail build on any warning
                            "-Werror"
                    ))
                }
                //disable deprecation warnings for the protobuf module
                if (project.name == "proto") {
                    options.compilerArgs.add("-Xlint:-deprecation")
                }

                options.encoding = "UTF-8"

                if (name.contains("Test")) {
                    // serialVersionUID is basically guaranteed to be useless in tests
                    options.compilerArgs.add("-Xlint:-serial")
                }

                (options as ExtensionAware).extensions.configure<ErrorProneOptions> {
                    disableWarningsInGeneratedCode.set(true)
                    allDisabledChecksAsWarnings.set(true)

                    (this as ExtensionAware).extensions.configure<NullAwayOptions> {
                        // Enable nullaway on main sources.
                        // TODO(anuraaga): Remove enableNullaway flag when all errors fixed
                        if (!name.contains("Test") && !name.contains("Jmh") && enableNullaway == "true") {
                            severity.set(CheckSeverity.ERROR)
                        } else {
                            severity.set(CheckSeverity.OFF)
                        }
                        annotatedPackages.add("io.opentelemetry")
                    }

                    // Doesn't currently use Var annotations.
                    disable("Var") // "-Xep:Var:OFF"

                    // ImmutableRefactoring suggests using com.google.errorprone.annotations.Immutable,
                    // but currently uses javax.annotation.concurrent.Immutable
                    disable("ImmutableRefactoring") // "-Xep:ImmutableRefactoring:OFF"

                    // AutoValueImmutableFields suggests returning Guava types from API methods
                    disable("AutoValueImmutableFields")
                    // Suggests using Guava types for fields but we don't use Guava
                    disable("ImmutableMemberCollection")
                    // "-Xep:AutoValueImmutableFields:OFF"

                    // Fully qualified names may be necessary when deprecating a class to avoid
                    // deprecation warning.
                    disable("UnnecessarilyFullyQualified")

                    // Ignore warnings for protobuf and jmh generated files.
                    excludedPaths.set(".*generated.*|.*internal.shaded.*")
                    // "-XepExcludedPaths:.*/build/generated/source/proto/.*"

                    disable("Java7ApiChecker")
                    disable("AndroidJdkLibsChecker")
                    //apparently disabling android doesn't disable this
                    disable("StaticOrDefaultInterfaceMethod")

                    //until we have everything converted, we need these
                    disable("JdkObsolete")
                    disable("UnnecessaryAnonymousClass")

                    // Limits APIs
                    disable("NoFunctionalReturnType")

                    // We don't depend on Guava so use normal splitting
                    disable("StringSplitter")

                    // Prevents lazy initialization
                    disable("InitializeInline")

                    if (name.contains("Jmh") || name.contains("Test")) {
                        // Allow underscore in test-type method names
                        disable("MemberName")
                    }
                }
            }

            withType(Test::class) {
                useJUnitPlatform()

                testLogging {
                    exceptionFormat = TestExceptionFormat.FULL
                    showExceptions = true
                    showCauses = true
                    showStackTraces = true
                }
                maxHeapSize = "1500m"
            }

            withType(Javadoc::class) {
                exclude("io/opentelemetry/**/internal/**")

                with(options as StandardJavadocDocletOptions) {
                    source = "8"
                    encoding = "UTF-8"
                    docEncoding = "UTF-8"
                    breakIterator(true)

                    addBooleanOption("html5", true)

                    links("https://docs.oracle.com/javase/8/docs/api/")
                    addBooleanOption("Xdoclint:all,-missing", true)

                    afterEvaluate {
                        val title = "${project.description}"
                        docTitle = title
                        windowTitle = title
                    }
                }
            }

            afterEvaluate {
                withType(Jar::class) {
                    val moduleName: String by project
                    inputs.property("moduleName", moduleName)

                    manifest {
                        attributes(
                                "Automatic-Module-Name" to moduleName,
                                "Built-By" to System.getProperty("user.name"),
                                "Built-JDK" to System.getProperty("java.version"),
                                "Implementation-Title" to project.name,
                                "Implementation-Version" to project.version)
                    }
                }
            }
        }

        // https://docs.gradle.org/current/samples/sample_jvm_multi_project_with_code_coverage.html

        // Do not generate reports for individual projects
        tasks.named("jacocoTestReport") {
            enabled = false
        }

        configurations {
            val implementation by getting

            create("transitiveSourceElements") {
                isVisible = false
                isCanBeResolved = false
                isCanBeConsumed = true
                extendsFrom(implementation)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("source-folders"))
                }
                val mainSources = the<JavaPluginConvention>().sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                mainSources.java.srcDirs.forEach {
                    outgoing.artifact(it)
                }
            }

            create("coverageDataElements") {
                isVisible = false
                isCanBeResolved = false
                isCanBeConsumed = true
                extendsFrom(implementation)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("jacoco-coverage-data"))
                }
                // This will cause the test task to run if the coverage data is requested by the aggregation task
                tasks.withType(Test::class) {
                    outgoing.artifact(extensions.getByType<JacocoTaskExtension>().destinationFile!!)
                }
            }

            configureEach {
                resolutionStrategy {
                    failOnVersionConflict()
                    preferProjectModules()
                }
            }
        }

        configure<SpotlessExtension> {
            java {
                googleJavaFormat("1.9")
                licenseHeaderFile(rootProject.file("buildscripts/spotless.license.java"), "(package|import|class|// Includes work from:)")
            }
        }

        val dependencyManagement by configurations.creating {
            isCanBeConsumed = false
            isCanBeResolved = false
            isVisible = false
        }

        dependencies {
            add(dependencyManagement.name, platform(project(":dependencyManagement")))
            afterEvaluate {
                configurations.configureEach {
                    if (isCanBeResolved && !isCanBeConsumed) {
                        extendsFrom(dependencyManagement)
                    }
                }
            }

            add(COMPILE_ONLY_CONFIGURATION_NAME, "com.google.auto.value:auto-value-annotations")
            add(COMPILE_ONLY_CONFIGURATION_NAME, "com.google.code.findbugs:jsr305")

            add(TEST_COMPILE_ONLY_CONFIGURATION_NAME, "com.google.auto.value:auto-value-annotations")
            add(TEST_COMPILE_ONLY_CONFIGURATION_NAME, "com.google.errorprone:error_prone_annotations")
            add(TEST_COMPILE_ONLY_CONFIGURATION_NAME, "com.google.code.findbugs:jsr305")

            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.junit.jupiter:junit-jupiter-api")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.junit.jupiter:junit-jupiter-params")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "nl.jqno.equalsverifier:equalsverifier")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.mockito:mockito-core")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.mockito:mockito-junit-jupiter")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.assertj:assertj-core")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.awaitility:awaitility")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "io.github.netmikey.logunit:logunit-jul")

            add(TEST_RUNTIME_ONLY_CONFIGURATION_NAME, "org.junit.jupiter:junit-jupiter-engine")
            add(TEST_RUNTIME_ONLY_CONFIGURATION_NAME, "org.junit.vintage:junit-vintage-engine")

            add(ErrorPronePlugin.CONFIGURATION_NAME, "com.google.errorprone:error_prone_core")
            add(ErrorPronePlugin.CONFIGURATION_NAME, "com.uber.nullaway:nullaway")

            add(ANNOTATION_PROCESSOR_CONFIGURATION_NAME, "com.google.guava:guava-beta-checker")

            // Workaround for @javax.annotation.Generated
            // see: https://github.com/grpc/grpc-java/issues/3633
            add(COMPILE_ONLY_CONFIGURATION_NAME, "javax.annotation:javax.annotation-api")
        }

        plugins.withId("com.google.protobuf") {
            protobuf {
                val versions: Map<String, String> by project
                protoc {
                    // The artifact spec for the Protobuf Compiler
                    artifact = "com.google.protobuf:protoc:${versions["com.google.protobuf"]}"
                }
                plugins {
                    id("grpc") {
                        artifact = "io.grpc:protoc-gen-grpc-java:${versions["io.grpc"]}"
                    }
                }
                generateProtoTasks {
                    all().configureEach {
                        plugins {
                            id("grpc")
                        }
                    }
                }
            }

            afterEvaluate {
                // Classpath when compiling protos, we add dependency management directly
                // since it doesn't follow Gradle conventions of naming / properties.
                dependencies {
                    add("compileProtoPath", platform(project(":dependencyManagement")))
                    add("testCompileProtoPath", platform(project(":dependencyManagement")))
                }
            }
        }

        plugins.withId("ru.vyarus.animalsniffer") {
            dependencies {
                add(AnimalSnifferPlugin.SIGNATURE_CONF, "com.toasttab.android:gummy-bears-api-21:0.3.0:coreLib@signature")
            }

            configure<AnimalSnifferExtension> {
                sourceSets = listOf(the<JavaPluginConvention>().sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME))
            }
        }

        plugins.withId("me.champeau.jmh") {
            // Always include the jmhreport plugin and run it after jmh task.
            plugins.apply("io.morethan.jmhreport")
            dependencies {
                add("jmh", platform(project(":dependencyManagement")))
                add("jmh", "org.openjdk.jmh:jmh-core")
                add("jmh", "org.openjdk.jmh:jmh-generator-bytecode")
            }

            // invoke jmh on a single benchmark class like so:
            //   ./gradlew -PjmhIncludeSingleClass=StatsTraceContextBenchmark clean :grpc-core:jmh
            configure<JmhParameters> {
                failOnError.set(true)
                resultFormat.set("JSON")
                // Otherwise an error will happen:
                // Could not expand ZIP 'byte-buddy-agent-1.9.7.jar'.
                includeTests.set(false)
                profilers.add("gc")
                val jmhIncludeSingleClass: String? by project
                if (jmhIncludeSingleClass != null) {
                    includes.add(jmhIncludeSingleClass as String)
                }
            }

            configure<JmhReportExtension> {
                jmhResultPath = file("${buildDir}/results/jmh/results.json").absolutePath
                jmhReportOutput = file("${buildDir}/results/jmh").absolutePath
            }

            tasks {
                // TODO(anuraaga): Unclear why this is triggering even though there don't seem to
                // be duplicates, possibly a bug in JMH plugin.
                named<ProcessResources>("processJmhResources") {
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                }

                named("jmh") {
                    finalizedBy(named("jmhReport"))
                }
            }
        }
    }

    plugins.withId("me.champeau.gradle.japicmp") {
        afterEvaluate {
            tasks {
                val jApiCmp by registering(JapicmpTask::class) {
                    dependsOn("jar")
                    // the japicmp "old" version is either the user-specified one, or the latest release.
                    val userRequestedBase = project.properties["apiBaseVersion"] as String?
                    val baselineVersion: String = userRequestedBase ?: latestReleasedVersion
                    try {
                        val baselineArtifact: File = project.findArtifact(baselineVersion)
                        oldClasspath = files(baselineArtifact)
                    } catch (ignored: Throwable) {
                        // Generally a module that hasn't been released yet.
                        oldClasspath = files()
                    }

                    // the japicmp "new" version is either the user-specified one, or the locally built jar.
                    val newVersion : String? = project.properties["apiNewVersion"] as String?
                    val newArtifact: File = if (newVersion == null) {
                        val jar = getByName("jar") as Jar
                        file(jar.archiveFile)
                    } else {
                        project.findArtifact(newVersion)
                    }
                    newClasspath = files(newArtifact)

                    //only output changes, not everything
                    isOnlyModified = true
                    //this is needed so that we only consider the current artifact, and not dependencies
                    isIgnoreMissingClasses = true
                    // double wildcards don't seem to work here (*.internal.*)
                    packageExcludes = listOf("*.internal", "io.opentelemetry.internal.shaded.jctools.*")
                    if (newVersion == null) {
                        val baseVersionString = if (userRequestedBase == null) "latest" else baselineVersion
                        txtOutputFile = file("$rootDir/docs/apidiffs/current_vs_${baseVersionString}/${project.base.archivesBaseName}.txt")
                    } else {
                        txtOutputFile = file("$rootDir/docs/apidiffs/${newVersion}_vs_${baselineVersion}/${project.base.archivesBaseName}.txt")
                    }
                }
                // have the check task depend on the api comparison task, to make it more likely it will get used.
                named("check") {
                    dependsOn(jApiCmp)
                }
            }
        }
    }

    plugins.withId("maven-publish") {
        // generate the api diff report for any module that is stable and publishes a jar.
        if (!project.hasProperty("otel.release") && !project.name.startsWith("bom")) {
            plugins.apply("me.champeau.gradle.japicmp")
        }
        plugins.apply("signing")
        plugins.apply("de.marcphilipp.nexus-publish")

        configure<PublishingExtension> {
            publications {
                register<MavenPublication>("mavenPublication") {
                    val release = findProperty("otel.release")
                    if (release != null) {
                        val versionParts = version.split('-').toMutableList()
                        versionParts[0] += "-${release}"
                        version = versionParts.joinToString("-")
                    }
                    groupId = "io.opentelemetry"
                    afterEvaluate {
                        // not available until evaluated.
                        artifactId = the<BasePluginConvention>().archivesBaseName
                        pom.description.set(project.description)
                    }

                    plugins.withId("java-platform") {
                        from(components["javaPlatform"])
                    }
                    plugins.withId("java-library") {
                        from(components["java"])
                    }

                    versionMapping {
                        allVariants {
                            fromResolutionResult()
                        }
                    }

                    pom {
                        name.set("OpenTelemetry Java")
                        url.set("https://github.com/open-telemetry/opentelemetry-java")

                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }

                        developers {
                            developer {
                                id.set("opentelemetry")
                                name.set("OpenTelemetry")
                                url.set("https://github.com/open-telemetry/community")
                            }
                        }

                        scm {
                            connection.set("scm:git:git@github.com:open-telemetry/opentelemetry-java.git")
                            developerConnection.set("scm:git:git@github.com:open-telemetry/opentelemetry-java.git")
                            url.set("git@github.com:open-telemetry/opentelemetry-java.git")
                        }
                    }
                }
            }
        }

        configure<NexusPublishExtension> {
            repositories {
                sonatype()
            }

            connectTimeout.set(Duration.ofMinutes(5))
            clientTimeout.set(Duration.ofMinutes(5))
        }

        val publishToSonatype by tasks.getting
        releaseTask.configure {
            finalizedBy(publishToSonatype)
        }
        rootProject.tasks.named("closeAndReleaseRepository") {
            mustRunAfter(publishToSonatype)
        }

        tasks.withType(Sign::class) {
            onlyIf { System.getenv("CI") != null }
        }

        configure<SigningExtension> {
            useInMemoryPgpKeys(System.getenv("GPG_PRIVATE_KEY"), System.getenv("GPG_PASSWORD"))
            sign(the<PublishingExtension>().publications["mavenPublication"])
        }
    }
}

allprojects {
    tasks.register("updateVersionInDocs") {
        group = "documentation"
        doLast {
            val versionParts = version.toString().split('.')
            val minorVersionNumber = Integer.parseInt(versionParts[1])
            val nextSnapshot = "${versionParts[0]}.${minorVersionNumber + 1}.0-SNAPSHOT"

            val readme = file("README.md")
            if (readme.exists()) {
                val readmeText = readme.readText()
                val updatedText = readmeText
                        .replace("""<version>\d+\.\d+\.\d+</version>""".toRegex(), "<version>${version}</version>")
                        .replace("""<version>\d+\.\d+\.\d+-SNAPSHOT</version>""".toRegex(), "<version>${nextSnapshot}</version>")
                        .replace("""(implementation.*io\.opentelemetry:.*:)(\d+\.\d+\.\d+)(?!-SNAPSHOT)(.*)""".toRegex(), "\$1${version}\$3")
                        .replace("""(implementation.*io\.opentelemetry:.*:)(\d+\.\d+\.\d+-SNAPSHOT)(.*)""".toRegex(), "\$1${nextSnapshot}\$3")
                        .replace("""<!--VERSION_STABLE-->.*<!--/VERSION_STABLE-->""".toRegex(), "<!--VERSION_STABLE-->${version}<!--/VERSION_STABLE-->")
                        .replace("""<!--VERSION_UNSTABLE-->.*<!--/VERSION_UNSTABLE-->""".toRegex(), "<!--VERSION_UNSTABLE-->${version}-alpha<!--/VERSION_UNSTABLE-->")
                readme.writeText(updatedText)
            }
        }
    }
}
