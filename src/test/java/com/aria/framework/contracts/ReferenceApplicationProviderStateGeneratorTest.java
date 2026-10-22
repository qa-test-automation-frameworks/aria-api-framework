package com.aria.framework.contracts;

import au.com.dius.pact.core.model.generators.ProviderStateGenerator;
import au.com.dius.pact.core.support.expressions.DataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("contract")
class ReferenceApplicationProviderStateGeneratorTest {

    @Test
    @DisplayName("Pact provider-state generators replace header, path, and body examples")
    void providerStateGeneratorsReplaceExamples() {
        Map<String, Object> state = Map.of(
            "token", "hosted-session-token",
            "policyId", "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
            "claimId", "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
        );

        assertThat(generate(ReferenceApplicationConsumerPactTest.TOKEN_EXPRESSION, state,
            "Bearer " + ReferenceApplicationConsumerPactTest.EXAMPLE_TOKEN))
            .isEqualTo("Bearer hosted-session-token");
        assertThat(generate(ReferenceApplicationConsumerPactTest.POLICY_ID_EXPRESSION, state,
            ReferenceApplicationConsumerPactTest.POLICY_ID))
            .isEqualTo(state.get("policyId"));
        assertThat(generate(ReferenceApplicationConsumerPactTest.CLAIM_ID_EXPRESSION, state,
            "/api/v2/claims/cccccccc-cccc-4ccc-8ccc-cccccccccccc/events"))
            .isEqualTo("/api/v2/claims/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee/events");
    }

    private static String generate(String expression, Map<String, Object> state, String example) {
        return (String) new ProviderStateGenerator(expression, DataType.STRING)
            .generate(Map.of("providerState", state), example);
    }
}
