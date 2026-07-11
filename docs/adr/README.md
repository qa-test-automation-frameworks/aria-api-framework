# Architecture Decision Index

| ADR | Decision | Trade-off |
| --- | --- | --- |
| [ADR-001](ADR-001-deterministic-default-execution.md) | Keep public live targets out of the default gate | Stable CI at the cost of a separate live signal |
| [ADR-002](ADR-002-layered-service-client-architecture.md) | Separate HTTP clients from business services | More types in exchange for reusable boundaries |
| [ADR-003](ADR-003-sanitized-allure-diagnostics.md) | Sanitize diagnostics before persistence | Reduced raw detail in exchange for safer artifacts |
| [ADR-004](ADR-004-contract-openapi-quality-gates.md) | Combine contracts with endpoint coverage mapping | Mapping maintenance in exchange for visible omissions |
| [ADR-005](ADR-005-owned-provider-http-server.md) | Use the JDK HTTP server for owned fixtures | Lightweight fixture with limited middleware semantics |
