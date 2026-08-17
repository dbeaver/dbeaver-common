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

## Compatibility, build, and OSGi

- OSGi modules target Java 17; `com.dbeaver.spring.utils` and inheriting products use Java 21.
- Do not use Java 18+ APIs in Java 17 modules or preview features.
- Build from the top-level POM; `root/pom.xml` is a parent/version-management POM, not the reactor.

```bash
./mvnw clean verify
./mvnw -pl :org.jkiss.utils -am test
./mvnw -pl :com.dbeaver.rest.client -am verify
```

- For `eclipse-plugin` modules, keep `pom.xml`, `MANIFEST.MF`, `build.properties`, Maven dependencies, OSGi requirements,
  exports, versions, execution environments, and module names aligned.
- Export only intentional public API packages.

Source layouts differ: utils, REST, and Spring use `src/main/java`; JDBC and servlet use `src/`. Do not create a second
source tree or add OSGi packaging to the Spring module.

## Dependencies, API, and utilities

- Shared dependency and plugin versions belong in `root/pom.xml`; use managed versions in modules.
- Before adding a dependency, prefer the JDK and existing utilities; review license, security, Java 17, OSGi, and
  downstream impact.
- Assume exported packages and public/protected members have external consumers.
- Preserve source/binary compatibility, null behavior, exceptions, ordering, equality, mutability, encoding, and thread
  safety. New overloads must not make existing calls ambiguous.
- Keep implementation helpers package-private and framework types out of low-level APIs.
- Search the JDK and reuse `CommonUtils`, `ArrayUtils`, `MapUtils`, `IOUtils`, `StringUtils`, and `XMLUtils` before adding
  a helper.
- Add generic, tested helpers to the narrowest class; keep product-specific helpers in the consuming repository.
- Keep changes minimal and follow the repository's Checkstyle, nullability, exception, and Apache header conventions.
- Use `@NotNull`, `@Nullable`, and `@NotNullWhen` from `org.jkiss.code` where applicable.
- Preserve exception causes. Low-level modules use `java.util.logging`, not SLF4J, Log4j, product logging, or
  `System.out/err`.

## Testing, security, and validation

- Keep tests in the owning module and add regression coverage for shared behavior.
- Cover relevant edge cases and keep tests independent of network services and user files.
- Validate untrusted URLs, paths, headers, XML, serialized data, and process arguments.
- Preserve TLS and certificate validation; avoid unsafe deserialization, XXE, command injection, and path traversal.
- Never commit secrets or private data; do not publish or release without an explicit request.
- Report vulnerabilities through `SECURITY.md`, not a public issue.
- Run targeted tests and the full build for parent POM, packaging, manifest, or multi-module changes.
- Verify dependencies, OSGi metadata, exports, source layout, and Java 17 compatibility; run `git diff --check`.

## Git workflow

- **Main branch** - `devel`, this is the main development branch in all repos. All other branches must use it as upstream.
- **Naming convention**: issues, commit messages, and PR titles should follow the format `dbeaver/<repo>#<issueNumber> title` (e.g., `dbeaver/dbeaver#999999 Add new function`). Repo is the repository name where issue is created.
- **Branch naming**: branches should follow the format `dbeaver/<repo>#<issueNumber>-issueTitle` (e.g., `dbeaver/dbeaver#999999-add-new-function`).
- **Linking PRs to issues**: start the PR description with `Closes dbeaver/<repo>#<issueNumber>` for the original ticket.
- **Review state** — create pull requests directly in the **Ready for review** state. Create a draft only when the user or ticket owner explicitly requests one.
- **AI-generated PRs**: large pull requests that are entirely AI-generated are strongly discouraged. Keep AI-assisted contributions focused and small, and ensure each change is understood and reviewed by a human contributor.
- **AI tools disclosure**: if AI tools were used to generate code, mention it in the PR description. Example: *This PR was generated with AI (GitHub Copilot)*.
