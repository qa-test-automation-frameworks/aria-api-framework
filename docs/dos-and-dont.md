# Dos And Dont

## Do

- Use service objects from tests.
- Keep default `test` deterministic and free of public API dependencies.
- Add request/response schemas for new endpoints.
- Add every OpenAPI endpoint to `endpoint-test-coverage.csv`.
- Use environment variables or CI secrets for credentials.
- Attach diagnostics through the centralized sanitized filters.
- Use `BookingPayloadBuilder` and data factories for test data.
- Tag live tests with `live`.

## Dont

- Do not put raw RestAssured endpoint calls in normal test classes when a client/service exists.
- Do not commit secrets, `.env`, `build/`, local logs, or generated Allure output.
- Do not add live tests to the default CI gate.
- Do not assert public demo API security weaknesses as desired behavior.
- Do not bypass `RedactionPolicy` for request/response attachments.
- Do not add a dependency without updating the lockfile and SBOM path.
- Do not remove OpenAPI coverage mappings to make a build pass.
