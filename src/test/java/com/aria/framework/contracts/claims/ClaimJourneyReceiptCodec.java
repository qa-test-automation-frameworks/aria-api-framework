package com.aria.framework.contracts.claims;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict receipt codec for deterministic replay and sanitized retained evidence. */
public final class ClaimJourneyReceiptCodec {

    private static final Pattern CLAIM_ALIAS = Pattern.compile("(?:a-seed|b-seed|a-over-limit|missing-claim|generated-[0-9]+)");
    private static final Pattern BASELINE_ALIAS = Pattern.compile("baseline-[0-9a-f]{12}");
    private static final Pattern UNMAPPED_ALIAS = Pattern.compile("unmapped-[0-9a-f]{12}");
    private static final Pattern SHA256 = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Set<String> STATES = Set.of("submitted", "under_review", "approved", "rejected");
    private static final Set<String> ROLES = Set.of("member", "reviewer");
    private static final Set<String> EXPECTED_ERRORS = Set.of(
        "AUTHENTICATION_REQUIRED",
        "ROLE_FORBIDDEN",
        "RESOURCE_NOT_FOUND",
        "VERSION_CONFLICT",
        "INVALID_TRANSITION",
        "VALIDATION_ERROR"
    );
    private static final Set<String> OBSERVED_ERRORS = Set.of(
        "AUTHENTICATION_REQUIRED",
        "ROLE_FORBIDDEN",
        "RESOURCE_NOT_FOUND",
        "VERSION_CONFLICT",
        "INVALID_TRANSITION",
        "VALIDATION_ERROR",
        "MALFORMED_ERROR",
        "UNEXPECTED_ERROR_CODE",
        "MALFORMED_RESPONSE"
    );

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    private ClaimJourneyReceiptCodec() {
    }

    public static String write(ClaimJourneyReceipt receipt, Path destination) throws IOException {
        validate(receipt);
        if (destination == null) {
            throw new IOException("Replay receipt destination is required.");
        }
        Path absolute = destination.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("Replay receipt destination must have a parent directory.");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".f2-receipt-", ".tmp");
        try {
            String json = serialize(receipt) + System.lineSeparator();
            Files.writeString(temporary, json);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            return json;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static String serialize(ClaimJourneyReceipt receipt) throws IOException {
        validate(receipt);
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(receipt);
        } catch (JsonProcessingException exception) {
            throw new IOException("Replay receipt could not be serialized.", exception);
        }
    }

    public static ClaimJourneyReceipt read(Path source) throws IOException {
        ClaimJourneyReceipt receipt;
        try {
            receipt = MAPPER.readValue(source.toFile(), ClaimJourneyReceipt.class);
        } catch (JsonProcessingException exception) {
            throw new IOException("Replay receipt is malformed or contains an unknown command field.", exception);
        }
        validate(receipt);
        return receipt;
    }

    public static ClaimJourneyReceipt parse(String json) throws IOException {
        ClaimJourneyReceipt receipt;
        try {
            receipt = MAPPER.readValue(json, ClaimJourneyReceipt.class);
        } catch (JsonProcessingException exception) {
            throw new IOException("Replay receipt is malformed or contains an unknown command field.", exception);
        }
        validate(receipt);
        return receipt;
    }

    private static void validate(ClaimJourneyReceipt receipt) throws IOException {
        if (receipt == null || receipt.schemaVersion() != 1 || (receipt.apiVersion() != 1 && receipt.apiVersion() != 2)) {
            throw new IOException("Replay receipt has an unsupported schema or API version.");
        }
        if (receipt.seed() != ClaimCommandGenerator.SEEDS[0] && receipt.seed() != ClaimCommandGenerator.SEEDS[1]) {
            throw new IOException("Replay receipt uses an uncommitted command seed.");
        }
        if (!ClaimCommandGenerator.ALGORITHM.equals(receipt.generator())) {
            throw new IOException("Replay receipt names an unsupported command generator.");
        }
        if (isBlank(receipt.frameworkRevision()) || isBlank(receipt.targetRevision()) || isBlank(receipt.targetDigest())
            || !SHA256.matcher(receipt.targetDigest()).matches()) {
            throw new IOException("Replay receipt is missing framework or target identity.");
        }
        if (receipt.baselineAliases() == null || receipt.baselineAliases().stream()
            .anyMatch(alias -> alias == null || !BASELINE_ALIAS.matcher(alias).matches())) {
            throw new IOException("Replay receipt contains an unknown baseline alias.");
        }
        if (receipt.baselineAliases().stream().distinct().count() != receipt.baselineAliases().size()) {
            throw new IOException("Replay receipt repeats a baseline alias.");
        }
        if (receipt.coverage() == null || !nonNegative(receipt.coverage().commandTypes())
            || !nonNegative(receipt.coverage().transitionEdges()) || !nonNegative(receipt.coverage().errorCodes())
            || !nonNegative(receipt.coverage().apiVersions())) {
            throw new IOException("Replay receipt has invalid seed coverage counts.");
        }
        List<ClaimJourneyReceipt.CommandResult> commands = receipt.commands();
        if (commands == null || commands.isEmpty()) {
            throw new IOException("Replay receipt has no commands.");
        }
        if (commands.size() > ClaimCommandGenerator.COMMANDS_PER_SEQUENCE) {
            throw new IOException("Replay receipt exceeds the bounded command sequence.");
        }
        Set<String> createdAliases = new HashSet<>();
        Set<String> seenCreateAliases = new HashSet<>();
        for (int index = 0; index < commands.size(); index++) {
            ClaimJourneyReceipt.CommandResult result = commands.get(index);
            if (result == null || result.commandIndex() != index || result.command() == null
                || result.expected() == null || result.observed() == null || isBlank(result.observationPhase())) {
                throw new IOException("Replay receipt contains an incomplete or out-of-order command result.");
            }
            ClaimCommand command = result.command();
            if (command.apiVersion() != receipt.apiVersion()) {
                throw new IOException("Replay receipt command uses a different API version.");
            }
            String claimAlias = command.claimAlias();
            if (claimAlias != null && !CLAIM_ALIAS.matcher(claimAlias).matches()) {
                throw new IOException("Replay receipt contains an unknown claim alias.");
            }
            if (command.type() == ClaimCommand.Type.CREATE) {
                if (claimAlias.equals("missing-claim")) {
                    throw new IOException("Replay receipt uses the missing-claim sentinel as a create alias.");
                }
                if (!seenCreateAliases.add(claimAlias)) {
                    throw new IOException("Replay receipt reuses a claim alias.");
                }
                if (result.expected().status() == 201) {
                    createdAliases.add(claimAlias);
                }
            } else if (claimAlias != null && !claimAlias.equals("missing-claim")
                && !createdAliases.contains(claimAlias)) {
                throw new IOException("Replay receipt references a claim alias before it is created.");
            }
            validateExpected(result.expected());
            validateObserved(result.observed());
            boolean hasPostcondition = result.postconditionExpected() != null || result.postconditionObserved() != null;
            if (hasPostcondition && (result.postconditionExpected() == null || result.postconditionObserved() == null
                || isBlank(result.postconditionPhase()))) {
                throw new IOException("Replay receipt contains an incomplete mutation postcondition.");
            }
            if (hasPostcondition) {
                validateExpected(result.postconditionExpected());
                validateObserved(result.postconditionObserved());
            }
        }
        Integer failureIndex = receipt.firstFailingCommandIndex();
        if (failureIndex != null && (failureIndex < 0 || failureIndex >= commands.size())) {
            throw new IOException("Replay receipt has an invalid first failing command index.");
        }
        if (failureIndex != null && failureIndex != commands.size() - 1) {
            throw new IOException("Replay receipt contains commands after its first divergence.");
        }
        if ((failureIndex == null) != (receipt.divergence() == null)) {
            throw new IOException("Replay receipt divergence and first failing command must be recorded together.");
        }
        if (receipt.divergence() != null && receipt.divergence().length() > 4096) {
            throw new IOException("Replay receipt divergence exceeds its safe size limit.");
        }
    }

    private static void validateExpected(ClaimJourneyModel.Expected expected) throws IOException {
        if (expected.errorCode() != null && !EXPECTED_ERRORS.contains(expected.errorCode())) {
            throw new IOException("Replay receipt contains an unknown expected error code.");
        }
        validateSnapshot(expected.claim());
        validateEvents(expected.events());
        validateAliases(expected.visibleClaims());
    }

    private static void validateObserved(ClaimJourneyReceipt.Observed observed) throws IOException {
        if (observed.errorCode() != null && !OBSERVED_ERRORS.contains(observed.errorCode())) {
            throw new IOException("Replay receipt contains an unsafe observed error code.");
        }
        validateSnapshot(observed.claim());
        validateEvents(observed.events());
        validateAliases(observed.visibleClaims());
    }

    private static void validateSnapshot(ClaimJourneyModel.ClaimSnapshot snapshot) throws IOException {
        if (snapshot != null && (snapshot.status() == null
            || (!STATES.contains(snapshot.status()) && !"UNEXPECTED_STATUS".equals(snapshot.status())))) {
            throw new IOException("Replay receipt contains an unknown claim state.");
        }
    }

    private static void validateEvents(List<ClaimJourneyModel.EventSnapshot> events) throws IOException {
        if (events == null) return;
        for (ClaimJourneyModel.EventSnapshot event : events) {
            if (event == null || (event.fromStatus() != null && !safeStatus(event.fromStatus()))
                || !safeStatus(event.toStatus()) || event.actorRole() == null
                || (!ROLES.contains(event.actorRole()) && !"UNEXPECTED_ROLE".equals(event.actorRole()))) {
                throw new IOException("Replay receipt contains an unsafe claim event.");
            }
        }
    }

    private static boolean safeStatus(String status) {
        return status != null && (STATES.contains(status) || "UNEXPECTED_STATUS".equals(status));
    }

    private static void validateAliases(List<String> aliases) throws IOException {
        if (aliases == null) return;
        for (String alias : aliases) {
            if (alias == null || !(CLAIM_ALIAS.matcher(alias).matches()
                || BASELINE_ALIAS.matcher(alias).matches() || UNMAPPED_ALIAS.matcher(alias).matches())) {
                throw new IOException("Replay receipt contains an unsafe visible claim alias.");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean nonNegative(java.util.Map<String, Integer> counts) {
        return counts != null && counts.values().stream().allMatch(value -> value != null && value >= 0);
    }
}
