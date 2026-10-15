# Judge4j roadmap

What `0.1.0` contains is described in [README.md](README.md) and [ARCHITECTURE.md](ARCHITECTURE.md).
This file lists planned additions. Each is a separate, additive piece of work; nothing in `0.1.0` depends
on them.

## Next: make comparison easier to write

Today a prompt or model comparison needs a Mockito spy, a seam method on the service and a hand-written
`EvalVariantFactory` (see `judge4j-examples`). A typed layer on top would remove that ceremony.

- [ ] `PromptVariant` / `ModelVariant`: self-describing variants that compile down to `EvalVariant`, so
      the report can show *what* differs between columns (the prompt text, the model id).
- [ ] A declarative surface (for example `@ComparePrompts` / `@CompareModels`) that installs the spy,
      stubs the seam and resets it, plus guards: the seam bean is missing or not a spy, the seam method
      was never called during a run (so the variants cannot differ), labels are duplicated.
- [ ] Optional variant teardown so state never leaks between evaluation classes.

## Result model and report

- [ ] Capture the rendered prompt, the raw model output and the post-processed output separately, so a
      failing case shows whether the prompt or the pipeline is at fault.
- [ ] Token counts and cost per run and per variant; latency of the model call itself.
- [ ] Baseline comparison with a pass/fail gate (`assertAgainstBaseline`, a minimum pass rate) and a real
      difference view in `generateDiff`.
- [ ] Per-assertion thresholds; non-binary rubric scores.

## Evaluators

- [ ] JSON-schema, numeric-range and length matchers; fuzzy / edit distance.
- [ ] Pairwise "which is better, A or B" judge for variant comparison.
- [ ] Multi-sample evaluation for non-deterministic outputs (run each case N times, report variance).
- [ ] `expected` / `reference` as first-class dataset fields shown in the report.

## Datasets

- [ ] JSONL and CSV; files outside the classpath; per-case tags; sampling and filtering
      (`-Djudge4j.cases=...`).

## Execution

- [ ] Variants without Spring (plain JUnit).
- [ ] Safe parallel execution (today evaluation classes take an exclusive lock and run one at a time).
- [ ] Optional response caching or record/replay, only if gating prompt regressions in CI becomes a real
      need (deterministic, free reruns).
- [ ] Built-in throttling and retry for provider rate limits.

## Packaging

- [ ] Reproducible builds (a fixed `project.build.outputTimestamp`) so the jars on the GitHub Release and on
      Maven Central are byte-identical; today the release workflow builds twice.
- [ ] A BOM for the five library artifacts.
