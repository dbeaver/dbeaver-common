# DBeaver Common – AI Agent Instructions

## Scope

DBeaver Common is the public Apache 2.0 build foundation and utility repository shared by DBeaver products. Changes to
public APIs, dependency versions, manifests, and parent POM properties may affect many repositories.

Keep product-specific UI, branding, deployment, and business logic out of this repository.

## Modules

| Module | Packaging | Purpose |
|---|---|---|
| `org.jkiss.utils` | `eclipse-plugin` | Core utilities and annotations |
| `com.dbeaver.jdbc.api` | `eclipse-plugin` | Reusable JDBC interfaces and base classes |
| `com.dbeaver.rest.client` | `eclipse-plugin` | JDK HTTP client helpers |
| `com.dbeaver.servlet.api` | `eclipse-plugin` | Shared servlet health/status API |
| `com.dbeaver.spring.utils` | JAR | Spring-specific utilities |

`org.jkiss.utils` is the lowest layer and must not depend on JDBC, servlet, Spring, Eclipse UI, or product code. Avoid
cycles and keep framework-specific code in its module.

## Compatibility and build

- OSGi modules target Java 17; `com.dbeaver.spring.utils` and inheriting products use Java 21.
- Do not use Java 18+ APIs in Java 17 modules or preview features.
- Build from the top-level POM; `root/pom.xml` is a parent/version-management POM, not the reactor.

```bash
./mvnw clean verify
./mvnw -pl :org.jkiss.utils -am test
./mvnw -pl :com.dbeaver.rest.client -am verify
```

CI also runs shared Checkstyle.

## Maven and OSGi

For `eclipse-plugin` modules:

- Keep `pom.xml`, `META-INF/MANIFEST.MF`, and `build.properties` synchronized.
- Keep Maven dependencies and OSGi `Require-Bundle` entries aligned.
- Export only intentional public API packages.
- Preserve symbolic names, versions, execution environments, and module names.

Source layouts differ: utils, REST, and Spring use `src/main/java`; JDBC and servlet use `src/`. Do not create a second
source tree or add OSGi packaging to the Spring module.

## Dependencies and public API

- Shared dependency and plugin versions belong in `root/pom.xml`; use managed versions in modules.
- Prefer the JDK and existing utilities before adding a dependency.
- Review license, security, transitives, size, Java 17, OSGi, BOM ordering, and downstream impact.
- Avoid unrelated upgrades, especially in the parent POM.
- Assume exported packages and public/protected members have external consumers.
- Preserve source/binary compatibility, null behavior, exceptions, ordering, equality, mutability, encoding, and thread
  safety. New overloads must not make existing calls ambiguous.
- Keep implementation helpers package-private and framework types out of low-level APIs.

## Utilities and code style

- Search the JDK and this repository before adding a helper.
- Reuse `CommonUtils`, `ArrayUtils`, `MapUtils`, `IOUtils`, `StringUtils`, and `XMLUtils`.
- Add generic helpers to the narrowest class with defined null/error behavior and focused tests.
- Keep product- or domain-specific helpers in the consuming repository.
- Follow `.github/dbeaver-checkstyle-config.xml` and keep changes minimal.
- Use `@NotNull`, `@Nullable`, and `@NotNullWhen` from `org.jkiss.code` where applicable.
- Follow existing exception contracts and preserve original causes.
- Low-level modules use `java.util.logging`; do not add SLF4J, Log4j, product logging, or `System.out/err`.
- New Java files use the repository's Apache 2.0 header.

## Testing and security

- Keep tests in the owning module and add regression coverage for shared behavior.
- Cover nulls, empty or malformed input, boundaries, encoding, platform differences, and concurrency when relevant.
- Keep tests deterministic and independent of network services and user files.
- Validate untrusted URLs, paths, headers, XML, serialized data, and process arguments.
- Preserve TLS and certificate validation; avoid unsafe deserialization, XXE, command injection, and path traversal.
- Never commit credentials, keys, signing material, private URLs, local configuration, or generated artifacts.
- Report vulnerabilities through `SECURITY.md`, not a public issue.

## Git workflow

- Target `devel`; use release branches only for requested backports.
- Commits and PR titles use `dbeaver/<issue-repository>#<issue-number> <description>`, for example
  `dbeaver/dbeaver#999999 Add new function` with a deliberately fictitious issue number.
- Branches use `dbeaver/<issue-repository>#<issue-number>-<short-description>`, for example
  `dbeaver/dbeaver#999999-add-new-function`.
- Start the PR description with `Closes dbeaver/<issue-repository>#<issue-number>` or the full issue URL.
- Create PRs Ready for review unless a draft is explicitly requested.
- Describe compatibility, consumers, tests, dependency/manifest changes, and follow-up work.
- Disclose materially AI-generated code or documentation.

## Validation

- Run targeted tests while iterating and the full build for parent POM, packaging, manifest, or multi-module changes.
- Check dependencies, OSGi requirements, exports, source layout, and Java 17 compatibility.
- Run `git diff --check` and review the complete diff for secrets, generated files, and unrelated changes.
- Report checks that were not run.

## Pitfalls

- Java targets and source layouts differ by module.
- OSGi modules have both Maven and manifest dependency metadata.
- Exported packages and parent POM changes have a broad compatibility impact.
- Tests are currently concentrated in `org.jkiss.utils`.
- README examples are partly stale; trust the reactor and POMs.
- Publishing profiles have external effects and require an explicit request.
