# Seeded Defect Examples

These controlled mutations describe how the suite demonstrates that its assertions fail for the right reason.

| Seeded defect | Expected detector | Evidence |
| --- | --- | --- |
| Remove token and password redaction | Redaction unit/security tests fail because sensitive values remain visible | `RedactionPolicy` and security tests |
| Accept both concurrent updates with last-write-wins | Concurrency test fails because it requires one `200`, one `409`, version `2`, and one mutation | `ConcurrentBookingBoundaryTest` |
| Retry a non-idempotent mutation without a control key | Retry policy tests fail because uncontrolled mutations execute once | `RetryUtils` tests |
| Delete an endpoint from the coverage mapping | OpenAPI coverage task reports the unmapped operation | `OpenApiCoverageReporter` |
| Return a body that violates the checked schema | Schema validation tests fail at the response contract assertion | Restful Booker schema tests |

Use these mutations on a temporary branch or in a mutation-testing job. They are examples, not defects committed to the default branch.
