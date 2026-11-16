# Shared Java lint

`java-checkstyle.yml` runs the official Checkstyle CLI with the unchanged
`dbeaver-checkstyle-config.xml` and the DBeaver nullability plugin on its classpath.
It does not use a Checkstyle wrapper action, Docker, reviewdog, or a reporting token.

The CLI version and its expected SHA-256 are stored together in the workflow.
When updating them, verify the official release asset before updating the digest.
The nullability plugin checkout and GitHub actions are pinned to commit SHAs.
The plugin's Maven tests and the CLI/reporting regression tests run before analysis.

Analysis includes tracked Java, XML, and properties files in the caller repository.
On PRs, diagnostics are filtered to the merge diff against the PR base, including
three unchanged context lines; renamed files are treated as additions. Pushes
report all diagnostics. Existing warning severity is preserved: style/nullability
violations are advisory, but download,
plugin build, test, CLI, and XML parsing failures fail the job. Up to ten warnings
and ten errors are emitted as annotations; additional matching results remain in
the job log with per-rule totals.

By default, callers use configuration and scripts from `dbeaver-common@devel`.
DBeaver Common's own PRs use their checked-out revision. To test a coordinated
change in another repository, set both the reusable workflow reference and its
`common-ref` input to the same branch or commit. After merging the shared change,
callers can return to `@devel` and omit the input.

Run the reporter tests with:

```text
python -m unittest discover -s .github/checkstyle -p "test_*.py" -v
```

Set `CHECKSTYLE_JAR` and `CHECKSTYLE_PLUGIN` to enable the real CLI integration
tests. They exercise custom style rules, constructor/method nullability,
recognized annotations, and invalid configurations.
