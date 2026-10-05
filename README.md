# swarm-deployer

Ce qui déploie les stacks d'un dépôt git sur Docker Swarm, partagé par deux outils :

- **swarm-service** : ton cluster, avec son interface, son webhook et OIDC ;
- **hortiprobes-supervisor** : le boîtier HortiProbes, sans interface (`hortiprobes-appliance/ARCHITECTURE.md` §5).

Les deux suivent une branche, rendent ses stacks avec leurs valeurs et les déploient dans l'ordre. Ce socle commun garde un seul exemplaire des pièges déjà résolus dans swarm-service, documentés dans son README :
- versions des configs ;
- guillemets ;
- identifiants du registre transmis aux services ;
- mise à jour de soi-même.

## Ce qu'il contient

| | Classe | Rôle |
|---|---|---|
| L1 | `git.GitRepository`, `GitSettings` | Clone puis suit une branche, reset dur à chaque synchronisation. **Sans jeton, en lecture anonyme** : assez pour un dépôt public |
| L2 | `stacks.StackCatalog` | Les dossiers de stacks, et leur ordre dans `stacks.yaml` |
| L3 | `stacks.StackRenderer`, `encryption.EncryptionService` | Copie rendue d'une stack : `{{clé}}` substitués, `{cipher}…{cipher}` ouverts (AES-GCM, et l'ancien ECB en lecture). Un `{{clé}}` inconnu est signalé, pas remplacé ; à l'appelant de décider |
| L4 | `stacks.ConfigRotation` | Le nom de chaque config ou secret Swarm déclaré avec `file:` reçoit le condensat de son contenu rendu |
| L5 | `stacks.StackDeployCommand` | `docker stack deploy --with-registry-auth`, sans attendre (`DETACHED`) ou jusqu'à la convergence, contrôles de santé compris (`AWAITED`) |
| L6 | `docker.RegistryLogin` | `docker login` dans un `DOCKER_CONFIG` à soi, **hôte de registre au choix** |
| L7 | `docker.SwarmOperations` | Purge des configs devenues inutiles, redémarrage d'un service, changement d'image, y compris la sienne |

Des classes Java simples, sans Spring : chaque outil les assemble à sa façon. Dans swarm-service, c'est `config/DeployerConfiguration`.

## Règle

**swarm-service doit se comporter exactement pareil avec cette bibliothèque.** Une évolution ici passe par les tests des deux côtés. Elle est validée sur l'instance `dev` de swarm-service avant la production.

## Publier

Une release GitHub publie la version de son tag sur GitHub Packages, par exemple `0.1.0`. Le workflow `.github/workflows/publish.yml` appelle `maven-library.yml` de `shared-workflows`, sur le runner self-hosted, comme les images. Il se lance aussi à la main (*workflow_dispatch*), avec la version.

## S'en servir

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
    <version>0.1.0</version>
</dependency>
```

GitHub Packages demande des identifiants, même pour lire.

- **En CI** : `shared-workflows/spring.yml` passe le jeton du workflow à Maven. Le paquet doit autoriser le dépôt qui le lit : *Package settings → Manage Actions access*.
- **En local**, au choix :
  - `./mvnw install` dans ce dépôt, qui l'installe dans `~/.m2` ;
  - ou un serveur `github` dans `~/.m2/settings.xml`, avec un jeton classique `read:packages`.
