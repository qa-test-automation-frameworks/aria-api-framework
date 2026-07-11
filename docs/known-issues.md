# Known Issues

| Area | Status | Workaround |
| --- | --- | --- |
| Live GitHub rate limits | External dependency can throttle exploratory runs | Use deterministic owned-provider and WireMock coverage for PR gates |
| Local OSV scanner | `securityScan` skips local scanner execution when `osv-scanner` is not installed | CI runs the scanner; pass `-PrequireOsvScanner=true` to require it locally |
| Windows wrapper file | `gradlew.bat` may differ by local environment | Do not include wrapper changes unless intentionally refreshing Gradle wrapper files |
