# Contributing

Thank you for taking the time. This library deploys production clusters, so every change is reviewed with that in mind.

## Before you start

- **Bugs**: open an issue with the steps to reproduce, and what you expected.
- **Changes of behaviour or new features**: open an issue first and describe the use case. A change that alters what an existing tool deploys needs a very good reason.
- **Security issues**: never in a public issue. See [SECURITY.md](SECURITY.md).

## Building

Java 21 and the Maven wrapper are all you need. Docker is not required: the tests never call it.

```bash
./mvnw verify
```

The build checks the formatting ([Spring Java Format](https://github.com/spring-io/spring-javaformat)). To apply it:

```bash
./mvnw spring-javaformat:apply
```

## Pull requests

- One topic per pull request, with tests that fail without the change.
- Keep the public API stable. A breaking change waits for a major version and is called out in [CHANGELOG.md](CHANGELOG.md).
- Add a line under *Unreleased* in [CHANGELOG.md](CHANGELOG.md).
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org): `feat:`, `fix:`, `docs:`, `chore:`…
- **Only the maintainer merges.** CI must pass, and the code owners must approve.

By submitting a pull request, you agree that your contribution is licensed under the [Apache License 2.0](LICENSE).
