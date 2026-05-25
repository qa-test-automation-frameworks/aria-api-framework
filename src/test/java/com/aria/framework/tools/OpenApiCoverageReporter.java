package com.aria.framework.tools;

import org.junit.jupiter.api.Tag;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates a Markdown report mapping OpenAPI endpoints to test coverage and contract completeness.
 */
public final class OpenApiCoverageReporter {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete", "options");
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}]+)}");

    private OpenApiCoverageReporter() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: OpenApiCoverageReporter <openapi-dir> <output-file>");
        }
        generate(Path.of(args[0]), Path.of(args[1]));
    }

    public static CoverageReport generate(Path openApiDir, Path outputFile) throws IOException {
        OpenApiInventory inventory = readOpenApiInventory(openApiDir);
        List<Endpoint> endpoints = inventory.endpoints();
        Map<String, CoverageEntry> coverage = readCoverage(openApiDir.resolve("endpoint-test-coverage.csv"));
        CoverageValidation validation = validateCoverageReferences(coverage);
        List<Endpoint> missingEndpoints = endpoints.stream()
            .filter(endpoint -> !coverage.containsKey(endpoint.key()))
            .toList();
        List<Endpoint> nonDefaultCiEndpoints = endpoints.stream()
            .filter(endpoint -> {
                CoverageEntry entry = coverage.get(endpoint.key());
                return entry != null && !entry.defaultCiCovered();
            })
            .toList();

        CoverageReport report = new CoverageReport(
            endpoints,
            coverage,
            missingEndpoints,
            validation.invalidReferences(),
            nonDefaultCiEndpoints,
            inventory.contractIssues()
        );
        writeMarkdown(report, outputFile);
        if (!missingEndpoints.isEmpty()
            || !validation.invalidReferences().isEmpty()
            || !nonDefaultCiEndpoints.isEmpty()
            || !inventory.contractIssues().isEmpty()) {
            throw new IllegalStateException("OpenAPI coverage is incomplete. Missing endpoints: "
                + missingEndpoints + "; invalid test references: " + validation.invalidReferences()
                + "; endpoints without default-CI coverage: " + nonDefaultCiEndpoints
                + "; contract issues: " + inventory.contractIssues());
        }
        return report;
    }

    @SuppressWarnings("unchecked")
    private static OpenApiInventory readOpenApiInventory(Path openApiDir) throws IOException {
        Yaml yaml = new Yaml();
        List<Endpoint> endpoints = new ArrayList<>();
        List<String> contractIssues = new ArrayList<>();
        try (var files = Files.list(openApiDir)) {
            for (Path specFile : files
                .filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".yaml"))
                .sorted()
                .toList()) {
                Path fileName = specFile.getFileName();
                if (fileName == null) {
                    continue;
                }
                String specFileName = fileName.toString();
                try (InputStream inputStream = Files.newInputStream(specFile)) {
                    Map<String, Object> spec = yaml.load(inputStream);
                    Map<String, Object> paths = (Map<String, Object>) spec.getOrDefault("paths", Map.of());
                    for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                        Object rawPathItem = pathEntry.getValue();
                        if (!(rawPathItem instanceof Map<?, ?>)) {
                            continue;
                        }
                        Map<String, Object> pathItem = (Map<String, Object>) rawPathItem;
                        List<Map<String, Object>> pathParameters = parameters(pathItem.get("parameters"));
                        for (Map.Entry<String, Object> operationEntry : pathItem.entrySet()) {
                            String method = operationEntry.getKey();
                            if (!HTTP_METHODS.contains(method.toLowerCase(Locale.ROOT))) {
                                continue;
                            }
                            Map<String, Object> operation = (Map<String, Object>) operationEntry.getValue();
                            String normalizedMethod = method.toUpperCase(Locale.ROOT);
                            contractIssues.addAll(validateOperationContract(
                                specFileName,
                                normalizedMethod,
                                pathEntry.getKey(),
                                pathParameters,
                                operation
                            ));
                            endpoints.add(new Endpoint(
                                normalizedMethod,
                                pathEntry.getKey(),
                                specFileName,
                                responseStatuses(operation),
                                hasRequestBodySchema(operation),
                                hasResponseSchemas(operation)
                            ));
                        }
                    }
                }
            }
        }
        List<Endpoint> sortedEndpoints = endpoints.stream()
            .sorted(Comparator.comparing(Endpoint::specFile).thenComparing(Endpoint::path).thenComparing(Endpoint::method))
            .toList();
        return new OpenApiInventory(sortedEndpoints, contractIssues);
    }

    private static List<String> validateOperationContract(
        String specFile,
        String method,
        String path,
        List<Map<String, Object>> pathParameters,
        Map<String, Object> operation
    ) {
        List<String> issues = new ArrayList<>();
        String location = specFile + " " + method + " " + path;
        if (operation.get("operationId") == null || operation.get("operationId").toString().isBlank()) {
            issues.add(location + " is missing operationId");
        }
        if (BODY_METHODS.contains(method) && !hasRequestBodySchema(operation)) {
            issues.add(location + " is missing a JSON requestBody schema");
        }
        for (String pathParameter : pathParameterNames(path)) {
            if (!hasPathParameter(pathParameter, pathParameters, parameters(operation.get("parameters")))) {
                issues.add(location + " is missing path parameter definition for " + pathParameter);
            }
        }

        Object rawResponses = operation.get("responses");
        if (!(rawResponses instanceof Map<?, ?> responses) || responses.isEmpty()) {
            issues.add(location + " is missing responses");
            return issues;
        }
        for (Map.Entry<?, ?> responseEntry : responses.entrySet()) {
            Object rawResponse = responseEntry.getValue();
            if (!(rawResponse instanceof Map<?, ?> response)) {
                issues.add(location + " response " + responseEntry.getKey() + " is not an object");
                continue;
            }
            Object description = response.get("description");
            if (description == null || description.toString().isBlank()) {
                issues.add(location + " response " + responseEntry.getKey() + " is missing description");
            }
            if (!isNoContentStatus(responseEntry.getKey()) && !hasContentSchema(response)) {
                issues.add(location + " response " + responseEntry.getKey() + " is missing a content schema");
            }
        }
        return issues;
    }

    private static List<String> pathParameterNames(String path) {
        List<String> names = new ArrayList<>();
        Matcher matcher = PATH_PARAMETER.matcher(path);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static boolean hasPathParameter(
        String pathParameter,
        List<Map<String, Object>> pathParameters,
        List<Map<String, Object>> operationParameters
    ) {
        return hasPathParameter(pathParameter, pathParameters) || hasPathParameter(pathParameter, operationParameters);
    }

    private static boolean hasPathParameter(String pathParameter, List<Map<String, Object>> parameters) {
        return parameters.stream()
            .anyMatch(parameter -> pathParameter.equals(parameter.get("name")) && "path".equals(parameter.get("in")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parameters(Object rawParameters) {
        if (!(rawParameters instanceof List<?> parameters)) {
            return List.of();
        }
        return parameters.stream()
            .filter(Map.class::isInstance)
            .map(parameter -> (Map<String, Object>) parameter)
            .toList();
    }

    @SuppressWarnings("unchecked")
    private static boolean hasRequestBodySchema(Map<String, Object> operation) {
        Object rawRequestBody = operation.get("requestBody");
        if (!(rawRequestBody instanceof Map<?, ?> requestBody)) {
            return false;
        }
        Object rawContent = requestBody.get("content");
        if (!(rawContent instanceof Map<?, ?> content)) {
            return false;
        }
        return content.values().stream()
            .filter(Map.class::isInstance)
            .map(mediaType -> (Map<String, Object>) mediaType)
            .anyMatch(mediaType -> mediaType.get("schema") instanceof Map<?, ?>);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasResponseSchemas(Map<String, Object> operation) {
        Object rawResponses = operation.get("responses");
        if (!(rawResponses instanceof Map<?, ?> responses) || responses.isEmpty()) {
            return false;
        }
        return responses.entrySet().stream()
            .filter(entry -> entry.getValue() instanceof Map<?, ?>)
            .allMatch(entry -> isNoContentStatus(entry.getKey())
                || hasContentSchema((Map<String, Object>) entry.getValue()));
    }

    private static boolean isNoContentStatus(Object status) {
        String statusText = String.valueOf(status);
        return "204".equals(statusText) || "304".equals(statusText);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasContentSchema(Map<?, ?> response) {
        Object rawContent = response.get("content");
        if (!(rawContent instanceof Map<?, ?> content) || content.isEmpty()) {
            return false;
        }
        return content.values().stream()
            .filter(Map.class::isInstance)
            .map(mediaType -> (Map<String, Object>) mediaType)
            .anyMatch(mediaType -> mediaType.get("schema") instanceof Map<?, ?>);
    }

    private static List<String> responseStatuses(Map<String, Object> operation) {
        Object rawResponses = operation.get("responses");
        if (!(rawResponses instanceof Map<?, ?> responses)) {
            return List.of();
        }
        return responses.keySet().stream()
            .map(Object::toString)
            .sorted()
            .toList();
    }

    private static Map<String, CoverageEntry> readCoverage(Path coverageFile) throws IOException {
        Map<String, CoverageEntry> coverage = new HashMap<>();
        List<String> lines = Files.readAllLines(coverageFile, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(",", 4);
            List<TestReference> testReferences = List.of(parts[2].trim().split(";")).stream()
                .filter(test -> !test.isBlank())
                .map(OpenApiCoverageReporter::toUnvalidatedTestReference)
                .toList();
            CoverageEntry entry = new CoverageEntry(
                parts[0].trim().toUpperCase(Locale.ROOT),
                parts[1].trim(),
                testReferences,
                parts[3].trim()
            );
            coverage.put(entry.key(), entry);
        }
        return coverage;
    }

    private static TestReference toUnvalidatedTestReference(String testReference) {
        String[] parts = testReference.trim().split("#", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return new TestReference(testReference.trim(), "", false, false, false);
        }
        return new TestReference(parts[0].trim(), parts[1].trim(), false, false, false);
    }

    private static CoverageValidation validateCoverageReferences(Map<String, CoverageEntry> coverage) {
        List<String> invalidReferences = new ArrayList<>();
        Map<String, CoverageEntry> validatedCoverage = new HashMap<>();
        for (CoverageEntry entry : coverage.values()) {
            List<TestReference> validatedReferences = new ArrayList<>();
            for (TestReference reference : entry.tests()) {
                TestReference validatedReference = validateTestReference(reference, invalidReferences);
                validatedReferences.add(validatedReference);
            }
            validatedCoverage.put(entry.key(),
                new CoverageEntry(entry.method(), entry.path(), validatedReferences, entry.category()));
        }
        coverage.clear();
        coverage.putAll(validatedCoverage);
        return new CoverageValidation(invalidReferences);
    }

    private static TestReference validateTestReference(TestReference reference, List<String> invalidReferences) {
        if (reference.className().isBlank() || reference.methodName().isBlank()) {
            invalidReferences.add(reference.displayName());
            return reference;
        }
        try {
            Class<?> testClass = Class.forName(reference.className());
            Method testMethod = findMethod(testClass, reference.methodName());
            if (testMethod == null) {
                invalidReferences.add(reference.displayName());
                return new TestReference(reference.className(), reference.methodName(), false, false, false);
            }
            boolean live = isTagged(testClass, "live") || isTagged(testMethod, "live");
            boolean container = isTagged(testClass, "container") || isTagged(testMethod, "container");
            return new TestReference(reference.className(), reference.methodName(), live, !live, container);
        } catch (ClassNotFoundException exception) {
            invalidReferences.add(reference.displayName());
            return new TestReference(reference.className(), reference.methodName(), false, false, false);
        }
    }

    private static Method findMethod(Class<?> testClass, String methodName) {
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isTagged(Class<?> testClass, String tagName) {
        for (Tag tag : testClass.getAnnotationsByType(Tag.class)) {
            if (tagName.equals(tag.value())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTagged(Method testMethod, String tagName) {
        for (Tag tag : testMethod.getAnnotationsByType(Tag.class)) {
            if (tagName.equals(tag.value())) {
                return true;
            }
        }
        return false;
    }

    private static void writeMarkdown(CoverageReport report, Path outputFile) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Map<String, List<Endpoint>> endpointsBySpec = report.endpoints().stream()
            .collect(Collectors.groupingBy(Endpoint::specFile, LinkedHashMap::new, Collectors.toList()));

        StringBuilder markdown = new StringBuilder("# OpenAPI Endpoint Coverage\n\n");
        markdown.append("- Total endpoints: ").append(report.endpoints().size()).append('\n');
        markdown.append("- Covered endpoints: ").append(report.coveredCount()).append('\n');
        markdown.append("- Missing endpoints: ").append(report.missingEndpoints().size()).append('\n');
        markdown.append("- Default-CI covered endpoints: ").append(report.defaultCiCoveredCount()).append('\n');
        markdown.append("- Live-only endpoints: ").append(report.liveOnlyCount()).append('\n');
        markdown.append("- Endpoints without default-CI coverage: ").append(report.nonDefaultCiEndpoints().size()).append('\n');
        markdown.append("- Request-body schema endpoints: ").append(report.requestSchemaCount()).append('\n');
        markdown.append("- Response schema endpoints: ").append(report.responseSchemaCount()).append('\n');
        markdown.append("- Contract issues: ").append(report.contractIssues().size()).append('\n');
        markdown.append("- Invalid test references: ").append(report.invalidReferences().size()).append("\n\n");

        if (!report.contractIssues().isEmpty()) {
            markdown.append("## Contract Issues\n\n");
            for (String issue : report.contractIssues()) {
                markdown.append("- ").append(issue).append('\n');
            }
            markdown.append('\n');
        }

        for (Map.Entry<String, List<Endpoint>> entry : endpointsBySpec.entrySet()) {
            markdown.append("## ").append(entry.getKey()).append("\n\n");
            markdown.append("| Method | Path | Statuses | Request Schema | Response Schemas | Category | Execution | Tests |\n");
            markdown.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
            for (Endpoint endpoint : entry.getValue()) {
                CoverageEntry coverage = report.coverage().get(endpoint.key());
                markdown
                    .append("| ")
                    .append(endpoint.method())
                    .append(" | `")
                    .append(endpoint.path())
                    .append("` | ")
                    .append(String.join(", ", endpoint.responseStatuses()))
                    .append(" | ")
                    .append(endpoint.requestContracted() ? "yes" : "n/a")
                    .append(" | ")
                    .append(endpoint.responseContracted() ? "yes" : "no")
                    .append(" | ")
                    .append(coverage == null ? "missing" : coverage.category())
                    .append(" | ")
                    .append(coverage == null ? "missing" : coverage.executionProfile())
                    .append(" | ")
                    .append(coverage == null ? "" : coverage.tests().stream()
                        .map(TestReference::displayName)
                        .collect(Collectors.joining("<br>")))
                    .append(" |\n");
            }
            markdown.append('\n');
        }

        Files.writeString(outputFile, markdown.toString(), StandardCharsets.UTF_8);
    }

    public record Endpoint(
        String method,
        String path,
        String specFile,
        List<String> responseStatuses,
        boolean requestContracted,
        boolean responseContracted
    ) {
        public Endpoint {
            responseStatuses = List.copyOf(responseStatuses);
        }

        String key() {
            return method + " " + path;
        }
    }

    private record OpenApiInventory(List<Endpoint> endpoints, List<String> contractIssues) {
        OpenApiInventory {
            endpoints = List.copyOf(endpoints);
            contractIssues = List.copyOf(contractIssues);
        }
    }

    public record TestReference(
        String className,
        String methodName,
        boolean live,
        boolean defaultCi,
        boolean container
    ) {
        String displayName() {
            return methodName.isBlank() ? className : className + "#" + methodName;
        }
    }

    public record CoverageEntry(String method, String path, List<TestReference> tests, String category) {
        public CoverageEntry {
            tests = List.copyOf(tests);
        }

        String key() {
            return method + " " + path;
        }

        String executionProfile() {
            if (defaultCiCovered()) {
                return "default-ci";
            }
            if (tests.stream().anyMatch(TestReference::container)) {
                return "container";
            }
            if (tests.stream().anyMatch(TestReference::live)) {
                return "live-only";
            }
            return "unvalidated";
        }

        boolean defaultCiCovered() {
            return tests.stream().anyMatch(TestReference::defaultCi);
        }
    }

    public record CoverageValidation(List<String> invalidReferences) {
        public CoverageValidation {
            invalidReferences = List.copyOf(invalidReferences);
        }
    }

    public record CoverageReport(
        List<Endpoint> endpoints,
        Map<String, CoverageEntry> coverage,
        List<Endpoint> missingEndpoints,
        List<String> invalidReferences,
        List<Endpoint> nonDefaultCiEndpoints,
        List<String> contractIssues
    ) {
        public CoverageReport {
            endpoints = List.copyOf(endpoints);
            coverage = Map.copyOf(coverage);
            missingEndpoints = List.copyOf(missingEndpoints);
            invalidReferences = List.copyOf(invalidReferences);
            nonDefaultCiEndpoints = List.copyOf(nonDefaultCiEndpoints);
            contractIssues = List.copyOf(contractIssues);
        }

        int coveredCount() {
            return endpoints.size() - missingEndpoints.size();
        }

        int defaultCiCoveredCount() {
            return (int) endpoints.stream()
                .map(endpoint -> coverage.get(endpoint.key()))
                .filter(entry -> entry != null && entry.defaultCiCovered())
                .count();
        }

        int liveOnlyCount() {
            return (int) endpoints.stream()
                .map(endpoint -> coverage.get(endpoint.key()))
                .filter(entry -> entry != null
                    && entry.tests().stream().noneMatch(TestReference::defaultCi)
                    && entry.tests().stream().anyMatch(TestReference::live))
                .count();
        }

        int requestSchemaCount() {
            return (int) endpoints.stream()
                .filter(Endpoint::requestContracted)
                .count();
        }

        int responseSchemaCount() {
            return (int) endpoints.stream()
                .filter(Endpoint::responseContracted)
                .count();
        }
    }
}
