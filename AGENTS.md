# DBeaver Common – AI Agent Instructions

## What is DBeaver Common?

DBeaver Common is the shared build foundation and utility library repository used by DBeaver products. It contains the
parent Maven configuration, dependency and plugin version management, low-level Java utilities, and reusable JDBC,
REST, servlet, and Spring helpers.

This is a public, open-source repository distributed under the Apache License 2.0. Changes can affect DBeaver Community,
commercial products, and other repositories that inherit the parent POM or consume the published artifacts.
Treat every public class, method, package, dependency version, and build property as a potentially shared contract.

Keep this repository independent of product-specific UI, branding, deployment, and business logic.

---

## Repository Layout

```text
dbeaver-common/
├── root/
│   └── pom.xml                         # Shared parent POM, BOMs, versions, plugin management
├── modules/
│   ├── pom.xml                         # Common module reactor and Java compatibility rules
│   ├── org.jkiss.utils/                # Core utilities, annotations, API helpers, CSV/XML/OAuth/REST/RPC
│   ├── com.dbeaver.jdbc.api/           # Base implementations for custom JDBC drivers
│   ├── com.dbeaver.rest.client/        # JDK HTTP client and interceptor framework
│   ├── com.dbeaver.servlet.api/        # Shared servlet health/status API
│   └── com.dbeaver.spring.utils/       # Spring-specific utilities, including explicit JDBC transactions
├── .github/
│   ├── dbeaver-checkstyle-config.xml   # Shared Java Checkstyle configuration
│   └── workflows/                      # CI and reusable Maven/Checkstyle workflows
├── pom.xml                             # Top-level reactor and publishing profiles
├── mvnw / mvnw.cmd                     # Maven wrapper
├── LICENSE.md
└── SECURITY.md
```

The top-level `pom.xml` is the build entry point. `root/pom.xml` is primarily a parent POM and version-management source;
running Maven against it alone does not build the common modules.

---

## Modules and Dependency Direction

| Module | Packaging | Purpose |
|---|---|---|
| `org.jkiss.utils` | `eclipse-plugin` | Lowest-level utilities, nullability annotations, common APIs, CSV/XML/OAuth/REST/RPC helpers |
| `com.dbeaver.jdbc.api` | `eclipse-plugin` | Reusable base implementations of JDBC interfaces |
| `com.dbeaver.rest.client` | `eclipse-plugin` | JDK `HttpClient`-based REST client and interceptors |
| `com.dbeaver.servlet.api` | `eclipse-plugin` | Shared Jakarta Servlet health and status models |
| `com.dbeaver.spring.utils` | JAR | Spring JDBC and other Spring-specific helpers |

Keep dependency direction from specialized modules toward lower-level modules:

- `org.jkiss.utils` must remain lightweight and must not depend on JDBC, servlet, Spring, Eclipse UI, or product code.
- JDBC, REST, servlet, and Spring helpers may depend on `org.jkiss.utils`.
- Framework-specific behavior belongs in the corresponding specialized module, not in `org.jkiss.utils`.
- Do not add dependencies on DBeaver application repositories from DBeaver Common.

Avoid cyclic dependencies and do not move product-specific abstractions into this repository merely to make them
reachable from several consumers.

---

## Technology and Compatibility

| Area | Technology |
|---|---|
| Languages | Java, Maven XML, OSGi metadata |
| Java for OSGi modules | Java 17 |
| Java for `com.dbeaver.spring.utils` | Java 21 |
| Default Java for inheriting products | Java 21 |
| Build | Maven Wrapper and Eclipse Tycho |
| Module formats | OSGi bundles and ordinary Maven JARs |
| Tests | JUnit Jupiter and Mockito |
| CI | Maven `clean verify` plus shared Checkstyle |
| Publication | Maven Central/Sonatype profiles for public artifacts |

The four `eclipse-plugin` modules compile with Java 17 through the compatibility configuration in `modules/pom.xml`.
`com.dbeaver.spring.utils` is an ordinary Maven JAR and currently compiles for Java 21. Follow the owning module's
configured target; do not use Java 18+ APIs in Java 17 modules and do not use preview features anywhere.

OSGi packaging in this repository does not mean every consumer is an OSGi application. Published artifacts must remain
usable by ordinary Maven consumers where their API permits it.

---

## Build System

Prefer the checked-in Maven wrapper so local builds use the repository's Maven version.

### Full build

The local equivalent of the CI build is:

```bash
./mvnw clean verify
```

CI runs Maven verification from the repository root and runs the shared Checkstyle workflow separately.

### Targeted module build

Use the reactor and `-am` so required common modules are included:

```bash
./mvnw -pl :org.jkiss.utils -am test
./mvnw -pl :com.dbeaver.rest.client -am verify
```

Replace the artifact selector with the affected module. Before handoff, validate all affected modules rather than only
compiling a single source directory in an IDE.

---

## Maven and OSGi Metadata

Four modules are built as `eclipse-plugin` artifacts and contain both Maven and OSGi metadata:

```text
pom.xml
META-INF/MANIFEST.MF
build.properties
```

For these modules:

- Declare Maven dependencies in `pom.xml` and keep the corresponding OSGi `Require-Bundle` entries synchronized.
- Keep `Bundle-SymbolicName`, `Bundle-Version`, `Bundle-RequiredExecutionEnvironment`, and `Automatic-Module-Name`
  consistent with the artifact and compatibility requirements.
- Add a package to `Export-Package` only when it is intentionally public API. Do not export implementation-only
  packages.
- Keep `build.properties` aligned with the module's actual source layout and packaged content.
- Do not add `MANIFEST.MF` or Tycho packaging to `com.dbeaver.spring.utils` unless the task explicitly changes its
  distribution model.

Source layouts are not uniform:

| Module | Production source directory |
|---|---|
| `org.jkiss.utils` | `src/main/java` |
| `com.dbeaver.rest.client` | `src/main/java` |
| `com.dbeaver.spring.utils` | `src/main/java` |
| `com.dbeaver.jdbc.api` | `src/` |
| `com.dbeaver.servlet.api` | `src/` |

Follow the existing module layout; do not create a second source tree during a focused change.

---

## Dependency Management

Shared dependency and plugin versions belong in `root/pom.xml`. Prefer versions already managed there and omit explicit
versions from child modules when dependency management supplies them.

Before adding a dependency:

- Prefer the JDK and existing DBeaver Common utilities.
- Confirm that the functionality cannot reasonably be implemented with the existing stack.
- Evaluate license compatibility, known security issues, artifact size, transitive dependencies, Java 17 support, and
  OSGi availability where relevant.
- Consider every repository inheriting the parent POM, not only the module currently being changed.
- Keep BOM ordering intact. In particular, the Jetty BOMs intentionally precede the Spring Boot dependency BOM.

Do not perform unrelated dependency or version upgrades. A parent POM change has a much wider impact than an ordinary
module-local dependency change and requires proportionate cross-repository validation.

---

## Public API and Compatibility

Assume exported packages and public/protected members have external consumers. Prefer backward-compatible additions.
Do not remove, rename, narrow, or behaviorally redefine an API without explicitly identifying and coordinating all
known consumers.

When changing a shared API:

- Preserve source and binary compatibility whenever practical.
- Preserve null handling, exception behavior, ordering, equality, mutability, encoding, and thread-safety semantics.
- Be careful with overloads: a new overload can make calls with `null`, lambdas, or method references ambiguous.
- Avoid exposing framework-specific types from low-level modules.
- Keep implementation classes and helpers package-private when they are not intended as contracts.
- Update OSGi exports only when the package is intentionally supported as public API.

Changes to `root/pom.xml`, `org.jkiss.code`, `DBException`, HTTP/REST contracts, JDBC base classes, or exported utility
methods deserve extra compatibility review because they have especially broad usage.

---

## Utility Design

Before adding a helper, search this repository and the JDK for an existing implementation. Reuse established methods
such as `CommonUtils.isEmpty(...)`, `CommonUtils.isNotEmpty(...)`, `CommonUtils.isEmptyTrimmed(...)`, `ArrayUtils`,
`MapUtils`, `IOUtils`, `StringUtils`, and `XMLUtils` instead of duplicating their behavior.

When a new utility is justified:

- Add it to the narrowest appropriate utility class; do not put every helper in `CommonUtils`.
- Keep it generic and independent of a particular DBeaver product or business domain.
- Define null, empty, invalid-input, and error behavior explicitly with annotations and tests.
- Prefer deterministic, stateless implementations without hidden I/O, environment, locale, timezone, or global-state
  dependencies.
- Add focused tests for boundary cases and regression behavior.
- Avoid creating a new dependency for trivial functionality.

If a helper is only meaningful to one product or module, keep it with that consumer instead of expanding DBeaver
Common's public surface.

---

## Code Conventions

Follow `.github/dbeaver-checkstyle-config.xml` and the style of the module being changed. Keep changes minimal and scoped
to the task. Do not perform unrelated formatting, cleanup, renaming, refactoring, or dependency updates.

### Nullability

Use `@NotNull`, `@Nullable`, and, where appropriate, `@NotNullWhen` from `org.jkiss.code` on method parameters and return
values. Keep annotations consistent across overrides. Do not replace these annotations with a framework-specific
nullability package.

### Logging

Low-level common modules use `java.util.logging` and deliberately avoid a product logging dependency:

```java
private static final Logger log = Logger.getLogger(MyClass.class.getName());
```

Follow the existing module's logging approach. Do not introduce SLF4J, Log4j, DBeaver UI logging, `System.out`, or
`System.err` merely for a new log statement. Never log credentials, authorization headers, tokens, private keys, or
sensitive payloads.

### Exceptions

Preserve the abstraction's established exception contract:

- JDBC interface implementations use `SQLException` and its appropriate subclasses.
- Shared REST and utility APIs commonly expose `DBException` where the existing contract does so.
- Preserve the original exception as the cause when translating failures.
- Do not silently swallow failures or expose secrets in exception messages.

### License headers

The repository is distributed under Apache License 2.0 and most Java sources use the DBeaver Apache 2.0 header.

---

## Testing

Current unit tests live under `modules/org.jkiss.utils/src/test/java` and use JUnit Jupiter. Keep tests in the module that
owns the behavior. If another module gains testable behavior, add focused test configuration to that module rather than
placing its tests in `org.jkiss.utils`.

Testing expectations:

- Add regression tests for bug fixes and new shared utility behavior.
- Cover nulls, empty values, malformed input, boundaries, encoding, platform differences, and concurrency when relevant.
- Keep tests deterministic and independent of network access, production services, user files, locale, and timezone
  unless the test explicitly controls them.
- Verify both success and failure behavior for REST, JDBC, parsing, and transaction changes.
- Do not weaken or delete a test merely to make a behavior change pass.

For changes to an exported API or the parent POM, supplement local tests with validation in at least one representative
consumer when practical.

---

## Security

- Validate untrusted URLs, paths, headers, serialized data, XML, and process arguments at the appropriate boundary.
- Preserve secure TLS and certificate validation defaults. Never add a bypass that is enabled by default.
- Avoid unsafe deserialization, uncontrolled reflection, XML external entities, command injection, and path traversal.
- Do not commit credentials, tokens, signing material, private repository URLs, local environment files, or generated
  artifacts.
- Report discovered vulnerabilities according to `SECURITY.md`; do not publish exploit details in a public issue.

Security-sensitive changes to HTTP, OAuth, XML, filesystem, process, or cryptographic utilities require negative-path
tests and careful compatibility review.

---

## Branches and Git Workflow

- `devel` is the default development and pull-request target.
- Use a release branch only when the task explicitly requires a backport.
- Issue references, commit messages, and PR titles normally use
  `dbeaver/<issue-repository>#<issue-number> <description>`, where `<issue-repository>` is the repository in which the
  ticket was created. For example, using a deliberately fictitious issue number:
  `dbeaver/dbeaver#999999 Add new function`.
- Branches normally use `dbeaver/<issue-repository>#<issue-number>-<short-description>`. Quote branch names containing
  `#` in shell commands. For example: `'dbeaver/dbeaver#999999-add-new-function'`.
- Every PR must link to its original ticket. Put the GitHub closing keyword on the first line of the PR description:

  ```text
  Closes dbeaver/<issue-repository>#<issue-number>
  ```

  A full issue URL is also acceptable. Use the repository where the ticket was actually created, not necessarily the
  repository receiving the PR.
- Create pull requests directly in the **Ready for review** state. Do not create a draft PR unless the user or ticket
  owner explicitly requests one.
- Identify compatibility impact, affected consumers, tests, dependency/manifest changes, and any follow-up work in the
  PR description.
- If AI tools materially generated code or documentation, disclose their use in the PR description.

---

## Validation Before Handoff

- Run the narrowest relevant Maven tests while iterating, then verify every affected module with `-am`.
- Run the full `./mvnw clean verify` build for parent POM, dependency-management, packaging, manifest, or multi-module
  changes when practical.
- Check that Maven dependencies, OSGi requirements, exported packages, source layout, and Java 17 compatibility remain
  aligned.
- Run `git diff --check` and review the final diff for generated files, secrets, unrelated edits, and accidental API or
  dependency changes.
- Report the exact checks run and clearly identify checks that could not be completed.

---

## Common Pitfalls

1. **`root/pom.xml` is not the full reactor.** Build from the top-level `pom.xml`.
2. **Java targets differ.** OSGi modules target Java 17, while `com.dbeaver.spring.utils` currently targets Java 21. A
   build running on Java 21 does not make Java 21 APIs safe in the Java 17 modules.
3. **OSGi modules have dual dependency metadata.** Keep `pom.xml` and `MANIFEST.MF` synchronized.
4. **Source layouts differ by module.** Follow `build.properties` and the existing module POM.
5. **Exported packages are public API.** Do not export implementation packages by default.
6. **Parent POM changes affect many repositories.** Avoid unrelated version upgrades and validate representative
   consumers.
7. **Tests are currently concentrated in `org.jkiss.utils`.** This does not remove the need to test behavior in other
   modules.
8. **The README contains stale details.** It still mentions a standalone `com.dbeaver.rpc` module and older example
   versions; verify the current reactor and POMs instead of relying on those examples.
9. **Publishing profiles have external effects.** Never run deploy, release, signing, or Sonatype publication without an
   explicit request.
