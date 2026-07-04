# CLAUDE.md

## Project

Java 21 REST Assured/JUnit 5 API automation framework for Conduit-style API
coverage, contract checks, reliability evidence, and portfolio reporting.

## Session Start

Refresh the local code graph before structural discovery:

`bash .agent/index-codebase-memory.sh`

Current MCP project name:

`home-vyaspc-Documents-Repo-aria-api-framework`

## Commands

- Install/use wrapper: `./gradlew --version`
- Main verification: `./gradlew test`
- Tagged tests: `./gradlew test -DincludeTags=smoke`
- Format: `./gradlew spotlessApply`
- Format check: `./gradlew spotlessCheck`
- Static checks: `./gradlew spotbugsMain spotbugsTest`
- Dependency/security artifacts: `./gradlew cyclonedxBom`

## Layout

- `src/main/java` - reusable API framework code, clients, config, reporting helpers.
- `src/test/java` - JUnit 5 API, contract, reliability, and seeded-defect tests.
- `src/test/resources` - test data, schemas, Allure/JUnit resources.
- `docs/` - architecture, execution, reliability, writing-tests, and debugging guides.
- `reliability/quarantine.yml` - quarantine policy and known reliability exceptions.
- `portfolio/manifest.yml` - portfolio metadata.

## Codebase Memory MCP

Use graph tools before broad file reads:

1. `list_projects`
2. `get_architecture(project="home-vyaspc-Documents-Repo-aria-api-framework")`
3. `search_graph`
4. `trace_path`
5. `get_code_snippet`
6. `query_graph`

Fall back to `rg` for literals, configs, docs, generated files, or insufficient graph results.

## Agent Rules

- Cite `file:line` for code claims whenever practical.
- Keep changes scoped to the framework layer under test; avoid unrelated cleanup.
- Prefer targeted Gradle tasks and tagged tests over full-suite reruns.
- Do not commit `.codebase-memory/`, `codebase-memory/`, or `.agent/index-codebase-memory.sh`.

