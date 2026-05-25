package com.aria.framework.contracts;

import com.aria.framework.tools.OpenApiCoverageReporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.aria.framework.reporting.AllureTestDiagnostics.log;
import static com.aria.framework.reporting.AllureTestDiagnostics.step;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies every OpenAPI endpoint in the checked-in specs is mapped to at least one test.
 */
class OpenApiCoverageTest {

    @Test
    @Tag("contract")
    @DisplayName("OpenAPI endpoints are mapped to test coverage")
    void openApiEndpointsAreMappedToTests() throws Exception {
        OpenApiCoverageReporter.CoverageReport report = step("Generate OpenAPI endpoint coverage report", () ->
            OpenApiCoverageReporter.generate(
                Path.of("src/test/resources/openapi"),
                Path.of("build/reports/openapi-coverage.md")
            ));

        step("Verify OpenAPI coverage has no gaps", () -> {
            log(
                "Coverage report gaps: missingEndpoints={}, invalidReferences={}, contractIssues={}",
                report.missingEndpoints().size(),
                report.invalidReferences().size(),
                report.contractIssues().size()
            );
            assertThat(report.missingEndpoints())
                .as("OpenAPI endpoints without mapped tests")
                .isEmpty();
            assertThat(report.invalidReferences())
                .as("OpenAPI coverage references to missing test classes or methods")
                .isEmpty();
            assertThat(report.contractIssues())
                .as("OpenAPI operations missing request/response schemas, statuses, or path parameters")
                .isEmpty();
        });
    }
}
