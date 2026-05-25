package com.aria.framework.data;

import com.aria.framework.builders.GithubIssueBuilder;
import com.aria.framework.models.request.GithubIssueRequest;
import net.datafaker.Faker;

import java.util.Map;

/**
 * Factory for GitHub API test data and query parameters.
 */
public final class GithubDataFactory {

    private static final Faker FAKER = new Faker();
    private static final String RUN_ID = System.getProperty(
        "aria.run.id",
        System.getenv().getOrDefault("ARIA_RUN_ID", Long.toString(System.currentTimeMillis()))
    );

    private GithubDataFactory() {
    }

    /**
     * Creates a GitHub issue payload with a unique title.
     *
     * @return issue request payload
     */
    public static GithubIssueRequest issuePayload() {
        return new GithubIssueBuilder()
            .withTitle("ARIA automated issue " + RUN_ID + "-" + FAKER.number().digits(6))
            .withBody("Created by ARIA API framework automated tests. runId=" + RUN_ID)
            .addLabel("automation")
            .addLabel("aria-run-" + RUN_ID)
            .build();
    }

    public static GithubIssueRequest closeIssuePayload() {
        return new GithubIssueBuilder()
            .withState("closed")
            .build();
    }

    /**
     * Creates repository search parameters that exercise pagination and sorting.
     *
     * @return query parameter map
     */
    public static Map<String, Object> repositorySearchParams() {
        return Map.of(
            "q", "language:java stars:>1000",
            "per_page", 5,
            "page", 1,
            "sort", "stars",
            "order", "desc"
        );
    }
}
