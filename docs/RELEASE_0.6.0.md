# Release 0.6.0 checklist

This document tracks the first public Rizoma release.

## Release gate

- [ ] CI passes on Java 21 and 25.
- [ ] `./mvnw verify` passes from a clean checkout.
- [ ] `./scripts/quickstart.sh` passes.
- [ ] External adoption tests compile against the aggregate `rizoma` artifact.
- [ ] Public API and JSON compatibility notes are reviewed.
- [ ] `CHANGELOG.md` moves the 0.6 work from Unreleased into a dated `0.6.0` section.
- [ ] Reactor version changes from `0.6.0-SNAPSHOT` to `0.6.0`.
- [ ] README examples use `0.6.0`.
- [ ] Git tag `v0.6.0` is created from the verified release commit.
- [ ] GitHub Release publishes the CLI shaded JAR and release notes.

## Maven Central readiness

The project coordinates are intended to be:

```text
io.github.felipemacedo1:rizoma:0.6.0
```

Before Central publication, configure the publishing identity and secrets outside the repository. Never commit credentials or private signing keys.

Required release metadata is kept in the parent POM: project URL, Apache-2.0 license, developer identity and SCM coordinates.

The publishing workflow should only be enabled after the namespace/account and signing credentials are configured. Until then, GitHub Release can be used independently as the first public distribution channel.

## First-release policy

0.6.0 is the first public release. Treat the public Java facade and documented serialized contracts as compatibility-sensitive from this point forward. Breaking changes should be explicit in the changelog and versioning strategy.
