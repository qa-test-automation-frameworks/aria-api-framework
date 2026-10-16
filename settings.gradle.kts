val runtimeJavaVersion = System.getProperty("java.specification.version")
if (runtimeJavaVersion != "21") {
    throw GradleException(
        "ARIA requires JDK 21 to run Gradle; detected Java ${runtimeJavaVersion ?: "unknown"}. " +
            "Select JDK 21 and verify JAVA_HOME before running ./gradlew."
    )
}

rootProject.name = "aria-api-framework"
