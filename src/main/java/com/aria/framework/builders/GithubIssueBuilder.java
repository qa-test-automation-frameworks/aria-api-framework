package com.aria.framework.builders;

import com.aria.framework.models.request.GithubIssueRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder class to construct valid JSON payloads for GitHub issues.
 * Serializes as a map which is passed directly to RestAssured body parsing.
 */
public class GithubIssueBuilder {

    private String title;
    private String body;
    private String state;
    private final List<String> assignees = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    public GithubIssueBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public GithubIssueBuilder withBody(String body) {
        this.body = body;
        return this;
    }

    public GithubIssueBuilder withState(String state) {
        this.state = state;
        return this;
    }

    public GithubIssueBuilder addLabel(String label) {
        this.labels.add(label);
        return this;
    }

    public GithubIssueBuilder addAssignee(String assignee) {
        this.assignees.add(assignee);
        return this;
    }

    /**
     * Builds and returns a typed issue payload.
     *
     * @return GitHub issue request payload
     */
    public GithubIssueRequest build() {
        return new GithubIssueRequest(title, body, state, assignees, labels, null);
    }
}
