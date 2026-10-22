package com.aria.framework.contracts.claims;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("shared-app")
@Execution(ExecutionMode.SAME_THREAD)
class ReferenceApplicationStateMachineTest {

    private static final List<String> ACTORS = List.of("member-a", "member-b", "reviewer");
    private static final Set<String> SAFE_ERROR_CODES = Set.of(
        "AUTHENTICATION_REQUIRED",
        "ROLE_FORBIDDEN",
        "RESOURCE_NOT_FOUND",
        "VERSION_CONFLICT",
        "INVALID_TRANSITION",
        "VALIDATION_ERROR"
    );
    private static final Set<String> SAFE_STATES = Set.of("submitted", "under_review", "approved", "rejected");
    private static final String CATEGORY = "auto";
    private static final String CURRENCY = "USD";
    private static final String CONTROL_TOKEN_ENV = "TEST_CONTROL_TOKEN";
    private static final String REPLAY_RECEIPT_ENV = "CLAIM_MODEL_REPLAY_RECEIPT";
    private static final String CONTROL_NAME_ENV = "CLAIM_MODEL_CONTROL_NAME";
    private static final String DEFAULT_RECEIPT_ROOT = "work/audit-2026-10-21/F2/sequences";

    private String baseUrl;
    private String frameworkRevision;
    private String targetRevision;
    private String targetDigest;

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    @DisplayName("The independent claims model matches 120 bounded API observations")
    void seededJourneysMatchTheReferenceApplication() throws IOException {
        requireRunIdentity();
        String controlName = System.getenv(CONTROL_NAME_ENV);
        String replayPath = System.getenv(REPLAY_RECEIPT_ENV);
        if (replayPath != null && !replayPath.isBlank()) {
            replay(Path.of(replayPath), controlName);
            return;
        }
        if (controlName != null && !controlName.isBlank()) {
            runSequence(
                ClaimCommandGenerator.SEEDS[0],
                1,
                ClaimCommandGenerator.generate(ClaimCommandGenerator.SEEDS[0], 1),
                controlName,
                null
            );
            return;
        }

        List<ClaimJourneyReceipt> receipts = new ArrayList<>();
        for (int apiVersion : List.of(1, 2)) {
            for (long seed : ClaimCommandGenerator.SEEDS) {
                receipts.add(runSequence(
                    seed,
                    apiVersion,
                    ClaimCommandGenerator.generate(seed, apiVersion),
                    null,
                    null
                ));
            }
        }
        writeBatchSummary(receipts);
    }

    private void replay(Path receiptPath, String controlName) throws IOException {
        ClaimJourneyReceipt source = ClaimJourneyReceiptCodec.read(receiptPath);
        if (source.firstFailingCommandIndex() == null) {
            throw new AssertionError("A replay receipt must contain a retained counterexample.");
        }
        assertThat(source.frameworkRevision()).isEqualTo(frameworkRevision);
        assertThat(source.targetRevision()).isEqualTo(targetRevision);
        assertThat(source.targetDigest()).isEqualTo(targetDigest);
        List<ClaimCommand> commands = source.commands().stream()
            .map(ClaimJourneyReceipt.CommandResult::command)
            .toList();
        runSequence(source.seed(), source.apiVersion(), commands, controlName, source.baselineAliases());
    }

    private ClaimJourneyReceipt runSequence(
        long seed,
        int apiVersion,
        List<ClaimCommand> commands,
        String controlName,
        List<String> expectedBaselineAliases
    ) throws IOException {
        if (expectedBaselineAliases == null) {
            assertThat(commands).hasSize(ClaimCommandGenerator.COMMANDS_PER_SEQUENCE);
        } else {
            assertThat(commands).hasSizeBetween(1, ClaimCommandGenerator.COMMANDS_PER_SEQUENCE);
        }
        resetNamespace();
        Context context = startActorsAndCaptureBaseline(apiVersion);
        if (expectedBaselineAliases != null) {
            assertThat(context.baselineAliases().stream().sorted().toList())
                .containsExactlyElementsOf(expectedBaselineAliases.stream().sorted().toList());
        }

        ClaimJourneyModel model = new ClaimJourneyModel();
        CoverageCounter coverage = new CoverageCounter();
        List<ClaimJourneyReceipt.CommandResult> results = new ArrayList<>();
        Path receiptPath = receiptPath(seed, apiVersion, controlName);
        for (int index = 0; index < commands.size(); index++) {
            ClaimCommand command = commands.get(index);
            ClaimJourneyModel.Expected expected = model.expected(command);
            coverage.record(command, expected, model);
            Response response = invoke(command, context, model);
            if (command.type() == ClaimCommand.Type.CREATE && response.statusCode() == 201) {
                try {
                    String claimId = response.jsonPath().getString("claim.id");
                    if (claimId != null && !claimId.isBlank()) {
                        context.idToAlias().put(claimId, command.claimAlias());
                        context.claimIdsByAlias().put(command.claimAlias(), claimId);
                    }
                } catch (RuntimeException ignored) {
                    // Keep malformed provider output in the sanitized observation below.
                }
            }
            ClaimJourneyReceipt.Observed observed = observeResponse(response, command, context);
            if (!matches(command, expected, observed, context, model)) {
                recordFailure(
                    seed,
                    apiVersion,
                    commands,
                    index,
                    command,
                    expected,
                    observed,
                    "provider_response",
                    null,
                    null,
                    null,
                    context,
                    results,
                    receiptPath,
                    coverage.snapshot()
                );
            }

            model.commit(command, expected);
            ClaimJourneyModel.Expected postconditionExpected = null;
            ClaimJourneyReceipt.Observed postconditionObserved = null;
            String postconditionPhase = null;
            if (command.type() == ClaimCommand.Type.CREATE || command.type() == ClaimCommand.Type.TRANSITION) {
                if (model.snapshot(command.claimAlias()) != null
                    && context.claimIdsByAlias().containsKey(command.claimAlias())) {
                    postconditionExpected = new ClaimJourneyModel.Expected(
                        200,
                        null,
                        model.snapshot(command.claimAlias()),
                        null,
                        model.events(command.claimAlias())
                    );
                    postconditionObserved = observePersistedClaim(command.claimAlias(), context, apiVersion);
                    postconditionPhase = "reviewer_persisted_claim_and_events";
                } else {
                    postconditionExpected = new ClaimJourneyModel.Expected(
                        200,
                        null,
                        null,
                        visibleAliasesWithBaseline("reviewer", model, context),
                        null
                    );
                    postconditionObserved = observeList("reviewer", apiVersion, context);
                    postconditionPhase = "reviewer_list_unchanged";
                }
                if (!matchesPostcondition(postconditionExpected, postconditionObserved)) {
                    recordFailure(
                        seed,
                        apiVersion,
                        commands,
                        index,
                        command,
                        expected,
                        observed,
                        "provider_response",
                        postconditionExpected,
                        postconditionObserved,
                        postconditionPhase,
                        context,
                        results,
                        receiptPath,
                        coverage.snapshot()
                    );
                }
            }
            results.add(new ClaimJourneyReceipt.CommandResult(
                index,
                command,
                expected,
                observed,
                "provider_response",
                postconditionExpected,
                postconditionObserved,
                postconditionPhase
            ));
        }

        ClaimJourneyReceipt receipt = receipt(
            seed,
            apiVersion,
            coverage.snapshot(),
            context.baselineAliases(),
            results,
            null,
            null
        );
        ClaimJourneyReceiptCodec.write(receipt, receiptPath);
        return receipt;
    }

    private void writeBatchSummary(List<ClaimJourneyReceipt> receipts) throws IOException {
        int commandCount = receipts.stream().mapToInt(receipt -> receipt.commands().size()).sum();
        assertThat(receipts).hasSize(4);
        assertThat(commandCount).isEqualTo(120);
        StringBuilder summary = new StringBuilder()
            .append("# F2 claims state-model run\n\n")
            .append("- Framework revision: `").append(frameworkRevision).append("`\n")
            .append("- Target source: `").append(targetRevision).append("`\n")
            .append("- Target image ID: `").append(targetDigest).append("`\n")
            .append("- Generator: `").append(ClaimCommandGenerator.ALGORITHM).append("`\n")
            .append("- Sequences: `").append(receipts.size()).append("`\n")
            .append("- Commands observed: `").append(commandCount).append("`\n")
            .append("- Skipped commands: `0`\n\n")
            .append("| API | Seed | Commands | Command types | Edges | Errors |\n")
            .append("|---|---:|---:|---|---|---|\n");
        for (ClaimJourneyReceipt receipt : receipts) {
            summary.append("| v").append(receipt.apiVersion())
                .append(" | `0x").append(Long.toHexString(receipt.seed())).append('`')
                .append(" | ").append(receipt.commands().size())
                .append(" | `").append(receipt.coverage().commandTypes()).append('`')
                .append(" | `").append(receipt.coverage().transitionEdges()).append('`')
                .append(" | `").append(receipt.coverage().errorCodes()).append("` |\n");
        }
        Path path = Path.of("work/audit-2026-10-21/F2/summary.md");
        Path parent = path.getParent();
        if (parent == null) {
            throw new IOException("The F2 summary path must have a parent directory.");
        }
        Files.createDirectories(parent);
        Files.writeString(path, summary);
    }

    private Context startActorsAndCaptureBaseline(int apiVersion) {
        Map<String, String> tokens = new LinkedHashMap<>();
        for (String actor : ACTORS) {
            Response session = RestAssured.given()
                .baseUri(baseUrl)
                .contentType("application/json")
                .body(Map.of("identityKey", actor))
                .post("/api/v1/sessions");
            assertThat(session.statusCode()).as("session issuance status for %s", actor).isEqualTo(201);
            assertThat(session.jsonPath().getString("user.role"))
                .as("server-assigned role for %s", actor)
                .isEqualTo(actor.equals("reviewer") ? "reviewer" : "member");
            tokens.put(actor, session.jsonPath().getString("token"));
        }

        Map<String, String> policyIds = new HashMap<>();
        for (String member : List.of("member-a", "member-b")) {
            Response policies = request(member, apiVersion, tokens)
                .get(apiPath(apiVersion) + "/policies");
            assertThat(policies.statusCode()).as("policy list for %s", member).isEqualTo(200);
            List<String> ids = policies.jsonPath().getList("items.id", String.class);
            assertThat(ids).as("policy for %s", member).isNotEmpty();
            policyIds.put(member, ids.get(0));
        }

        Map<String, String> idToAlias = new HashMap<>();
        Map<String, Set<String>> baselineIdsByActor = new LinkedHashMap<>();
        Set<String> baselineAliases = new HashSet<>();
        for (String actor : ACTORS) {
            Response initialClaims = request(actor, apiVersion, tokens)
                .get(apiPath(apiVersion) + "/claims");
            assertThat(initialClaims.statusCode()).as("initial claim list for %s", actor).isEqualTo(200);
            Set<String> ids = new HashSet<>(initialClaims.jsonPath().getList("items.id", String.class));
            baselineIdsByActor.put(actor, ids);
            for (String id : ids) {
                String alias = idToAlias.computeIfAbsent(id, ReferenceApplicationStateMachineTest::baselineAlias);
                baselineAliases.add(alias);
            }
        }
        return new Context(tokens, policyIds, idToAlias, new HashMap<>(), baselineIdsByActor, baselineAliases);
    }

    private void resetNamespace() {
        String controlToken = System.getenv(CONTROL_TOKEN_ENV);
        assertThat(controlToken).as("the isolated shared-app reset token").isNotBlank();
        Response reset = RestAssured.given()
            .baseUri(baseUrl)
            .header("x-test-control-token", controlToken)
            .post("/__test/reset");
        assertThat(reset.statusCode()).as("reset only the disposable application namespace").isEqualTo(200);
    }

    private Response invoke(ClaimCommand command, Context context, ClaimJourneyModel model) {
        String root = apiPath(command.apiVersion());
        String claimId = context.claimIdsByAlias().getOrDefault(command.claimAlias(), "missing-claim");
        return switch (command.type()) {
            case CREATE -> request(command.actorAlias(), command.apiVersion(), context.tokens())
                .body(Map.of(
                    "policyId", context.policyIds().get(command.ownerAlias()),
                    "amountMinor", command.amountMinor(),
                    "currency", CURRENCY,
                    "category", CATEGORY
                ))
                .post(root + "/claims");
            case READ -> request(command.actorAlias(), command.apiVersion(), context.tokens())
                .get(root + "/claims/" + claimId);
            case LIST -> request(command.actorAlias(), command.apiVersion(), context.tokens())
                .get(root + "/claims");
            case TRANSITION -> request(command.actorAlias(), command.apiVersion(), context.tokens())
                .body(Map.of(
                    "status", command.targetStatus(),
                    "expectedVersion", expectedVersion(command, model)
                ))
                .patch(root + "/claims/" + claimId);
            case READ_EVENTS -> request(command.actorAlias(), command.apiVersion(), context.tokens())
                .get(root + "/claims/" + claimId + "/events");
        };
    }

    private ClaimJourneyReceipt.Observed observeResponse(
        Response response,
        ClaimCommand command,
        Context context
    ) {
        int status = response.statusCode();
        if (status >= 400) {
            return new ClaimJourneyReceipt.Observed(status, errorCode(response), null, null, null);
        }
        try {
            return switch (command.type()) {
                case CREATE, READ, TRANSITION -> new ClaimJourneyReceipt.Observed(
                    status,
                    null,
                    claimSnapshot(response, "claim"),
                    null,
                    null
                );
                case LIST -> new ClaimJourneyReceipt.Observed(
                    status,
                    null,
                    null,
                    aliasesForIds(response.jsonPath().getList("items.id", String.class), context),
                    null
                );
                case READ_EVENTS -> new ClaimJourneyReceipt.Observed(
                    status,
                    null,
                    null,
                    null,
                    events(response)
                );
            };
        } catch (RuntimeException exception) {
            return new ClaimJourneyReceipt.Observed(status, "MALFORMED_RESPONSE", null, null, null);
        }
    }

    private ClaimJourneyReceipt.Observed observePersistedClaim(
        String claimAlias,
        Context context,
        int apiVersion
    ) {
        String claimId = context.claimIdsByAlias().get(claimAlias);
        Response claimResponse = request("reviewer", apiVersion, context.tokens())
            .get(apiPath(apiVersion) + "/claims/" + claimId);
        if (claimResponse.statusCode() != 200) {
            return new ClaimJourneyReceipt.Observed(
                claimResponse.statusCode(),
                errorCode(claimResponse),
                null,
                null,
                null
            );
        }
        try {
            Response eventResponse = request("reviewer", apiVersion, context.tokens())
                .get(apiPath(apiVersion) + "/claims/" + claimId + "/events");
            if (eventResponse.statusCode() != 200) {
                return new ClaimJourneyReceipt.Observed(
                    eventResponse.statusCode(),
                    errorCode(eventResponse),
                    claimSnapshot(claimResponse, "claim"),
                    null,
                    null
                );
            }
            return new ClaimJourneyReceipt.Observed(
                200,
                null,
                claimSnapshot(claimResponse, "claim"),
                null,
                events(eventResponse)
            );
        } catch (RuntimeException exception) {
            return new ClaimJourneyReceipt.Observed(200, "MALFORMED_RESPONSE", null, null, null);
        }
    }

    private ClaimJourneyReceipt.Observed observeList(
        String actor,
        int apiVersion,
        Context context
    ) {
        Response response = request(actor, apiVersion, context.tokens()).get(apiPath(apiVersion) + "/claims");
        if (response.statusCode() != 200) {
            return new ClaimJourneyReceipt.Observed(response.statusCode(), errorCode(response), null, null, null);
        }
        try {
            List<String> ids = response.jsonPath().getList("items.id", String.class);
            if (ids == null) {
                return new ClaimJourneyReceipt.Observed(200, "MALFORMED_RESPONSE", null, null, null);
            }
            return new ClaimJourneyReceipt.Observed(
                200,
                null,
                null,
                aliasesForIds(ids, context),
                null
            );
        } catch (RuntimeException exception) {
            return new ClaimJourneyReceipt.Observed(200, "MALFORMED_RESPONSE", null, null, null);
        }
    }

    private boolean matches(
        ClaimCommand command,
        ClaimJourneyModel.Expected expected,
        ClaimJourneyReceipt.Observed observed,
        Context context,
        ClaimJourneyModel model
    ) {
        if (expected.status() != observed.status() || !equalNullable(expected.errorCode(), observed.errorCode())) {
            return false;
        }
        if (expected.status() >= 400) {
            return true;
        }
        if (command.type() == ClaimCommand.Type.LIST) {
            return visibleAliasesWithBaseline(command.actorAlias(), model, context).equals(observed.visibleClaims());
        }
        if (expected.claim() != null && !expected.claim().equals(observed.claim())) {
            return false;
        }
        return command.type() != ClaimCommand.Type.READ_EVENTS || expected.events().equals(observed.events());
    }

    private static boolean matchesPostcondition(
        ClaimJourneyModel.Expected expected,
        ClaimJourneyReceipt.Observed observed
    ) {
        if (expected.status() != observed.status() || !equalNullable(expected.errorCode(), observed.errorCode())) {
            return false;
        }
        if (expected.claim() != null && !expected.claim().equals(observed.claim())) {
            return false;
        }
        if (expected.visibleClaims() != null && !expected.visibleClaims().equals(observed.visibleClaims())) {
            return false;
        }
        return expected.events() == null || expected.events().equals(observed.events());
    }

    private List<String> visibleAliasesWithBaseline(String actor, ClaimJourneyModel model, Context context) {
        Set<String> aliases = new HashSet<>(model.expected(ClaimCommand.list(actor, 1)).visibleClaims());
        for (String id : context.baselineIdsByActor().get(actor)) {
            aliases.add(context.idToAlias().get(id));
        }
        return aliases.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private List<String> aliasesForIds(List<String> ids, Context context) {
        if (ids == null) {
            throw new IllegalArgumentException("Claim list response is missing its item identifiers.");
        }
        return ids.stream()
            .map(id -> context.idToAlias().computeIfAbsent(id, ReferenceApplicationStateMachineTest::unmappedAlias))
            .sorted()
            .toList();
    }

    private static ClaimJourneyModel.ClaimSnapshot claimSnapshot(Response response, String prefix) {
        return new ClaimJourneyModel.ClaimSnapshot(
            safeStatus(response.jsonPath().getString(prefix + ".status")),
            response.jsonPath().getInt(prefix + ".version"),
            response.jsonPath().getLong(prefix + ".amountMinor")
        );
    }

    private static List<ClaimJourneyModel.EventSnapshot> events(Response response) {
        List<?> items = response.jsonPath().getList("items");
        if (items == null) {
            throw new IllegalArgumentException("Claim events response is missing its item list.");
        }
        List<ClaimJourneyModel.EventSnapshot> result = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            String prefix = "items[" + index + "].";
            String fromStatus = response.jsonPath().getString(prefix + "fromStatus");
            result.add(new ClaimJourneyModel.EventSnapshot(
                fromStatus == null ? null : safeStatus(fromStatus),
                safeStatus(response.jsonPath().getString(prefix + "toStatus")),
                response.jsonPath().getInt(prefix + "claimVersion"),
                safeRole(response.jsonPath().getString(prefix + "actorRole"))
            ));
        }
        return List.copyOf(result);
    }

    private void recordFailure(
        long seed,
        int apiVersion,
        List<ClaimCommand> commands,
        int index,
        ClaimCommand command,
        ClaimJourneyModel.Expected expected,
        ClaimJourneyReceipt.Observed observed,
        String observationPhase,
        ClaimJourneyModel.Expected postconditionExpected,
        ClaimJourneyReceipt.Observed postconditionObserved,
        String postconditionPhase,
        Context context,
        List<ClaimJourneyReceipt.CommandResult> completed,
        Path path,
        ClaimJourneyReceipt.Coverage coverage
    ) throws IOException {
        ClaimJourneyReceipt.CommandResult failure = new ClaimJourneyReceipt.CommandResult(
            index,
            command,
            expected,
            observed,
            observationPhase,
            postconditionExpected,
            postconditionObserved,
            postconditionPhase
        );
        List<ClaimJourneyReceipt.CommandResult> results = new ArrayList<>(completed);
        results.add(failure);
        String difference = describeDifference(expected, observed, postconditionExpected, postconditionObserved);
        ClaimJourneyReceiptCodec.write(
            receipt(seed, apiVersion, coverage, context.baselineAliases(), results, index, difference),
            path
        );
        throw new AssertionError(
            "F2 divergence at command " + index + " (" + command.type() + ", " + command.actorAlias()
                + "): " + difference
        );
    }

    private ClaimJourneyReceipt receipt(
        long seed,
        int apiVersion,
        ClaimJourneyReceipt.Coverage coverage,
        Set<String> baselineAliases,
        List<ClaimJourneyReceipt.CommandResult> commands,
        Integer firstFailingCommandIndex,
        String divergence
    ) {
        return new ClaimJourneyReceipt(
            1,
            seed,
            apiVersion,
            ClaimCommandGenerator.ALGORITHM,
            frameworkRevision,
            targetRevision,
            targetDigest,
            coverage,
            baselineAliases.stream().sorted().toList(),
            commands,
            firstFailingCommandIndex,
            divergence
        );
    }

    private Path receiptPath(long seed, int apiVersion, String controlName) {
        String filename = "api-v" + apiVersion + "-seed-" + Long.toHexString(seed) + ".json";
        String replay = System.getenv(REPLAY_RECEIPT_ENV);
        if (controlName != null && !controlName.isBlank()) {
            return Path.of("work/audit-2026-10-21/F2/controls", controlName,
                replay == null || replay.isBlank() ? "counterexample.json" : "replay.json");
        }
        return Path.of(DEFAULT_RECEIPT_ROOT, filename);
    }

    private String describeDifference(
        ClaimJourneyModel.Expected expected,
        ClaimJourneyReceipt.Observed observed,
        ClaimJourneyModel.Expected postconditionExpected,
        ClaimJourneyReceipt.Observed postconditionObserved
    ) {
        String difference = "expected " + summary(expected.status(), expected.errorCode(), expected.claim())
            + "; observed " + summary(observed.status(), observed.errorCode(), observed.claim());
        if (expected.visibleClaims() != null || observed.visibleClaims() != null) {
            difference += "; visible aliases expected=" + safeAliases(expected.visibleClaims())
                + " observed=" + safeAliases(observed.visibleClaims());
        }
        if (expected.events() != null || observed.events() != null) {
            difference += "; events expected=" + safeEvents(expected.events())
                + " observed=" + safeEvents(observed.events());
        }
        if (postconditionExpected != null) {
            difference += "; postcondition expected "
                + summary(postconditionExpected.status(), postconditionExpected.errorCode(), postconditionExpected.claim())
                + "; observed "
                + summary(postconditionObserved.status(), postconditionObserved.errorCode(), postconditionObserved.claim());
            if (postconditionExpected.visibleClaims() != null || postconditionObserved.visibleClaims() != null) {
                difference += "; visible aliases expected=" + safeAliases(postconditionExpected.visibleClaims())
                    + " observed=" + safeAliases(postconditionObserved.visibleClaims());
            }
            if (postconditionExpected.events() != null || postconditionObserved.events() != null) {
                difference += "; events expected=" + safeEvents(postconditionExpected.events())
                    + " observed=" + safeEvents(postconditionObserved.events());
            }
        }
        return difference;
    }

    private static String summary(int status, String errorCode, ClaimJourneyModel.ClaimSnapshot claim) {
        String state = claim == null ? "none" : claim.status() + " version=" + claim.version()
            + " amountMinor=" + claim.amountMinor();
        return "status=" + status + " error=" + (errorCode == null ? "none" : errorCode) + " state=" + state;
    }

    private static List<String> safeAliases(List<String> aliases) {
        return aliases == null ? List.of() : aliases;
    }

    private static List<String> safeEvents(List<ClaimJourneyModel.EventSnapshot> events) {
        return events == null ? List.of() : events.stream()
            .map(event -> (event.fromStatus() == null ? "none" : event.fromStatus()) + "->" + event.toStatus()
                + "@" + event.claimVersion() + ":" + event.actorRole())
            .toList();
    }

    private static int expectedVersion(ClaimCommand command, ClaimJourneyModel model) {
        int current = model.version(command.claimAlias());
        return switch (command.expectedVersionMode()) {
            case CURRENT -> current;
            case STALE -> Math.max(1, current - 1);
            case FUTURE -> current + 1;
        };
    }

    private static io.restassured.specification.RequestSpecification request(
        String actor,
        int apiVersion,
        Map<String, String> tokens
    ) {
        return RestAssured.given()
            .baseUri(System.getenv("APP_BASE_URL"))
            .contentType("application/json")
            .accept("application/json")
            .auth().oauth2(tokens.get(actor));
    }

    private static String apiPath(int version) {
        return "/api/v" + version;
    }

    private static String errorCode(Response response) {
        try {
            String code = response.jsonPath().getString("error.code");
            return SAFE_ERROR_CODES.contains(code) ? code : "UNEXPECTED_ERROR_CODE";
        } catch (RuntimeException exception) {
            return "MALFORMED_ERROR";
        }
    }

    private void requireRunIdentity() {
        baseUrl = System.getenv("APP_BASE_URL");
        frameworkRevision = System.getenv("FRAMEWORK_SOURCE_REVISION");
        targetRevision = System.getenv("APP_SOURCE_REVISION");
        targetDigest = System.getenv("APP_IMAGE_DIGEST");
        assertThat(baseUrl).as("APP_BASE_URL must point to the isolated pinned application").isNotBlank();
        assertThat(frameworkRevision).as("the ARIA source revision is recorded").isNotBlank();
        assertThat(targetRevision).as("the application source revision is recorded").isNotBlank();
        assertThat(targetDigest).as("the built application image ID is recorded").startsWith("sha256:");
    }

    private static String baselineAlias(String id) {
        return "baseline-" + shortHash(id);
    }

    private static String unmappedAlias(String id) {
        return "unmapped-" + shortHash(id);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int index = 0; index < 6; index++) {
                hex.append(String.format("%02x", digest[index]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK.", exception);
        }
    }

    private static boolean equalNullable(Object expected, Object observed) {
        return expected == null ? observed == null : expected.equals(observed);
    }

    private static String safeStatus(String status) {
        return SAFE_STATES.contains(status) ? status : "UNEXPECTED_STATUS";
    }

    private static String safeRole(String role) {
        return Set.of("member", "reviewer").contains(role) ? role : "UNEXPECTED_ROLE";
    }

    private static final class CoverageCounter {
        private final Map<String, Integer> commandTypes = new HashMap<>();
        private final Map<String, Integer> transitionEdges = new HashMap<>();
        private final Map<String, Integer> errorCodes = new HashMap<>();
        private final Map<String, Integer> apiVersions = new HashMap<>();

        private void record(
            ClaimCommand command,
            ClaimJourneyModel.Expected expected,
            ClaimJourneyModel model
        ) {
            commandTypes.merge(command.type().name(), 1, Integer::sum);
            apiVersions.merge("v" + command.apiVersion(), 1, Integer::sum);
            if (expected.errorCode() != null) {
                errorCodes.merge(expected.errorCode(), 1, Integer::sum);
            }
            if (command.type() == ClaimCommand.Type.TRANSITION) {
                ClaimJourneyModel.ClaimSnapshot current = model.snapshot(command.claimAlias());
                String from = current == null ? "missing" : current.status();
                transitionEdges.merge(from + "->" + command.targetStatus(), 1, Integer::sum);
            }
        }

        private ClaimJourneyReceipt.Coverage snapshot() {
            return new ClaimJourneyReceipt.Coverage(commandTypes, transitionEdges, errorCodes, apiVersions);
        }
    }

    private record Context(
        Map<String, String> tokens,
        Map<String, String> policyIds,
        Map<String, String> idToAlias,
        Map<String, String> claimIdsByAlias,
        Map<String, Set<String>> baselineIdsByActor,
        Set<String> baselineAliases
    ) {
    }
}
