# Supported runtime policy

The framework build and test baseline is Eclipse Temurin JDK 21. `.java-version`,
the Gradle toolchain, and the required CI jobs agree on Java 21. The unsupported
JDK 17 CI control proves that Gradle rejects an older runtime with an actionable
message. Newer JDKs are not claimed as supported until the Gradle plugins and
test extensions are verified there.

Node.js 24.21.0 is pinned in `.nvmrc` for the dependency-free evidence contract
validator. It is an auxiliary CI tool; building and running the Java framework
does not require Node or npm. The framework's `scripts/check-runtime.mjs` checks
the Node floor before that validator runs. Node 24 is an active LTS line in the
[official Node release schedule](https://nodejs.org/en/about/previous-releases),
and [24.21.0](https://nodejs.org/en/blog/release/v24.21.0) is the current tested
patch baseline for this repository. Stable later Node 24 patches may satisfy the
guard, but are not automatically verified.

On a clean checkout, install a JDK 21 distribution and run the checked-in
wrapper:

```sh
./gradlew --no-daemon clean check
```

CI provisions Temurin 21 and uses the Gradle wrapper; it records actual Java and
Gradle versions in its evidence. The unsupported-JDK control is part of the
same workflow. Node evidence tooling reads `.nvmrc`; no separate npm dependency
tree is needed for the contract validator.

The runtime baseline was checked against upstream release and lifecycle
information on 2026-10-16. A runtime update requires compatible Gradle, JUnit,
Pact and Testcontainers results, including the container-backed task. This
policy describes tested project support, not a guarantee that every future
patch will work.
