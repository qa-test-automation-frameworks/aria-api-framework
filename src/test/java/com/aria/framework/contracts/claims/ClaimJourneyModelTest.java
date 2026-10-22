package com.aria.framework.contracts.claims;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("contract")
class ClaimJourneyModelTest {

    @Test
    void acceptedTransitionsAdvanceVersionAndAppendOneMatchingEvent() {
        ClaimJourneyModel model = new ClaimJourneyModel();
        ClaimCommand create = ClaimCommand.create("member-a", "claim-a", 1, 1);
        ClaimJourneyModel.Expected created = model.expected(create);
        assertThat(created.status()).isEqualTo(201);
        model.commit(create, created);

        ClaimCommand underReview = ClaimCommand.transition(
            "reviewer", "claim-a", 1, "under_review", ClaimCommand.ExpectedVersionMode.CURRENT
        );
        ClaimJourneyModel.Expected moved = model.expected(underReview);
        assertThat(moved.claim()).isEqualTo(new ClaimJourneyModel.ClaimSnapshot("under_review", 2, 1));
        model.commit(underReview, moved);

        ClaimCommand approved = ClaimCommand.transition(
            "reviewer", "claim-a", 1, "approved", ClaimCommand.ExpectedVersionMode.CURRENT
        );
        ClaimJourneyModel.Expected finalState = model.expected(approved);
        model.commit(approved, finalState);

        assertThat(model.snapshot("claim-a"))
            .isEqualTo(new ClaimJourneyModel.ClaimSnapshot("approved", 3, 1));
        assertThat(model.events("claim-a"))
            .extracting(ClaimJourneyModel.EventSnapshot::toStatus)
            .containsExactly("submitted", "under_review", "approved");
        assertThat(model.events("claim-a"))
            .extracting(ClaimJourneyModel.EventSnapshot::claimVersion)
            .containsExactly(1, 2, 3);
    }

    @Test
    void rejectedTransitionLeavesStateAndEventsUnchanged() {
        ClaimJourneyModel model = new ClaimJourneyModel();
        ClaimCommand create = ClaimCommand.create("member-b", "claim-b", 2, 500_000);
        ClaimJourneyModel.Expected created = model.expected(create);
        model.commit(create, created);
        ClaimJourneyModel.ClaimSnapshot before = model.snapshot("claim-b");
        List<ClaimJourneyModel.EventSnapshot> eventsBefore = model.events("claim-b");

        ClaimCommand invalid = ClaimCommand.transition(
            "reviewer", "claim-b", 2, "approved", ClaimCommand.ExpectedVersionMode.CURRENT
        );
        ClaimJourneyModel.Expected rejected = model.expected(invalid);
        assertThat(rejected.status()).isEqualTo(409);
        assertThat(rejected.errorCode()).isEqualTo("INVALID_TRANSITION");
        model.commit(invalid, rejected);

        assertThat(model.snapshot("claim-b")).isEqualTo(before);
        assertThat(model.events("claim-b")).isEqualTo(eventsBefore);
    }

    @Test
    void roleAndVersionChecksPrecedeTransitionLegalityAndClaimLookup() {
        ClaimJourneyModel model = new ClaimJourneyModel();
        ClaimCommand forbiddenUnknown = ClaimCommand.transition(
            "member-a", "missing", 1, "approved", ClaimCommand.ExpectedVersionMode.CURRENT
        );
        assertThat(model.expected(forbiddenUnknown).errorCode()).isEqualTo("ROLE_FORBIDDEN");

        ClaimCommand reviewerUnknown = ClaimCommand.transition(
            "reviewer", "missing", 1, "approved", ClaimCommand.ExpectedVersionMode.CURRENT
        );
        assertThat(model.expected(reviewerUnknown).errorCode()).isEqualTo("RESOURCE_NOT_FOUND");

        ClaimCommand create = ClaimCommand.create("member-a", "claim-a", 1, 100);
        model.commit(create, model.expected(create));
        ClaimCommand firstTransition = ClaimCommand.transition(
            "reviewer", "claim-a", 1, "under_review", ClaimCommand.ExpectedVersionMode.CURRENT
        );
        model.commit(firstTransition, model.expected(firstTransition));
        ClaimCommand staleIllegalEdge = ClaimCommand.transition(
            "reviewer", "claim-a", 1, "submitted", ClaimCommand.ExpectedVersionMode.STALE
        );

        assertThat(model.expected(staleIllegalEdge).errorCode()).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void apiLimitsAndMinorUnitBoundariesAreIndependentForBothVersions() {
        for (int apiVersion : List.of(1, 2)) {
            long limit = ClaimJourneyModel.limitForApi(apiVersion);
            ClaimJourneyModel model = new ClaimJourneyModel();
            ClaimCommand minimum = ClaimCommand.create("member-a", "minimum", apiVersion, 1);
            ClaimJourneyModel.Expected minimumResult = model.expected(minimum);
            assertThat(minimumResult.status()).isEqualTo(201);
            model.commit(minimum, minimumResult);

            ClaimCommand maximum = ClaimCommand.create("member-b", "maximum", apiVersion, limit);
            ClaimJourneyModel.Expected maximumResult = model.expected(maximum);
            assertThat(maximumResult.status()).isEqualTo(201);
            model.commit(maximum, maximumResult);

            ClaimCommand overMaximum = ClaimCommand.create("member-b", "too-large", apiVersion, limit + 1);
            assertThat(model.expected(overMaximum).status()).isEqualTo(422);
            assertThat(model.expected(overMaximum).errorCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(model.snapshot("too-large")).isNull();
        }
    }

    @Test
    void seededCommandSequencesAreStableBoundedAndReachBothTerminalBranches() {
        for (long seed : ClaimCommandGenerator.SEEDS) {
            for (int apiVersion : List.of(1, 2)) {
                List<ClaimCommand> first = ClaimCommandGenerator.generate(seed, apiVersion);
                List<ClaimCommand> replay = ClaimCommandGenerator.generate(seed, apiVersion);
                assertThat(first).containsExactlyElementsOf(replay).hasSize(ClaimCommandGenerator.COMMANDS_PER_SEQUENCE);
                assertThat(first).anySatisfy(command -> assertThat(command.targetStatus()).isEqualTo("approved"));
                assertThat(first).anySatisfy(command -> assertThat(command.targetStatus()).isEqualTo("rejected"));
                assertThat(first.get(2).type()).isEqualTo(ClaimCommand.Type.READ);
                assertThat(first.get(3).type()).isEqualTo(ClaimCommand.Type.READ_EVENTS);
                assertThat(first.get(4).type()).isEqualTo(ClaimCommand.Type.LIST);
                assertThat(first.get(4).actorAlias()).isEqualTo("member-b");
                assertThat(first.get(11).targetStatus()).isEqualTo("submitted");
                assertThat(first.get(13).targetStatus()).isEqualTo("under_review");

                ClaimJourneyModel model = new ClaimJourneyModel();
                for (int index = 0; index < first.size(); index++) {
                    ClaimCommand command = first.get(index);
                    ClaimJourneyModel.Expected expected = model.expected(command);
                    if (index == 11) {
                        assertThat(expected.errorCode()).isEqualTo("VERSION_CONFLICT");
                    }
                    if (index == 13) {
                        assertThat(expected.errorCode()).isEqualTo("INVALID_TRANSITION");
                    }
                    model.commit(command, expected);
                }
                assertThat(model.claimAliases()).hasSizeBetween(2, ClaimCommandGenerator.MAX_GENERATED_CLAIMS);
            }
        }
    }
}
