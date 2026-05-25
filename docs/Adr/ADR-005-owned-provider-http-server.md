# ADR-005: Owned Provider HTTP Server

## Status

Accepted

## Context

The deterministic provider fixture runs inside the test JVM and backs default CI coverage for Restful Booker and GitHub-style endpoints. It needs fast startup, no external process, no container dependency, and simple state reset between tests.

## Decision

Use the JDK `com.sun.net.httpserver.HttpServer` for the owned provider fixture.

## Consequences

The fixture remains dependency-free and starts quickly enough to share with `@BeforeAll` lifecycle setup. The `com.sun` package name is a portability tradeoff, but the API is shipped with the JDK used by this project and keeps the fixture smaller than embedding a full web framework.

If future test requirements need filters, routing middleware, TLS behavior, or richer HTTP semantics, replace it with WireMock or a lightweight embedded framework.
