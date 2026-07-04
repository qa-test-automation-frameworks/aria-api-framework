import java.util.Locale

plugins {
    java
    alias(libs.plugins.lombok)
    alias(libs.plugins.allure)
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.pitest)
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs)
}

group = "com.aria.framework"
version = "1.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // RestAssured
    implementation(libs.rest.assured)
    implementation(libs.rest.assured.json.schema.validator)
    implementation(libs.allure.java.commons)

    // Jackson (JSON Serialization/Deserialization)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Configuration Manager
    implementation(libs.owner)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    compileOnly(libs.spotbugs.annotations)
    testCompileOnly(libs.spotbugs.annotations)

    // Test data generation and reporting tools
    testImplementation(libs.datafaker)
    testImplementation(libs.snakeyaml)
    testImplementation(libs.swagger.request.validator.restassured)

    // Core Testing Framework (JUnit 5)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)

    // Assertions
    testImplementation(libs.assertj)

    // Mocking and CDC Contract Testing
    testImplementation(libs.wiremock)
    testImplementation(libs.pact.consumer.junit5) {
        exclude(group = "org.apache.commons", module = "commons-io")
    }
    testImplementation(libs.pact.provider.junit5) {
        exclude(group = "org.apache.commons", module = "commons-io")
    }
    testImplementation(libs.commons.io)

    // Containerization Support
    testImplementation(platform("io.netty:netty-bom:4.2.15.Final"))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)

    // Reporting
    implementation(libs.allure.rest.assured)
    testImplementation(libs.allure.junit5)

    constraints {
        implementation("org.apache.commons:commons-lang3:3.18.0") {
            because("CVE-2025-48924 affects commons-lang3 versions before 3.18.0")
        }
        implementation("org.mozilla:rhino:1.7.14.1") {
            because("GHSA-3w8q-xq97-5j7x affects Rhino versions before 1.7.14.1")
        }
        testImplementation("commons-fileupload:commons-fileupload:1.6.0") {
            because("CVE-2025-48976 affects commons-fileupload versions before 1.6.0")
        }
        testImplementation("commons-beanutils:commons-beanutils:1.11.0") {
            because("CVE-2025-48734 affects commons-beanutils versions before 1.11.0")
        }
        testImplementation("com.google.protobuf:protobuf-java:3.25.5") {
            because("GHSA-735f-pc8j-v9w8 affects protobuf-java versions before 3.25.5")
        }
        testImplementation("io.netty:netty-codec:4.2.15.Final") {
            because("Multiple Netty advisories affect the locked 4.1.87/4.1.91 modules")
        }
        testImplementation("io.netty:netty-codec-http:4.2.15.Final") {
            because("Multiple Netty advisories affect the locked 4.1.87/4.1.91 modules")
        }
        testImplementation("io.netty:netty-codec-http2:4.2.15.Final") {
            because("Multiple Netty advisories affect the locked 4.1.87/4.1.91 modules")
        }
        testImplementation("io.netty:netty-common:4.2.15.Final") {
            because("Multiple Netty advisories affect the locked 4.1.87/4.1.91 modules")
        }
        testImplementation("io.netty:netty-handler:4.2.15.Final") {
            because("Multiple Netty advisories affect the locked 4.1.87/4.1.91 modules")
        }
        testImplementation("io.netty:netty-handler-proxy:4.2.15.Final") {
            because("Multiple Netty advisories affect the locked 4.1.87/4.1.91 modules")
        }
        testImplementation("org.apache.commons:commons-compress:1.26.0") {
            because("Commons Compress advisories affect versions before 1.26.0")
        }
        testImplementation("org.apache.logging.log4j:log4j-core:2.25.4") {
            because("Multiple Log4j Core advisories affect the locked 2.22.0 version")
        }
        testImplementation("org.apache.tika:tika-core:3.2.2") {
            because("GHSA-f58c-gq56-vjjf affects tika-core versions before 3.2.2")
        }
        testImplementation("org.json:json:20231013") {
            because("GHSA-4jq9-2xhw-jpx7 affects org.json versions before 20231013")
        }
        testImplementation("org.mozilla:rhino:1.7.14.1") {
            because("GHSA-3w8q-xq97-5j7x affects Rhino versions before 1.7.14.1")
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}

configurations.configureEach {
    resolutionStrategy.force(
        "org.apache.logging.log4j:log4j-api:2.25.4",
        "org.apache.logging.log4j:log4j-core:2.25.4",
        // GHSA-j288-q9x7-2f5v: PIT's own tool classpath pulls an old commons-lang3.
        "org.apache.commons:commons-lang3:3.19.0",
        // GHSA-72hv-8253-57qq / GHSA-hgj6-7826-r7m5 / GHSA-j3rv-43j4-c7qm / GHSA-rmj7-2vxq-3g9f:
        // transitive tool classpaths (Gradle plugin resolution, Spotless) pull old Jackson.
        "com.fasterxml.jackson.core:jackson-core:2.22.0",
        "com.fasterxml.jackson.core:jackson-databind:2.22.0",
        // GHSA-735f-pc8j-v9w8: old protobuf-java pulled in transitively.
        "com.google.protobuf:protobuf-java:3.25.5",
        // GHSA-wxr5-93ph-8wr9: old commons-beanutils pulled in transitively.
        "commons-beanutils:commons-beanutils:1.11.0",
        // GHSA-f58c-gq56-vjjf: old tika-core pulled in transitively.
        "org.apache.tika:tika-core:3.2.2",
        // GHSA-3w8q-xq97-5j7x: the constraints-based rhino pin above doesn't reach
        // transient tool classpaths, so force it globally too.
        "org.mozilla:rhino:1.7.14.1"
    )
}

allure {
    version.set(libs.versions.allure.get())
    adapter {
        aspectjWeaver.set(false)
    }
}

fun Test.configureCommonApiTestTask() {
    useJUnitPlatform {
        val requestedTags = System.getProperty("includeTags", "")
        if (requestedTags.isNotBlank()) {
            includeTags(*requestedTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
        }
    }

    systemProperty("env", System.getProperty("env", "dev"))
    systemProperty("ENV", System.getenv("ENV") ?: System.getProperty("env", "dev"))
    systemProperty("github.token", System.getProperty("github.token", ""))
    systemProperty("pact.rootDir", layout.buildDirectory.dir("pacts").get().asFile.absolutePath)
    systemProperty("pact_do_not_track", System.getenv("pact_do_not_track") ?: "true")

    jvmArgs("-Dfile.encoding=UTF-8")

    reports {
        junitXml.required.set(true)
        html.required.set(true)
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

fun Test.configureDeterministicParallelForks() {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2)
        .coerceAtLeast(1)
        .coerceAtMost(2)
}

tasks.test {
    configureCommonApiTestTask()
    configureDeterministicParallelForks()
    useJUnitPlatform {
        val requestedTags = System.getProperty("includeTags", "")
        if (requestedTags.isBlank()) {
            excludeTags("live")
        }
    }
    finalizedBy("testDurationReport", "portfolioMetrics")
}

tasks.register("testDurationReport") {
    description = "Writes a Markdown summary of JUnit test durations."
    group = "verification"
    doLast {
        val resultDir = layout.buildDirectory.dir("test-results/test").get().asFile
        val reportFile = layout.buildDirectory.file("reports/test-duration-report.md").get().asFile
        reportFile.parentFile.mkdirs()
        val cases = fileTree(resultDir) {
            include("TEST-*.xml")
        }.files.flatMap { file ->
            val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
            val nodes = document.getElementsByTagName("testcase")
            (0 until nodes.length).map { index ->
                val node = nodes.item(index) as org.w3c.dom.Element
                Triple(
                    node.getAttribute("classname"),
                    node.getAttribute("name"),
                    node.getAttribute("time").toDouble()
                )
            }
        }.sortedByDescending { it.third }

        reportFile.writeText(buildString {
            appendLine("# Test Duration Report")
            appendLine()
            appendLine("| Class | Test | Seconds |")
            appendLine("| --- | --- | ---: |")
            cases.take(20).forEach { (className, testName, seconds) ->
                appendLine("| `$className` | `$testName` | `${"%.3f".format(seconds)}` |")
            }
        })
    }
}

tasks.register("portfolioMetrics") {
    description = "Writes the portfolio metrics contract from JUnit XML results."
    group = "verification"
    doLast {
        val resultDir = layout.buildDirectory.dir("test-results/test").get().asFile
        val reportFile = layout.buildDirectory.file("reports/portfolio-metrics-v1.json").get().asFile
        val suites = fileTree(resultDir) {
            include("TEST-*.xml")
        }.files.map { file ->
            javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
                .documentElement
        }
        if (suites.isEmpty()) {
            throw GradleException("portfolioMetrics found no JUnit XML under ${resultDir.absolutePath}")
        }

        fun attributeTotal(name: String): Long = suites.sumOf {
            it.getAttribute(name).ifBlank { "0" }.toLong()
        }
        val durationSeconds = suites.sumOf {
            it.getAttribute("time").ifBlank { "0" }.toDouble()
        }
        val tests = attributeTotal("tests")
        val failures = attributeTotal("failures")
        val errors = attributeTotal("errors")
        val skipped = attributeTotal("skipped")

        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            """
            {
              "schemaVersion": 1,
              "framework": "aria-api-framework",
              "generatedFrom": "JUnit XML",
              "tests": $tests,
              "passed": ${tests - failures - errors - skipped},
              "failures": $failures,
              "errors": $errors,
              "skipped": $skipped,
              "retries": 0,
              "durationSeconds": ${"%.3f".format(Locale.ROOT, durationSeconds)}
            }
            """.trimIndent() + System.lineSeparator()
        )
    }
}

tasks.register<Test>("smokeTest") {
    description = "Runs smoke-tagged API tests."
    group = "verification"
    configureCommonApiTestTask()
    configureDeterministicParallelForks()
    useJUnitPlatform {
        includeTags("smoke")
        excludeTags("live")
    }
}

tasks.register<Test>("regressionTest") {
    description = "Runs regression-tagged API tests."
    group = "verification"
    configureCommonApiTestTask()
    configureDeterministicParallelForks()
    useJUnitPlatform {
        includeTags("regression")
        excludeTags("live")
    }
}

tasks.register<Test>("contractTest") {
    description = "Runs contract-tagged API tests."
    group = "verification"
    configureCommonApiTestTask()
    configureDeterministicParallelForks()
    useJUnitPlatform {
        includeTags("contract")
    }
}

tasks.register<Test>("pactProviderVerificationTest") {
    description = "Replays generated Pact consumer contracts against the owned provider."
    group = "verification"
    configureCommonApiTestTask()
    useJUnitPlatform {
        includeTags("pact-provider")
    }
    dependsOn("contractTest")
    shouldRunAfter("contractTest")
}

tasks.register<Test>("securityTest") {
    description = "Runs deterministic security-tagged API tests."
    group = "verification"
    configureCommonApiTestTask()
    configureDeterministicParallelForks()
    useJUnitPlatform {
        includeTags("security")
        excludeTags("live")
    }
}

tasks.register<Test>("containerTest") {
    description = "Runs container-tagged API tests and fails if they are skipped."
    group = "verification"
    configureCommonApiTestTask()
    useJUnitPlatform {
        includeTags("container")
    }
    doLast {
        val resultDir = layout.buildDirectory.dir("test-results/containerTest").get().asFile
        val xml = fileTree(resultDir) {
            include("TEST-*.xml")
        }.files.joinToString("\n") { it.readText() }
        if (xml.isBlank()) {
            throw GradleException("containerTest produced no JUnit XML results")
        }
        if (Regex("""skipped="[1-9][0-9]*"""").containsMatchIn(xml)) {
            throw GradleException("containerTest skipped one or more tests; Docker-backed coverage must execute in CI")
        }
    }
}

tasks.register<Test>("liveTest") {
    description = "Runs live API tests against configured external environments."
    group = "verification"
    configureCommonApiTestTask()
    useJUnitPlatform {
        includeTags("live")
    }
    shouldRunAfter(tasks.test)
}

tasks.register<Test>("liveSmokeTest") {
    description = "Runs smoke-tagged live API tests against configured external environments."
    group = "verification"
    configureCommonApiTestTask()
    useJUnitPlatform {
        includeTags("live & smoke")
    }
    shouldRunAfter(tasks.test)
}

tasks.register("verifyLiveSmokeTagExpression") {
    description = "Verifies liveSmokeTest uses a strict live AND smoke tag expression."
    group = "verification"
    doLast {
        val configuredTags = tasks.named<Test>("liveSmokeTest").get()
            .options.let { it as org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions }
            .includeTags
        if (configuredTags != setOf("live & smoke")) {
            throw GradleException("liveSmokeTest must include only the strict tag expression 'live & smoke'; found $configuredTags")
        }
    }
}

val openApiCoverageReport by tasks.registering(JavaExec::class) {
    description = "Generates OpenAPI endpoint-to-test coverage report."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.aria.framework.tools.OpenApiCoverageReporter")
    args(
        layout.projectDirectory.dir("src/test/resources/openapi").asFile.absolutePath,
        layout.buildDirectory.file("reports/openapi-coverage.md").get().asFile.absolutePath
    )
    outputs.file(layout.buildDirectory.file("reports/openapi-coverage.md"))
    dependsOn(tasks.testClasses)
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.GIT_ATTRIBUTES
    java {
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

spotbugs {
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
}

pitest {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(
        setOf(
            "com.aria.framework.reporting.RedactionPolicy",
            "com.aria.framework.utils.RetryUtils",
            "com.aria.framework.tools.OpenApiCoverageReporter"
        )
    )
    targetTests.set(
        setOf(
            "com.aria.framework.reporting.*",
            "com.aria.framework.utils.*",
            "com.aria.framework.contracts.*"
        )
    )
    mutators.set(setOf("STRONGER"))
    timestampedReports.set(false)
    outputFormats.set(setOf("XML", "HTML"))
    mutationThreshold.set(70)
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("xml") {
        required.set(true)
    }
    reports.create("html") {
        required.set(true)
    }
}

tasks.named("check") {
    dependsOn(
        "spotlessCheck",
        "spotbugsMain",
        "spotbugsTest",
        "pitest",
        openApiCoverageReport,
        "pactProviderVerificationTest",
        "verifyLiveSmokeTagExpression"
    )
}

tasks.named("cyclonedxBom") {
    group = "verification"
}

tasks.register("securityScan") {
    description = "Generates a local SBOM and runs osv-scanner when installed."
    group = "verification"
    dependsOn("cyclonedxBom")
    doLast {
        val reportFile = layout.buildDirectory.file("reports/security-scan.md").get().asFile
        reportFile.parentFile.mkdirs()
        val sbomFile = layout.buildDirectory.file("reports/cyclonedx/bom.json").get().asFile
        val requireScanner = providers.gradleProperty("requireOsvScanner")
            .map(String::toBoolean)
            .getOrElse(false)
        val scannerLookup = providers.exec {
            isIgnoreExitValue = true
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                commandLine("cmd", "/c", "where", "osv-scanner")
            } else {
                commandLine("sh", "-c", "command -v osv-scanner")
            }
        }

        val scannerLookupResult = scannerLookup.result.get()
        val scannerPath = scannerLookup.standardOutput.asText.get()
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
        if (scannerLookupResult.exitValue != 0 || scannerPath == null) {
            reportFile.writeText(
                """
                # Local Vulnerability Scan

                SBOM: `build/reports/cyclonedx/bom.json`

                Result: `osv-scanner` was not found on PATH, so no local vulnerability scan was executed.

                Install `osv-scanner` and run:

                ```powershell
                osv-scanner --sbom build/reports/cyclonedx/bom.json
                ```

                CI runs the official OSV scanner action. To require a local scanner for this task, run:

                ```powershell
                .\gradlew.bat securityScan -PrequireOsvScanner=true
                ```
                """.trimIndent()
            )
            if (requireScanner) {
                throw GradleException("osv-scanner is required but was not found on PATH")
            }
            return@doLast
        }

        val scanOutput = providers.exec {
            isIgnoreExitValue = true
            commandLine(scannerPath, "--sbom", sbomFile.absolutePath)
        }
        val scanResult = scanOutput.result.get()
        val scanText = buildString {
            append(scanOutput.standardOutput.asText.get())
            append(scanOutput.standardError.asText.get())
        }
        reportFile.writeText(
            """
            # Local Vulnerability Scan

            SBOM: `build/reports/cyclonedx/bom.json`

            Command: `$scannerPath --sbom ${sbomFile.absolutePath}`

            Exit code: `${scanResult.exitValue}`

            ```text
            ${scanText.trim()}
            ```
            """.trimIndent()
        )
        if (scanResult.exitValue != 0) {
            throw GradleException("osv-scanner reported vulnerabilities or scan failure; see ${reportFile.absolutePath}")
        }
    }
}
