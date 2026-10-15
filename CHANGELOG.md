# Changelog

All notable changes are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org/).

## [0.1.0] - 2026-10-15

First public version, published to Maven Central as `io.github.judge4j:judge4j-*:0.1.0`.

### Added

- `@EvalTest`, `@EvalTestCases`, `@Input` and `@EvalVariants`: run a test method once per dataset case
  and variant against a Spring test context.
- Weighted, named assertions with run-to-completion semantics, and an error result recorded for every
  failure mode (throwing method, failing variant setup, no assertions, throwing evaluator).
- `Evaluator<T>` with `negate()`; `Matchers` for strings and JSON (`equals`, `contains`, `containsAny`,
  `containsAll`, `startsWith`, `regex`, `isJson`, `containsJson`, `jsonEquals`, case-insensitive variants).
- `RubricEvaluator` (LLM judge) and `EmbeddingSimilarityEvaluator` for Spring AI 1.1.
- YAML datasets with unique ids, variable order preserved, and conversion of inputs to the parameter type.
- JSON results and a self-contained HTML comparison report: per-variant statistics, per-assertion
  chart, heatmap, side-by-side grid, per-cell drill-down, light and dark themes, and a two-run comparison.
- A runnable example (document summarizer) with a prompt comparison, a three-model comparison, an LLM
  judge, embedding similarity and deliberate failures.
- Live evaluations are tagged `eval` and excluded from the default build; the `evals` profile runs them.
- Evaluation classes run serially under an exclusive lock, even with JUnit parallelism on.
- Release tooling: Apache-2.0 license, Maven wrapper, `release` profile (sources, Javadoc, signing),
  `scripts/check-release-ready.sh`, CI on Java 17 and 21, and a tag-driven release workflow (GitHub Release
  with jars attached, optional Maven Central publish) plus `jitpack.yml`.
