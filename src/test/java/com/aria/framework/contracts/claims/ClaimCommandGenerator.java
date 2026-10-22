package com.aria.framework.contracts.claims;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Bounded command generation. The committed seed and java.util.Random define replay order. */
public final class ClaimCommandGenerator {

    static final long[] SEEDS = {0x51DE7L, 0xC1A1L};
    public static final int COMMANDS_PER_SEQUENCE = 30;
    public static final int MAX_GENERATED_CLAIMS = 4;
    public static final String ALGORITHM = "java.util.Random / Java 21";

    private ClaimCommandGenerator() {
    }

    public static List<ClaimCommand> generate(long seed, int apiVersion) {
        Random random = new Random(seed);
        ClaimJourneyModel model = new ClaimJourneyModel();
        List<ClaimCommand> commands = new ArrayList<>(COMMANDS_PER_SEQUENCE);
        long limit = ClaimJourneyModel.limitForApi(apiVersion);
        List<ClaimCommand> fixedPrefix = List.of(
            ClaimCommand.create("member-a", "a-seed", apiVersion, 1),
            ClaimCommand.create("member-b", "b-seed", apiVersion, limit),
            ClaimCommand.read("member-b", "a-seed", apiVersion),
            ClaimCommand.readEvents("member-b", "a-seed", apiVersion),
            ClaimCommand.list("member-b", apiVersion),
            ClaimCommand.read("member-a", "a-seed", apiVersion),
            ClaimCommand.list("member-a", apiVersion),
            ClaimCommand.transition("member-a", "missing-claim", apiVersion, "under_review", ClaimCommand.ExpectedVersionMode.CURRENT),
            ClaimCommand.transition("reviewer", "missing-claim", apiVersion, "approved", ClaimCommand.ExpectedVersionMode.CURRENT),
            ClaimCommand.transition("member-a", "a-seed", apiVersion, "under_review", ClaimCommand.ExpectedVersionMode.CURRENT),
            ClaimCommand.transition("reviewer", "a-seed", apiVersion, "under_review", ClaimCommand.ExpectedVersionMode.CURRENT),
            ClaimCommand.transition("reviewer", "a-seed", apiVersion, "submitted", ClaimCommand.ExpectedVersionMode.STALE),
            ClaimCommand.transition("reviewer", "a-seed", apiVersion, "approved", ClaimCommand.ExpectedVersionMode.CURRENT),
            ClaimCommand.transition("reviewer", "a-seed", apiVersion, "under_review", ClaimCommand.ExpectedVersionMode.CURRENT),
            ClaimCommand.transition("reviewer", "b-seed", apiVersion, "under_review", ClaimCommand.ExpectedVersionMode.CURRENT),
            ClaimCommand.transition("reviewer", "b-seed", apiVersion, "rejected", ClaimCommand.ExpectedVersionMode.CURRENT),
            ClaimCommand.transition("reviewer", "b-seed", apiVersion, "submitted", ClaimCommand.ExpectedVersionMode.CURRENT),
            ClaimCommand.read("member-b", "b-seed", apiVersion),
            ClaimCommand.readEvents("member-b", "b-seed", apiVersion),
            ClaimCommand.readEvents("member-a", "a-seed", apiVersion),
            ClaimCommand.create("member-a", "a-over-limit", apiVersion, limit + 1)
        );
        append(commands, fixedPrefix, model);
        int nextAlias = 0;

        while (commands.size() < COMMANDS_PER_SEQUENCE) {
            ClaimCommand selected = null;
            for (int draw = 0; draw < 10 && selected == null; draw++) {
                selected = drawCandidate(random, apiVersion, model, nextAlias);
            }
            if (selected == null) {
                List<String> aliases = model.claimAliases();
                if (aliases.isEmpty()) {
                    throw new IllegalStateException("The fixed prefix must create a claim before fallback.");
                }
                selected = ClaimCommand.read(
                    randomActor(random),
                    aliases.get(random.nextInt(aliases.size())),
                    apiVersion
                );
            }
            append(commands, List.of(selected), model);
            if (selected.type() == ClaimCommand.Type.CREATE) {
                nextAlias += 1;
            }
        }
        return List.copyOf(commands);
    }

    private static ClaimCommand drawCandidate(
        Random random,
        int apiVersion,
        ClaimJourneyModel model,
        int nextAlias
    ) {
        int choice = random.nextInt(100);
        if (choice < 10) {
            if (model.claimAliases().size() >= MAX_GENERATED_CLAIMS) {
                return null;
            }
            String owner = random.nextBoolean() ? "member-a" : "member-b";
            long limit = ClaimJourneyModel.limitForApi(apiVersion);
            long amount = switch (random.nextInt(4)) {
                case 0 -> 1;
                case 1 -> limit;
                case 2 -> limit + 1;
                default -> 1 + random.nextLong(limit);
            };
            return ClaimCommand.create(owner, "generated-" + nextAlias, apiVersion, amount);
        }
        if (choice < 25) {
            return ClaimCommand.read(randomActor(random), randomClaim(random, model), apiVersion);
        }
        if (choice < 35) {
            return ClaimCommand.list(randomActor(random), apiVersion);
        }
        if (choice < 80) {
            String alias = randomClaim(random, model);
            int version = model.version(alias);
            ClaimCommand.ExpectedVersionMode versionMode = switch (random.nextInt(3)) {
                case 1 -> version > 1 ? ClaimCommand.ExpectedVersionMode.STALE : ClaimCommand.ExpectedVersionMode.CURRENT;
                case 2 -> ClaimCommand.ExpectedVersionMode.FUTURE;
                default -> ClaimCommand.ExpectedVersionMode.CURRENT;
            };
            String target = List.of("submitted", "under_review", "approved", "rejected")
                .get(random.nextInt(4));
            return ClaimCommand.transition("reviewer", alias, apiVersion, target, versionMode);
        }
        return ClaimCommand.readEvents(randomActor(random), randomClaim(random, model), apiVersion);
    }

    private static String randomActor(Random random) {
        return List.of("member-a", "member-b", "reviewer").get(random.nextInt(3));
    }

    private static String randomClaim(Random random, ClaimJourneyModel model) {
        List<String> aliases = model.claimAliases();
        if (aliases.isEmpty()) {
            throw new IllegalStateException("No claim alias is available for a generated command.");
        }
        return aliases.get(random.nextInt(aliases.size()));
    }

    private static void append(
        List<ClaimCommand> output,
        List<ClaimCommand> additions,
        ClaimJourneyModel model
    ) {
        for (ClaimCommand command : additions) {
            output.add(command);
            ClaimJourneyModel.Expected expected = model.expected(command);
            model.commit(command, expected);
        }
    }
}
