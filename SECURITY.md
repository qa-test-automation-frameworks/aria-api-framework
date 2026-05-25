# Security Policy

## Supported Branch

Security fixes are accepted against the default branch.

## Reporting a Vulnerability

Do not open a public issue for secrets, credential exposure, bypasses, or dependency vulnerabilities with exploit details. Report privately to the repository owner through GitHub private vulnerability reporting or by direct contact if private reporting is unavailable.

Include:

- Affected file, dependency, workflow, or test path
- Reproduction steps
- Expected and actual impact
- Any safe remediation suggestion

## Security Expectations

- Secrets must be supplied through environment variables, system properties, or CI secrets.
- HTTP diagnostics must use `RedactionPolicy` before being attached to reports.
- Dependency changes must pass `securityScan` and CI vulnerability checks.
- New auth flows must include negative coverage for missing, malformed, expired, and unauthorized credentials where the target API supports those scenarios.
