package com.aria.framework.contracts.claims;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test-owned claims rules. Keep this independent from the application's transition implementation. */
public final class ClaimJourneyModel {

    public static final long API_V1_LIMIT_MINOR = 1_000_000;
    public static final long API_V2_LIMIT_MINOR = 500_000;

    public record ClaimSnapshot(String status, int version, long amountMinor) {
    }

    public record EventSnapshot(String fromStatus, String toStatus, int claimVersion, String actorRole) {
    }

    public record Expected(
        int status,
        String errorCode,
        ClaimSnapshot claim,
        List<String> visibleClaims,
        List<EventSnapshot> events
    ) {
        public Expected {
            visibleClaims = visibleClaims == null ? null : List.copyOf(visibleClaims);
            events = events == null ? null : List.copyOf(events);
        }
    }

    private static final Map<String, List<String>> ALLOWED_TRANSITIONS = Map.of(
        "submitted", List.of("under_review"),
        "under_review", List.of("approved", "rejected"),
        "approved", List.of(),
        "rejected", List.of()
    );

    private final Map<String, Claim> claims = new LinkedHashMap<>();

    public Expected expected(ClaimCommand command) {
        return switch (command.type()) {
            case CREATE -> expectedCreate(command);
            case READ -> expectedRead(command);
            case LIST -> new Expected(
                200,
                null,
                null,
                visibleAliases(command.actorAlias()),
                null
            );
            case TRANSITION -> expectedTransition(command);
            case READ_EVENTS -> expectedEvents(command);
        };
    }

    /** Commits only an accepted model result, after the hosted provider matched the oracle. */
    public void commit(ClaimCommand command, Expected expected) {
        if (command.type() == ClaimCommand.Type.CREATE && expected.status() == 201) {
            Claim claim = new Claim(command.claimAlias(), command.ownerAlias(), command.amountMinor());
            claims.put(command.claimAlias(), claim);
            claim.events.add(new EventSnapshot(null, "submitted", 1, "member"));
            return;
        }
        if (command.type() == ClaimCommand.Type.TRANSITION && expected.status() == 200) {
            Claim claim = claims.get(command.claimAlias());
            String fromStatus = claim.status;
            claim.status = command.targetStatus();
            claim.version += 1;
            claim.events.add(new EventSnapshot(fromStatus, claim.status, claim.version, "reviewer"));
        }
    }

    public ClaimSnapshot snapshot(String claimAlias) {
        Claim claim = claims.get(claimAlias);
        return claim == null ? null : new ClaimSnapshot(claim.status, claim.version, claim.amountMinor);
    }

    public List<EventSnapshot> events(String claimAlias) {
        Claim claim = claims.get(claimAlias);
        return claim == null ? List.of() : List.copyOf(claim.events);
    }

    public List<String> claimAliases() {
        return claims.keySet().stream().sorted().toList();
    }

    public String ownerAlias(String claimAlias) {
        Claim claim = claims.get(claimAlias);
        return claim == null ? null : claim.ownerAlias;
    }

    public int version(String claimAlias) {
        Claim claim = claims.get(claimAlias);
        return claim == null ? 1 : claim.version;
    }

    public static long limitForApi(int apiVersion) {
        return switch (apiVersion) {
            case 1 -> API_V1_LIMIT_MINOR;
            case 2 -> API_V2_LIMIT_MINOR;
            default -> throw new IllegalArgumentException("API version must be 1 or 2.");
        };
    }

    private Expected expectedCreate(ClaimCommand command) {
        long limit = limitForApi(command.apiVersion());
        if (command.amountMinor() < 1 || command.amountMinor() > limit) {
            return error(422, "VALIDATION_ERROR");
        }
        return new Expected(201, null, new ClaimSnapshot("submitted", 1, command.amountMinor()), null, null);
    }

    private Expected expectedRead(ClaimCommand command) {
        Claim claim = visibleClaim(command.actorAlias(), command.claimAlias());
        return claim == null ? error(404, "RESOURCE_NOT_FOUND") : successClaim(claim);
    }

    private Expected expectedTransition(ClaimCommand command) {
        // The provider checks reviewer authorization before looking up the claim or its version.
        if (!"reviewer".equals(command.actorAlias())) {
            return error(403, "ROLE_FORBIDDEN");
        }
        Claim claim = claims.get(command.claimAlias());
        if (claim == null) {
            return error(404, "RESOURCE_NOT_FOUND");
        }
        int expectedVersion = switch (command.expectedVersionMode()) {
            case CURRENT -> claim.version;
            case STALE -> Math.max(1, claim.version - 1);
            case FUTURE -> claim.version + 1;
        };
        if (expectedVersion != claim.version) {
            return error(409, "VERSION_CONFLICT");
        }
        if (!ALLOWED_TRANSITIONS.get(claim.status).contains(command.targetStatus())) {
            return error(409, "INVALID_TRANSITION");
        }
        List<EventSnapshot> nextEvents = new ArrayList<>(claim.events);
        nextEvents.add(new EventSnapshot(claim.status, command.targetStatus(), claim.version + 1, "reviewer"));
        return new Expected(
            200,
            null,
            new ClaimSnapshot(command.targetStatus(), claim.version + 1, claim.amountMinor),
            null,
            nextEvents
        );
    }

    private Expected expectedEvents(ClaimCommand command) {
        Claim claim = visibleClaim(command.actorAlias(), command.claimAlias());
        return claim == null
            ? error(404, "RESOURCE_NOT_FOUND")
            : new Expected(200, null, null, null, claim.events);
    }

    private Expected successClaim(Claim claim) {
        return new Expected(
            200,
            null,
            new ClaimSnapshot(claim.status, claim.version, claim.amountMinor),
            null,
            null
        );
    }

    private Claim visibleClaim(String actorAlias, String claimAlias) {
        Claim claim = claims.get(claimAlias);
        if (claim == null) {
            return null;
        }
        return "reviewer".equals(actorAlias) || claim.ownerAlias.equals(actorAlias) ? claim : null;
    }

    private List<String> visibleAliases(String actorAlias) {
        return claims.values().stream()
            .filter(claim -> "reviewer".equals(actorAlias) || claim.ownerAlias.equals(actorAlias))
            .map(claim -> claim.alias)
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    private static Expected error(int status, String code) {
        return new Expected(status, code, null, null, null);
    }

    private static final class Claim {
        private final String alias;
        private final String ownerAlias;
        private final long amountMinor;
        private final List<EventSnapshot> events = new ArrayList<>();
        private String status = "submitted";
        private int version = 1;

        private Claim(String alias, String ownerAlias, long amountMinor) {
            this.alias = alias;
            this.ownerAlias = ownerAlias;
            this.amountMinor = amountMinor;
        }
    }
}
