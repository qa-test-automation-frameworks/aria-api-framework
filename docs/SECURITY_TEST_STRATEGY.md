# Security Test Strategy

ARIA separates deterministic security-boundary checks from live API probes.

## Deterministic Coverage

`src/test/java/com/aria/framework/security` uses the owned in-memory provider fixture to exercise API-security behavior that the public demo APIs do not reliably enforce:

- OWASP API1 BOLA/IDOR: cross-user booking access returns `403`.
- OWASP API3 mass assignment: unexpected privileged fields are rejected.
- OWASP API3 excessive data exposure: responses do not include internal fields.

These tests run in the default `test`/`check` gate because they do not depend on public services. WireMock remains available for fault injection and dependency-stub scenarios, but the security-boundary suite verifies executable owned-provider behavior rather than isolated stubs.

## Live Coverage

`src/test/java/com/aria/framework/restfulbooker/BookingSecurityTests.java` runs only with the `live` tag. It validates malformed-token behavior against the Restful Booker API.

Run live security tests with:

```powershell
.\gradlew.bat liveTest -Denv=dev
```

`BOOKER_USERNAME` and `BOOKER_PASSWORD` must be supplied through environment variables or system properties.

Known public-demo validation weaknesses are tagged `known-demo-api-limitations` instead of normal `negative` coverage. Owned-provider security tests assert the desired `400`, `403`, or equivalent rejection behavior.
