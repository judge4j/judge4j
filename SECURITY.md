# Security policy

## Reporting a vulnerability

Please report suspected vulnerabilities privately through GitHub's **Security > Report a
vulnerability** form on this repository rather than in a public issue. Include what you found, how to
reproduce it, and the version. You can expect an acknowledgement within a few days.

## What to keep in mind when using Judge4j

- **Model output is untrusted text.** The HTML report escapes every value and embeds its data so that
  nothing can close the surrounding `<script>` element; there is a test for this. Still, treat reports
  built from untrusted inputs as you would any file containing untrusted text.
- **Rubric judges read untrusted text.** `RubricEvaluator` delimits the request and response and tells
  the judge to treat them as data, which reduces, but cannot eliminate, prompt-injection risk in LLM
  judging. Do not rely on a judge verdict for security decisions.
- **Reports contain your inputs and outputs.** They are written to `target/judge4j` by default; do not
  publish them if your datasets contain sensitive data.
- **API keys** are read from your application's own configuration and are never written to reports.
