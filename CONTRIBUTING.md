# Contributing to Judge4j

Thanks for helping. This is a small library, so the process is light.

## Build and test

You need JDK 17 or newer. The Maven wrapper is included.

```sh
./mvnw verify                        # builds everything and runs every unit test
```

The default build needs **no network access to a model provider and no API key**: live-model
evaluations are tagged `eval` and excluded. To run them (they call real models and cost money):

```sh
export GOOGLE_API_KEY=...            # or the key for the provider set in application.yml
./mvnw test -Pevals -pl judge4j-examples -am
```

Open `judge4j-examples/target/judge4j/*.html` to see the reports.

## Ground rules

- **Every behaviour has a test, including the awkward inputs** (null, empty, malformed, hostile). The
  default build must stay free of network access and credentials; test the engine with the small stand-in
  services in `judge4j-spring-test` and stub models in `judge4j-spring-ai`, not with a live model.
- **Changing the report template?** Run `judge4j-core`'s `ReportGalleryTest`, then open
  `judge4j-core/target/report-gallery/*.html` in a browser and check the console for script errors.
- **Public API gets Javadoc**, and the Javadoc must pass `-Prelease` lint (see below).
- **Never commit secrets.** `scripts/check-release-ready.sh` fails on key-shaped literals; run it before
  you push. Configuration reads keys from environment variables only.
- Keep modules honest about their dependencies: `judge4j-core` must not depend on JUnit or Spring.

## Before opening a pull request

```sh
./mvnw verify
./mvnw -Prelease -Dgpg.skip=true -DskipTests package   # sources + Javadoc jars, Javadoc lint
scripts/check-release-ready.sh
```

CI runs the same on Java 17 and 21.

## Adding things

- **A matcher:** add a static method to `Matchers` that validates its arguments eagerly, treats a `null`
  actual as a failure, and has tests in `MatchersEdgeCasesTest`.
- **An LLM-judged criterion:** subclass `RubricEvaluator` (see `SummaryGroundedInDocumentEvaluator`).
- **An evaluator type:** implement `Evaluator<T>`; scores must be within `[0, 1]`.

## Releasing

Maintainers only. A release is **one tag push**; GitHub Actions does the rest (`.github/workflows/release.yml`):

```sh
scripts/check-release-ready.sh                # secrets and license check
git tag v0.1.0
git push origin v0.1.0
```

The workflow then:

1. sets the version from the tag (`v0.1.0` becomes `0.1.0`; nothing is committed back);
2. runs every unit test and builds the jars with sources and Javadoc for the five library modules
   (`judge4j-examples` is never released);
3. creates a **GitHub Release** with generated notes and all the jars attached. This needs no setup;
4. signs the jars and uploads them to **Maven Central**, if the secrets below exist.

Everything here is free for a public repository. After the tag, JitPack can also serve that tag with no
further work (`jitpack.yml`).

### One-time setup for Maven Central

1. **Account and namespace.** Sign in at <https://central.sonatype.com> with GitHub. Under *Namespaces*, add
   `io.github.judge4j` and verify it (create the temporary public repo it names inside the `judge4j`
   organization, then click *Verify*).
2. **A signing key.** Central requires signed artifacts:

   ```sh
   gpg --full-generate-key                              # RSA 4096; use your real name and email
   gpg --list-secret-keys --keyid-format LONG           # note the key id after "rsa4096/"
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>
   gpg --armor --export-secret-keys <KEYID> | pbcopy    # the private key, for the GPG_PRIVATE_KEY secret
   ```

3. **A Central token.** In the Central Portal open *Account > Generate User Token*. It gives a username
   and a password.
4. **Repository secrets.** In GitHub: *Settings > Secrets and variables > Actions > New repository secret*:

   | Secret | Value |
   |---|---|
   | `CENTRAL_USERNAME` | the token's username |
   | `CENTRAL_PASSWORD` | the token's password |
   | `GPG_PRIVATE_KEY` | the exported private key, including the `BEGIN` and `END` lines |
   | `GPG_PASSPHRASE` | the key's passphrase |

Until those secrets exist the workflow still creates the GitHub Release and simply skips Central.

The first upload lands in the Central Portal as a *deployment* waiting for your approval: review it and click
**Publish**. (To publish automatically from then on, add `<autoPublish>true</autoPublish>` to the
`central-publishing-maven-plugin` configuration in the root `pom.xml`.) The jars are usually downloadable from
Maven Central within about 30 minutes.

To build the release artifacts locally without signing or publishing:

```sh
./mvnw -Prelease -Dgpg.skip=true -DskipTests package -pl '!judge4j-examples'
```

After a release, bump the working version if you like (`./mvnw versions:set -DnewVersion=0.1.1-SNAPSHOT`).

## License

By contributing you agree that your contributions are licensed under the Apache License 2.0.
