# swarm-deployer

[![CI](https://github.com/Wizards-Development/swarm-deployer/actions/workflows/ci.yml/badge.svg)](https://github.com/Wizards-Development/swarm-deployer/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/java-21%2B-orange.svg)

**GitOps building blocks for Docker Swarm.** Follow a branch of a git repository, render its stacks with values and sealed secrets, and deploy them in order, with every Swarm pitfall already handled.

It is a plain Java library, with no framework. Two tools are built on it:

- **swarm-service**, a web console that deploys a cluster from a `stacks` repository on every push;
- **hortiprobes-supervisor**, a headless agent that keeps a single-node appliance on the release branch it follows.

## Why

`docker stack deploy` is simple until it is automated. Then:
- a config whose content changed is refused (`only updates to Labels are allowed`);
- secrets must not sit in clear in the repository;
- private images are rejected on the nodes that have to pull them;
- a deployer cannot redeploy itself in the middle of its own work.

This library holds the answers in one place, with their tests, so that every tool built on it gets them right.

## Features

| Building block | Class | What it does |
|---|---|---|
| Git working copy | `git.GitRepository`, `GitSettings` | Clones a branch, then hard-resets on the remote at every sync, so it never diverges. **Anonymous** without a token, enough for a public repository |
| Stack catalog | `stacks.StackCatalog` | The folders holding a `stack.yaml`, and their deployment order in `stacks.yaml` |
| Rendering | `stacks.StackRenderer` | A temporary copy of a stack where `{{key}}` is substituted and `{cipher}…{cipher}` opened, in every text file |
| Sealed values | `encryption.EncryptionService` | AES-256-GCM with a random IV per value. Values from the former AES-ECB scheme stay readable |
| Config rotation | `stacks.ConfigRotation` | Gives every Swarm config or secret declared with `file:` a name ending with the hash of its rendered content |
| Deployment | `stacks.StackDeployCommand` | `docker stack deploy --with-registry-auth`. Optionally waits for convergence, health checks included, and prunes the services removed from the file |
| Registry login | `docker.RegistryLogin` | `docker login` into a `DOCKER_CONFIG` of its own, for any registry |
| Swarm operations | `docker.SwarmOperations` | Removes unused config versions, restarts a service, points a service at another image, the deployer's own included |

## Requirements

- Java 21 or later.
- The `docker` CLI on the `PATH`, talking to a Swarm **manager**.
- A [docker-java](https://github.com/docker-java/docker-java) `DockerClient`, for `SwarmOperations` only.

## Installation

The library is published on GitHub Packages, which requires a token even to read a public package:
- in a GitHub Actions workflow, the workflow's `GITHUB_TOKEN` is enough;
- locally, use a classic personal access token with the `read:packages` scope.

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Wizards-Development/swarm-deployer</url>
    </repository>
</repositories>

<dependency>
    <groupId>ch.lucnelmr</groupId>
    <artifactId>swarm-deployer</artifactId>
    <version>0.2.0</version>
</dependency>
```

```xml
<!-- ~/.m2/settings.xml -->
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_LOGIN</username>
        <password>YOUR_TOKEN</password>
    </server>
</servers>
```

## Quick start

```java
EncryptionService encryption = new EncryptionService(System.getenv("CIPHER_KEY"));
GitRepository git = new GitRepository(GitSettings.anonymous(
        "https://github.com/acme/stacks.git", "main", Path.of("/var/lib/deployer/repository")));
StackCatalog catalog = new StackCatalog(git.repoDir());
StackRenderer renderer = new StackRenderer(encryption);
RegistryLogin registry = new RegistryLogin(Path.of("/var/lib/deployer/.docker"));
StackDeployCommand command = new StackDeployCommand(Convergence.AWAITED, RemovedServices.PRUNED,
        Duration.ofMinutes(10));

git.pull();
registry.ensureLoggedIn("ghcr.io", "deployer", System.getenv("REGISTRY_TOKEN"));
Map<String, String> values = Map.of("domain", "example.org");

for (Stack stack : catalog.declared().values().stream().sorted(Comparator.comparingInt(Stack::priority)).toList()) {
    StackRenderer.Rendered rendered = renderer.render(catalog.directory(stack.name()), stack.name(), values);
    try {
        Path composeFile = catalog.composeFile(rendered.directory());
        ConfigRotation.stamp(composeFile);
        command.deploy(composeFile, stack.name(), registry.commandEnvironment());
    }
    finally {
        StackRenderer.delete(rendered.directory()); // it holds decrypted values
    }
}
new SwarmOperations(dockerClient).pruneRotatedConfigs();
```

## The repository it reads

```
stacks.yaml            the stacks to deploy, and their order
traefik/
  stack.yaml           a regular compose file for docker stack deploy
  traefik.yml          any file next to it is rendered too
gatus/
  stack.yaml
  config.yaml
```

```yaml
# stacks.yaml: the lowest priority deploys first. A folder missing from it is not deployed.
traefik:
  priority: 10
  failOnDeploymentError: true   # exposed by Stack; stopping the batch is the caller's call
gatus:
  priority: 20
```

### Values: `{{key}}`

`{{key}}` is replaced in **every text file** of a stack, not only in `stack.yaml`. The rules:

- **An unknown or blank key** is left as is and reported in `Rendered.unresolved()`. Whether that stops the deployment is up to the caller.
- **A key starting with a dot**, such as `{{.Node.Hostname}}`, belongs to Swarm's own templates and is never touched.
- **Binary files** are copied untouched.

Do not use double braces for anything else in a stack folder: Go templates, Handlebars, Grafana legends. Use `${…}` for those.

### Sealed values: `{cipher}…{cipher}`

```yaml
POSTGRES_PASSWORD: '{cipher}v2.q2V…{cipher}'
```

- `EncryptionService.encrypt` produces the payload, and `wrap` adds the markers.
- The key is derived from a passphrase (SHA-256). It is the one value that cannot be sealed.
- **A value that cannot be opened fails the rendering** rather than being deployed as is.
- **Quote a value that starts with `{`.** Otherwise YAML reads it as a mapping.

### Config rotation

A Swarm config is immutable: changing what a service reads means deploying it under another name. `ConfigRotation.stamp` edits the rendered `stack.yaml` in place, one line per declaration:

```yaml
configs:
  gatus_config:
    file: ./config.yaml        # becomes name: gatus_config-1f2e3d4c5b6a
```

- The hash covers the **rendered** content, so a changed value moves the name even when the file in git did not change.
- The rest of the file is never re-emitted: an unquoted `8080:8080` stays a string.
- `SwarmOperations.pruneRotatedConfigs` removes the versions no service uses any more. Docker refuses to remove one still in use.

### Deployment modes

| | |
|---|---|
| `Convergence.DETACHED` | Returns as soon as Swarm has the new spec |
| `Convergence.AWAITED` | Returns once every service has converged, health checks included. Use it to deploy stacks in order, or to detect a failed update |
| `RemovedServices.KEPT` | A service deleted from the file keeps running |
| `RemovedServices.PRUNED` | `--prune`: the stack is exactly what its file says |

### Updating the deployer itself

A deployer running as a Swarm service cannot deploy its own stack: it would stop itself halfway. `SwarmOperations.updateServiceImage` points its service at the new image instead, and Swarm replaces the running task, the caller included. Do it last.

## Security

- **Rendered copies hold decrypted secrets.** They live in the system temporary folder: delete them with `StackRenderer.delete` as soon as the stack is deployed.
- **Registry credentials stay in the deployer's own `DOCKER_CONFIG`**, never the host's. `--with-registry-auth` then hands them to the services, which is how nodes pull private images.
- To report a vulnerability, see [SECURITY.md](SECURITY.md).

## Versioning

[Semantic Versioning](https://semver.org). Changes are listed in [CHANGELOG.md](CHANGELOG.md). A GitHub release publishes the version of its tag.

## Contributing

Issues and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). Every change is reviewed and merged by the maintainer.

## License

[Apache License 2.0](LICENSE).
