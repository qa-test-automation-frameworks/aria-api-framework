# Mutation Testing

PITest runs against the utility code where mutation signal is most useful:

- `RedactionPolicy`
- `RetryUtils`
- `OpenApiCoverageReporter`

Run locally:

```bash
./gradlew pitest
```

The configured mutation threshold is 70%. Reports are written to `build/reports/pitest/` in XML and HTML formats.
