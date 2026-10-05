# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.0] - 2026-10-05

### Added

- `StackDeployCommand.RemovedServices`: `PRUNED` deploys with `--prune`, so a service removed from its stack file is removed from Swarm; `KEPT` leaves it running, as before.

### Changed

- The `StackDeployCommand` constructor takes the `RemovedServices` policy.

## [0.1.0] - 2026-10-05

### Added

- First release, extracted from swarm-service without any change of behaviour:
  - `git.GitRepository`, now anonymous when no token is given;
  - `stacks.StackCatalog`;
  - `stacks.StackRenderer` and `encryption.EncryptionService`;
  - `stacks.ConfigRotation`;
  - `stacks.StackDeployCommand`, which can now await convergence;
  - `docker.RegistryLogin`, for any registry;
  - `docker.SwarmOperations`.

[Unreleased]: https://github.com/Wizards-Development/swarm-deployer/compare/0.2.0...HEAD
[0.2.0]: https://github.com/Wizards-Development/swarm-deployer/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/Wizards-Development/swarm-deployer/releases/tag/0.1.0
