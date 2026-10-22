package com.aria.framework.contracts.claims;

import java.util.Objects;
import java.util.Set;

/** A replayable business action expressed only with aliases, never runtime identifiers. */
public record ClaimCommand(
    Type type,
    String actorAlias,
    String claimAlias,
    String ownerAlias,
    int apiVersion,
    long amountMinor,
    String targetStatus,
    ExpectedVersionMode expectedVersionMode
) {

    private static final Set<String> ACTORS = Set.of("member-a", "member-b", "reviewer");
    private static final Set<String> STATES = Set.of("submitted", "under_review", "approved", "rejected");

    public enum Type {
        CREATE,
        READ,
        LIST,
        TRANSITION,
        READ_EVENTS
    }

    public enum ExpectedVersionMode {
        CURRENT,
        STALE,
        FUTURE
    }

    public ClaimCommand {
        Objects.requireNonNull(type, "type");
        if (!ACTORS.contains(actorAlias)) {
            throw new IllegalArgumentException("Unknown actor alias.");
        }
        if (apiVersion != 1 && apiVersion != 2) {
            throw new IllegalArgumentException("API version must be 1 or 2.");
        }
        if (type == Type.CREATE) {
            if (!actorAlias.equals(ownerAlias) || !actorAlias.startsWith("member-")) {
                throw new IllegalArgumentException("Claims can only be created for the acting member alias.");
            }
            if (claimAlias == null || claimAlias.isBlank()) {
                throw new IllegalArgumentException("Create requires a claim alias.");
            }
        } else if (ownerAlias != null || amountMinor != 0) {
            throw new IllegalArgumentException("Only create commands can contain owner or amount fields.");
        }
        if (type == Type.LIST && claimAlias != null) {
            throw new IllegalArgumentException("List cannot name a claim alias.");
        }
        if ((type == Type.READ || type == Type.READ_EVENTS || type == Type.TRANSITION)
            && (claimAlias == null || claimAlias.isBlank())) {
            throw new IllegalArgumentException("This command requires a claim alias.");
        }
        if (type == Type.TRANSITION) {
            if (!STATES.contains(targetStatus) || expectedVersionMode == null) {
                throw new IllegalArgumentException("Transition requires a known state and version mode.");
            }
        } else if (targetStatus != null || expectedVersionMode != null) {
            throw new IllegalArgumentException("Only transition commands can contain transition fields.");
        }
    }

    public static ClaimCommand create(String ownerAlias, String claimAlias, int apiVersion, long amountMinor) {
        return new ClaimCommand(Type.CREATE, ownerAlias, claimAlias, ownerAlias, apiVersion, amountMinor, null, null);
    }

    public static ClaimCommand read(String actorAlias, String claimAlias, int apiVersion) {
        return new ClaimCommand(Type.READ, actorAlias, claimAlias, null, apiVersion, 0, null, null);
    }

    public static ClaimCommand list(String actorAlias, int apiVersion) {
        return new ClaimCommand(Type.LIST, actorAlias, null, null, apiVersion, 0, null, null);
    }

    public static ClaimCommand transition(
        String actorAlias,
        String claimAlias,
        int apiVersion,
        String targetStatus,
        ExpectedVersionMode expectedVersionMode
    ) {
        return new ClaimCommand(
            Type.TRANSITION,
            actorAlias,
            claimAlias,
            null,
            apiVersion,
            0,
            targetStatus,
            expectedVersionMode
        );
    }

    public static ClaimCommand readEvents(String actorAlias, String claimAlias, int apiVersion) {
        return new ClaimCommand(Type.READ_EVENTS, actorAlias, claimAlias, null, apiVersion, 0, null, null);
    }
}
