package com.aria.framework.contracts.claims;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.TreeMap;

/** Sanitized run evidence. Runtime sessions, policy IDs, claim IDs, headers, and response text are excluded. */
public record ClaimJourneyReceipt(
    int schemaVersion,
    long seed,
    int apiVersion,
    String generator,
    String frameworkRevision,
    String targetRevision,
    String targetDigest,
    Coverage coverage,
    List<String> baselineAliases,
    List<CommandResult> commands,
    Integer firstFailingCommandIndex,
    String divergence
) {

    public ClaimJourneyReceipt {
        baselineAliases = List.copyOf(baselineAliases);
        commands = List.copyOf(commands);
    }

    public record CommandResult(
        int commandIndex,
        ClaimCommand command,
        ClaimJourneyModel.Expected expected,
        Observed observed,
        String observationPhase,
        ClaimJourneyModel.Expected postconditionExpected,
        Observed postconditionObserved,
        String postconditionPhase
    ) {
    }

    public record Observed(
        int status,
        String errorCode,
        ClaimJourneyModel.ClaimSnapshot claim,
        List<String> visibleClaims,
        List<ClaimJourneyModel.EventSnapshot> events
    ) {
        public Observed {
            visibleClaims = visibleClaims == null ? null : List.copyOf(visibleClaims);
            events = events == null ? null : List.copyOf(events);
        }
    }

    public record Coverage(
        Map<String, Integer> commandTypes,
        Map<String, Integer> transitionEdges,
        Map<String, Integer> errorCodes,
        Map<String, Integer> apiVersions
    ) {
        public Coverage {
            commandTypes = Collections.unmodifiableMap(new TreeMap<>(commandTypes));
            transitionEdges = Collections.unmodifiableMap(new TreeMap<>(transitionEdges));
            errorCodes = Collections.unmodifiableMap(new TreeMap<>(errorCodes));
            apiVersions = Collections.unmodifiableMap(new TreeMap<>(apiVersions));
        }
    }
}
