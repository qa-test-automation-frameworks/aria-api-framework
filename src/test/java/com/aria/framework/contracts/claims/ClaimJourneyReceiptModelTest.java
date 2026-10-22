package com.aria.framework.contracts.claims;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("contract")
class ClaimJourneyReceiptModelTest {

    @Test
    void receiptRoundTripsWithAliasesAndWithoutCredentialsOrRuntimeIds() throws IOException {
        ClaimCommand command = ClaimCommand.create("member-a", "a-seed", 2, 3741);
        ClaimJourneyModel.Expected expected = new ClaimJourneyModel.Expected(
            201,
            null,
            new ClaimJourneyModel.ClaimSnapshot("submitted", 1, 3741),
            null,
            null
        );
        ClaimJourneyReceipt receipt = new ClaimJourneyReceipt(
            1,
            0x51DE7L,
            2,
            ClaimCommandGenerator.ALGORITHM,
            "framework-sha",
            "target-sha",
            "sha256:" + "a".repeat(64),
            new ClaimJourneyReceipt.Coverage(Map.of("CREATE", 1), Map.of(), Map.of(), Map.of("v2", 1)),
            List.of("baseline-0a1b2c3d4e5f"),
            List.of(new ClaimJourneyReceipt.CommandResult(
                0,
                command,
                expected,
                new ClaimJourneyReceipt.Observed(
                    201,
                    null,
                    new ClaimJourneyModel.ClaimSnapshot("submitted", 1, 3741),
                    null,
                    null
                ),
                "provider_response",
                null,
                null,
                null
            )),
            null,
            null
        );

        String json = ClaimJourneyReceiptCodec.serialize(receipt);
        ClaimJourneyReceipt parsed = ClaimJourneyReceiptCodec.parse(json);

        assertThat(parsed).isEqualTo(receipt);
        assertThat(json).contains("a-seed", "member-a", "3741");
        assertThat(json).doesNotContain("token", "policyId", "claimId", "authorization", "eyJ");
    }

    @Test
    void replayRejectsUnknownCommandsAndUnexpectedFields() throws IOException {
        String valid = sampleJson();
        String unknownCommand = valid.replace("\"type\" : \"CREATE\"", "\"type\" : \"EXPLODE\"");
        assertThatThrownBy(() -> ClaimJourneyReceiptCodec.parse(unknownCommand))
            .isInstanceOf(IOException.class);

        String unknownField = valid.replace(
            "\"claimAlias\" : \"a-seed\"",
            "\"claimAlias\" : \"a-seed\",\n        \"token\" : \"must-not-be-accepted\""
        );
        assertThatThrownBy(() -> ClaimJourneyReceiptCodec.parse(unknownField))
            .isInstanceOf(IOException.class);
    }

    @Test
    void replayRejectsUnsupportedSchemaAndUnknownActorAliases() throws IOException {
        String valid = sampleJson();
        assertThatThrownBy(() -> ClaimJourneyReceiptCodec.parse(valid.replace("\"schemaVersion\" : 1", "\"schemaVersion\" : 2")))
            .isInstanceOf(IOException.class);

        String unknownActor = valid.replace("\"actorAlias\" : \"member-a\"", "\"actorAlias\" : \"service-admin\"");
        assertThatThrownBy(() -> ClaimJourneyReceiptCodec.parse(unknownActor))
            .isInstanceOf(IOException.class);

        String unknownClaim = valid.replace(
            "\"claimAlias\" : \"a-seed\"",
            "\"claimAlias\" : \"unmapped-123456789abc\""
        );
        assertThatThrownBy(() -> ClaimJourneyReceiptCodec.parse(unknownClaim))
            .isInstanceOf(IOException.class);
    }

    private static String sampleJson() throws IOException {
        ClaimCommand command = ClaimCommand.create("member-a", "a-seed", 1, 1);
        ClaimJourneyModel.Expected expected = new ClaimJourneyModel.Expected(
            201,
            null,
            new ClaimJourneyModel.ClaimSnapshot("submitted", 1, 1),
            null,
            null
        );
        ClaimJourneyReceipt receipt = new ClaimJourneyReceipt(
            1,
            ClaimCommandGenerator.SEEDS[0],
            1,
            ClaimCommandGenerator.ALGORITHM,
            "framework-sha",
            "target-sha",
            "sha256:" + "b".repeat(64),
            new ClaimJourneyReceipt.Coverage(Map.of("CREATE", 1), Map.of(), Map.of(), Map.of("v1", 1)),
            List.of(),
            List.of(new ClaimJourneyReceipt.CommandResult(
                0,
                command,
                expected,
                new ClaimJourneyReceipt.Observed(201, null, expected.claim(), null, null),
                "provider_response",
                null,
                null,
                null
            )),
            null,
            null
        );
        return ClaimJourneyReceiptCodec.serialize(receipt);
    }
}
