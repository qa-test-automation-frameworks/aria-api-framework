## Summary

- 

## Validation

- [ ] `.\gradlew.bat test -Denv=dev`
- [ ] `.\gradlew.bat clean check securityScan allureReport -Denv=dev`
- [ ] Docker-backed tests considered when relevant

## Checklist

- [ ] Deterministic tests stay independent of live public APIs
- [ ] New live tests are tagged `live`
- [ ] Secrets and diagnostics remain redacted
- [ ] OpenAPI coverage mapping updated when endpoints change
- [ ] Docs updated for user-facing workflow changes
