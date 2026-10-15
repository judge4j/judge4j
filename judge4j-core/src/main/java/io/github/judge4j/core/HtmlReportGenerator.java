package io.github.judge4j.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes a {@link RunResult} as one self-contained HTML file: the run data is embedded as JSON and
 * rendered in the browser, so the file opens anywhere with no server and makes no network requests.
 * It follows the viewer's light or dark theme.
 */
public final class HtmlReportGenerator implements ReportGenerator {

    @Override
    public void generate(RunResult run, Path outputPath) throws IOException {
        write(outputPath, "Judge4j Report", Map.of("run", Json.sanitize(run)));
    }

    @Override
    public void generateDiff(RunResult baseline, RunResult candidate, Path outputPath) throws IOException {
        write(outputPath, "Judge4j Run Comparison",
                Map.of("baseline", Json.sanitize(baseline), "candidate", Json.sanitize(candidate)));
    }

    private void write(Path outputPath, String title, Object data) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // "<" is escaped so no value can close the surrounding <script> element.
        String json = Json.mapper().writeValueAsString(data).replace("<", "\\u003c");
        String html = TEMPLATE.replace("__TITLE__", title).replace("__DATA__", json);
        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
    }

    private static final String TEMPLATE = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>__TITLE__</title>
            <style>
            :root {
              --page: #f4f5f7; --surface: #ffffff; --surface-2: #f8f9fb;
              --border: #e4e6eb; --border-strong: #d3d6dd;
              --ink: #12141a; --ink-secondary: #565c6b; --ink-muted: #8a8f9c;
              --accent: #4f63d2; --accent-soft: rgba(79, 99, 210, 0.1);
              --pass: #14804f; --pass-soft: rgba(20, 128, 79, 0.12);
              --fail: #c22a3e; --fail-soft: rgba(194, 42, 62, 0.11);
              --shadow-sm: 0 1px 2px rgba(16, 24, 40, 0.04);
              --shadow-md: 0 8px 24px rgba(16, 24, 40, 0.08);
              --radius-sm: 8px; --radius-md: 12px; --radius-lg: 18px;
              --series-1: #2a78d6; --series-2: #eb6834; --series-3: #1baf7a; --series-4: #eda100;
              --series-5: #e87ba4; --series-6: #008300; --series-7: #4a3aa7; --series-8: #e34948;
              color-scheme: light;
            }
            @media (prefers-color-scheme: dark) {
              :root {
                --page: #0c0d10; --surface: #16171c; --surface-2: #1c1e24;
                --border: #2a2c33; --border-strong: #383b44;
                --ink: #f2f3f5; --ink-secondary: #b7bac3; --ink-muted: #82868f;
                --accent: #8ea1ff; --accent-soft: rgba(142, 161, 255, 0.15);
                --pass: #35c98a; --pass-soft: rgba(53, 201, 138, 0.15);
                --fail: #ff6b7a; --fail-soft: rgba(255, 107, 122, 0.15);
                --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.4);
                --shadow-md: 0 8px 24px rgba(0, 0, 0, 0.5);
                --series-1: #3987e5; --series-2: #d95926; --series-3: #199e70; --series-4: #c98500;
                --series-5: #d55181; --series-6: #008300; --series-7: #9085e9; --series-8: #e66767;
                color-scheme: dark;
              }
            }
            * { box-sizing: border-box; }
            body {
              color: var(--ink); background: var(--page);
              font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
              -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility;
              max-width: 1320px; margin: 0 auto; padding: 40px 24px 100px;
            }
            .eyebrow {
              display: inline-block; font-size: 0.68rem; font-weight: 700; letter-spacing: 0.1em;
              text-transform: uppercase; color: var(--accent); background: var(--accent-soft);
              padding: 3px 10px; border-radius: 999px; margin-bottom: 12px;
            }
            h1 { margin: 0 0 8px; font-size: 1.65rem; font-weight: 700; letter-spacing: -0.015em; }
            h2 {
              font-size: 0.78rem; margin: 44px 0 14px; color: var(--ink-muted); font-weight: 700;
              text-transform: uppercase; letter-spacing: 0.08em;
            }
            .meta-line { color: var(--ink-secondary); font-size: 0.9rem; margin: 0 0 28px; }
            .meta-line .dot { color: var(--border-strong); margin: 0 8px; }
            .meta-line code {
              font-family: ui-monospace, monospace; font-size: 0.8rem; color: var(--ink-muted);
              background: var(--surface-2); padding: 1px 6px; border-radius: 4px;
            }
            .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 14px; }
            .metric {
              border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--surface);
              padding: 16px 20px; box-shadow: var(--shadow-sm); position: relative; overflow: hidden;
            }
            .metric.accent::before {
              content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: var(--metric-accent, var(--accent));
            }
            .metric span {
              font-size: 0.7rem; color: var(--ink-muted); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600;
            }
            .metric strong { display: block; font-size: 1.7rem; margin-top: 6px; font-weight: 700; letter-spacing: -0.01em; }
            .charts-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 16px; }
            .card {
              border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--surface);
              padding: 22px; box-shadow: var(--shadow-sm);
            }
            .card + .card { margin-top: 16px; }
            .card-title { font-size: 0.92rem; font-weight: 700; margin: 0 0 4px; }
            .card-subtitle { font-size: 0.8rem; color: var(--ink-muted); margin: 0 0 16px; }
            .chart-legend { display: flex; gap: 16px; flex-wrap: wrap; font-size: 0.78rem; color: var(--ink-secondary); margin-bottom: 10px; }
            .chart-legend .swatch { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 6px; vertical-align: middle; }
            .chart-wrap { position: relative; overflow-x: auto; }
            .chart-wrap svg [data-tooltip], .chart-wrap svg [data-label] { cursor: pointer; }
            .chart-tooltip {
              position: absolute; pointer-events: none; background: var(--ink); color: var(--surface);
              font-size: 0.75rem; padding: 6px 10px; border-radius: 6px; opacity: 0; transition: opacity 0.1s ease;
              white-space: nowrap; box-shadow: var(--shadow-md); z-index: 5; transform: translateY(-100%);
            }
            .stats-table { width: 100%; border-collapse: collapse; margin-top: 18px; font-size: 0.84rem; }
            .stats-table th { color: var(--ink-muted); font-weight: 700; font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.05em; }
            .table-scroll {
              overflow-x: auto; border: 1px solid var(--border); border-radius: var(--radius-lg);
              background: var(--surface); box-shadow: var(--shadow-sm);
            }
            table.report { width: 100%; border-collapse: collapse; font-size: 0.87rem; }
            table.report th, table.report td { padding: 14px; text-align: left; vertical-align: top; }
            table.report th {
              background: var(--surface-2); color: var(--ink-muted); font-weight: 700; font-size: 0.7rem;
              text-transform: uppercase; letter-spacing: 0.05em; position: sticky; top: 0; z-index: 1;
            }
            table.report th:first-child { border-top-left-radius: var(--radius-lg); }
            table.report tbody tr:nth-child(even) { background: var(--surface-2); }
            table.report tbody tr:hover { background: var(--accent-soft); }
            .heatmap { width: 100%; border-collapse: collapse; font-size: 0.82rem; }
            .heatmap th { background: var(--surface-2); color: var(--ink-muted); font-weight: 700; font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.05em; padding: 10px; text-align: left; }
            .heatmap td { padding: 0; text-align: center; }
            .heat-cell {
              padding: 12px 10px !important; cursor: pointer; font-weight: 700; color: var(--ink);
              transition: outline 0.1s ease;
            }
            .heat-cell:hover { outline: 2px solid var(--accent); outline-offset: -2px; }
            table.report th, table.report td,
            .stats-table th, .stats-table td,
            .assertion-table th, .assertion-table td,
            .heatmap th, .heatmap td {
              border-right: 1px solid var(--border); border-bottom: 1px solid var(--border);
            }
            table.report th:last-child, table.report td:last-child,
            .stats-table th:last-child, .stats-table td:last-child,
            .assertion-table th:last-child, .assertion-table td:last-child,
            .heatmap th:last-child, .heatmap td:last-child {
              border-right: none;
            }
            table.report tbody tr:last-child td,
            .stats-table tbody tr:last-child td,
            .assertion-table tbody tr:last-child td,
            .heatmap tbody tr:last-child td {
              border-bottom: none;
            }
            .variables-cell { min-width: 210px; max-width: 260px; }
            .case-desc { color: var(--ink-secondary); margin-bottom: 8px; font-weight: 600; }
            .case-meta {
              color: var(--ink-muted); margin-top: 10px; font-size: 0.74rem; font-family: ui-monospace, monospace;
              padding-top: 8px; border-top: 1px dashed var(--border);
            }
            .kv { margin: 8px 0; }
            .kv:first-child { margin-top: 0; }
            .kv .k { display: block; color: var(--ink-muted); font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 3px; font-weight: 600; }
            .kv .v { display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; color: var(--ink-secondary); }
            .link-btn {
              background: none; border: none; color: var(--accent); cursor: pointer; font: inherit;
              padding: 0; margin-top: 8px; font-size: 0.8rem; font-weight: 600;
            }
            .link-btn:hover { text-decoration: underline; }
            .pill {
              display: inline-block; padding: 4px 11px; border-radius: 999px; font-size: 0.7rem;
              font-weight: 700; letter-spacing: 0.04em;
            }
            .pill.pass { background: var(--pass-soft); color: var(--pass); }
            .pill.fail { background: var(--fail-soft); color: var(--fail); }
            .muted { color: var(--ink-muted); }
            .output-cell { min-width: 270px; max-width: 400px; }
            .fail-banner {
              background: var(--fail-soft); color: var(--fail); border-radius: var(--radius-sm); padding: 8px 12px;
              font-size: 0.8rem; font-weight: 600; margin-bottom: 10px; line-height: 1.4;
            }
            .metric-chip {
              display: inline-block; border: 1px solid var(--border); border-radius: 6px; padding: 3px 9px;
              font-size: 0.72rem; margin: 0 6px 6px 0; background: var(--surface-2); color: var(--ink-secondary); font-weight: 600;
            }
            .metric-chip.pass { border-color: transparent; background: var(--pass-soft); color: var(--pass); }
            .metric-chip.fail { border-color: transparent; background: var(--fail-soft); color: var(--fail); }
            .output-block {
              white-space: pre-wrap; overflow-wrap: anywhere; max-height: 260px; overflow-y: auto;
              font-size: 0.85rem; line-height: 1.5; margin-top: 4px; color: var(--ink);
              scrollbar-width: thin;
            }
            .output-block::-webkit-scrollbar { width: 6px; }
            .output-block::-webkit-scrollbar-thumb { background: var(--border-strong); border-radius: 4px; }
            .variant-header { min-width: 230px; }
            .variant-name { font-size: 0.86rem; color: var(--ink); font-weight: 700; text-transform: none; letter-spacing: 0; margin-bottom: 6px; }
            .variant-stats { font-size: 0.73rem; color: var(--ink-secondary); text-transform: none; letter-spacing: 0; font-weight: 500; margin-bottom: 8px; }
            .variant-stats strong { color: var(--pass); font-weight: 700; }
            .overlay {
              position: fixed; inset: 0; background: rgba(10, 11, 14, 0.5); display: flex;
              align-items: flex-start; justify-content: center; padding: 60px 16px; overflow-y: auto; z-index: 20;
              opacity: 0; visibility: hidden; pointer-events: none; transition: opacity 0.15s ease;
              backdrop-filter: blur(2px);
            }
            .overlay.open { opacity: 1; visibility: visible; pointer-events: auto; }
            .modal {
              background: var(--surface); border-radius: var(--radius-lg); max-width: 820px; width: 100%; padding: 28px;
              position: relative; box-shadow: var(--shadow-md); border: 1px solid var(--border);
              transform: translateY(6px) scale(0.98); transition: transform 0.15s ease;
            }
            .overlay.open .modal { transform: translateY(0) scale(1); }
            .modal h3 { margin: 0 0 6px; font-size: 1.15rem; font-weight: 700; }
            .modal-close {
              position: absolute; top: 18px; right: 20px; background: var(--surface-2); border: 1px solid var(--border);
              border-radius: 999px; width: 30px; height: 30px; font-size: 1.1rem; cursor: pointer; color: var(--ink-muted); line-height: 1;
            }
            .modal-close:hover { color: var(--ink); border-color: var(--border-strong); }
            .modal pre {
              background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 12px;
              white-space: pre-wrap; overflow-wrap: anywhere; font-family: ui-monospace, monospace; font-size: 0.82rem;
            }
            .assertion-table { width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 0.85rem; }
            .assertion-table th { color: var(--ink-muted); font-weight: 700; font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.05em; }
            details summary { cursor: pointer; color: var(--accent); font-size: 0.82rem; font-weight: 600; }
            .assertion-list { margin-top: 16px; display: flex; flex-direction: column; gap: 12px; }
            .assertion-item { border: 1px solid var(--border); border-radius: var(--radius-md); padding: 16px; }
            .assertion-item-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 4px; }
            .assertion-item-head strong { font-size: 0.94rem; }
            .assertion-field { margin-top: 10px; }
            .assertion-field .k {
              display: block; color: var(--ink-muted); font-size: 0.7rem; text-transform: uppercase;
              letter-spacing: 0.05em; font-weight: 600; margin-bottom: 4px;
            }
            .assertion-field p { margin: 0; color: var(--ink-secondary); font-size: 0.86rem; }
            .assertion-field pre { max-height: 220px; overflow-y: auto; margin-top: 0; }
            </style></head><body>
            <span class="eyebrow">Evaluation report</span>
            <h1>__TITLE__</h1>
            <div class="meta-line" id="meta"></div>
            <div class="summary" id="summary"></div>
            <h2>Variant comparison</h2>
            <div class="charts-grid">
              <div class="card">
                <div class="card-title">Pass rate by variant</div>
                <div class="card-subtitle">Click a bar to see which cases passed or failed</div>
                <div class="chart-legend">
                  <span><span class="swatch" style="background:var(--pass)"></span>Pass</span>
                  <span><span class="swatch" style="background:var(--fail)"></span>Fail</span>
                </div>
                <div class="chart-wrap" id="chartwrap-passfail">
                  <div id="chart-passfail"></div>
                  <div class="chart-tooltip" id="tooltip-passfail"></div>
                </div>
              </div>
              <div class="card">
                <div class="card-title">Assertion score by variant</div>
                <div class="card-subtitle">Average score per assertion &mdash; click a bar for the underlying cases</div>
                <div class="chart-legend" id="legend-assertions"></div>
                <div class="chart-wrap" id="chartwrap-assertions">
                  <div id="chart-assertions"></div>
                  <div class="chart-tooltip" id="tooltip-assertions"></div>
                </div>
              </div>
            </div>
            <div class="card">
              <div class="card-title">Score heatmap</div>
              <div class="card-subtitle">Every test case &times; variant &mdash; click a cell for the full assertion breakdown</div>
              <div class="table-scroll"><table class="heatmap" id="heatmap"></table></div>
            </div>
            <div class="card">
              <div class="card-title">Summary table</div>
              <table class="stats-table" id="stats-table"></table>
            </div>
            <h2>Test cases</h2>
            <div class="table-scroll">
              <table class="report"><thead><tr id="header-row"></tr></thead><tbody id="rows"></tbody></table>
            </div>
            <div class="overlay" id="overlay">
              <div class="modal">
                <button class="modal-close" id="modal-close" aria-label="Close">&times;</button>
                <div id="modal-body"></div>
              </div>
            </div>
            <script id="judge4j-data" type="application/json">__DATA__</script>
            <script>
            const data = JSON.parse(document.querySelector('#judge4j-data').textContent);

            function escapeHtml(value) {
              const element = document.createElement('div');
              element.textContent = value == null ? '' : String(value);
              return element.innerHTML;
            }

            function formatValue(value) {
              if (value == null) return '';
              if (typeof value === 'string') return value;
              return JSON.stringify(value, null, 2);
            }

            function formatTimestamp(ts) {
              if (ts == null) return '';
              const ms = typeof ts === 'number' ? ts * 1000 : Date.parse(ts);
              if (Number.isNaN(ms)) return String(ts);
              return new Date(ms).toLocaleString();
            }

            function seriesColor(index) {
              return 'var(--series-' + ((index % 8) + 1) + ')';
            }

            const CASE_ID_PATTERN = /^(.+)#(.+):(.+)$/;

            function parseCaseId(testCaseId) {
              const match = CASE_ID_PATTERN.exec(testCaseId || '');
              if (!match) return { className: null, methodName: null, caseId: testCaseId };
              return { className: match[1], methodName: match[2], caseId: match[3] };
            }

            function buildModel() {
              let allResults = [];
              let meta = '';
              if (data.run) {
                const run = data.run;
                allResults = run.results.map(r => ({ ...r, columnKey: r.variantLabel }));
                meta = '<strong>' + escapeHtml(run.label) + '</strong>' +
                  '<span class="dot">\\u2022</span>' + escapeHtml(formatTimestamp(run.timestamp)) +
                  '<span class="dot">\\u2022</span><code>' + escapeHtml(run.runId) + '</code>';
              } else {
                const baseline = data.baseline, candidate = data.candidate;
                const tag = (run, r) => run.label + ' \\u00b7 ' + r.variantLabel;
                allResults = [
                  ...baseline.results.map(r => ({ ...r, columnKey: tag(baseline, r) })),
                  ...candidate.results.map(r => ({ ...r, columnKey: tag(candidate, r) })),
                ];
                meta = '<strong>' + escapeHtml(baseline.label) + '</strong> vs <strong>' + escapeHtml(candidate.label) + '</strong>';
              }
              const columns = [...new Set(allResults.map(r => r.columnKey))];
              const byCase = new Map();
              for (const r of allResults) {
                if (!byCase.has(r.testCaseId)) {
                  byCase.set(r.testCaseId, {
                    testCaseId: r.testCaseId, description: r.testCaseDescription, inputs: r.inputs,
                    ...parseCaseId(r.testCaseId), cells: new Map(),
                  });
                }
                byCase.get(r.testCaseId).cells.set(r.columnKey, r);
              }
              const rows = [...byCase.values()];
              const columnStats = columns.map(col => {
                const results = allResults.filter(r => r.columnKey === col);
                const total = results.length;
                const passCount = results.filter(r => r.pass).length;
                const failCount = total - passCount;
                const avgScore = total ? results.reduce((s, r) => s + r.score, 0) / total : 0;
                const latencies = results.map(r => r.latencyMs || 0);
                const avgLatency = total ? latencies.reduce((s, v) => s + v, 0) / total : 0;
                const allAssertions = results.flatMap(r => r.assertions || []);
                const assertTotal = allAssertions.length;
                const assertPassCount = allAssertions.filter(a => a.pass).length;
                const assertionAverages = new Map();
                for (const a of allAssertions) {
                  const entry = assertionAverages.get(a.label) || { sum: 0, count: 0 };
                  entry.sum += a.score;
                  entry.count += 1;
                  assertionAverages.set(a.label, entry);
                }
                const assertionChips = [...assertionAverages.entries()].map(([label, e]) => ({ label, avgScore: e.sum / e.count }));
                return {
                  col, total, passCount, failCount, passRate: total ? passCount / total * 100 : 0, avgScore, avgLatency,
                  assertTotal, assertPassCount, assertionChips,
                };
              });
              return { meta, columns, rows, columnStats, allResults };
            }

            function buildAssertionMatrix(model) {
              const labels = [];
              const seen = new Set();
              const cellMap = new Map();
              for (const row of model.rows) {
                for (const col of model.columns) {
                  const cell = row.cells.get(col);
                  if (!cell) continue;
                  for (const a of (cell.assertions || [])) {
                    if (!seen.has(a.label)) { seen.add(a.label); labels.push(a.label); }
                    const key = a.label + '\\u0001' + col;
                    if (!cellMap.has(key)) cellMap.set(key, { sum: 0, count: 0, entries: [] });
                    const entry = cellMap.get(key);
                    entry.sum += a.score;
                    entry.count += 1;
                    entry.entries.push({
                      testCaseId: row.testCaseId, caseId: row.caseId, pass: a.pass,
                      score: a.score, feedback: a.feedback, expectation: a.expectation,
                    });
                  }
                }
              }
              return { labels, cellMap };
            }

            function renderSummary(model) {
              const totalCases = model.rows.length;
              const overallPassRate = model.columnStats.length
                ? model.columnStats.reduce((s, c) => s + c.passRate, 0) / model.columnStats.length : 0;
              const passAccent = overallPassRate >= 80 ? 'var(--pass)' : overallPassRate >= 50 ? '#c98500' : 'var(--fail)';
              const tiles = [
                ['Test cases', totalCases, false],
                ['Variants compared', model.columns.length, false],
                ['Avg pass rate', overallPassRate.toFixed(1) + '%', true],
              ];
              document.querySelector('#summary').innerHTML = tiles
                .map(([label, value, accent]) => '<div class="metric' + (accent ? ' accent' : '') + '"' +
                  (accent ? ' style="--metric-accent:' + passAccent + '"' : '') +
                  '><span>' + escapeHtml(label) + '</span><strong>' + escapeHtml(value) + '</strong></div>')
                .join('');
            }

            function attachTooltip(containerSel, wrapSel, tooltipSel) {
              const container = document.querySelector(containerSel);
              const wrap = document.querySelector(wrapSel);
              const tooltip = document.querySelector(tooltipSel);
              container.addEventListener('mousemove', event => {
                const target = event.target.closest('[data-tooltip]');
                if (!target) { tooltip.style.opacity = 0; return; }
                const wrapRect = wrap.getBoundingClientRect();
                tooltip.textContent = target.getAttribute('data-tooltip');
                tooltip.style.left = (event.clientX - wrapRect.left + 12) + 'px';
                tooltip.style.top = (event.clientY - wrapRect.top - 6) + 'px';
                tooltip.style.opacity = 1;
              });
              container.addEventListener('mouseleave', () => { tooltip.style.opacity = 0; });
              return container;
            }

            function renderPassFailChart(model) {
              const barWidth = 26, barGap = 6, groupGap = 40, leftPad = 20, topPad = 24, chartHeight = 130, labelHeight = 40;
              const groupWidth = barWidth * 2 + barGap;
              const width = leftPad * 2 + model.columnStats.length * (groupWidth + groupGap) - groupGap;
              const height = topPad + chartHeight + labelHeight;
              const maxCount = Math.max(1, ...model.columnStats.flatMap(c => [c.passCount, c.failCount]));
              const baselineY = topPad + chartHeight;
              const scale = count => count / maxCount * chartHeight;

              let gridlines = '';
              [0.25, 0.5, 0.75].forEach(fraction => {
                const y = baselineY - chartHeight * fraction;
                gridlines += '<line x1="' + leftPad + '" y1="' + y + '" x2="' + (width - leftPad + groupGap) + '" y2="' + y + '" stroke="var(--border)" stroke-width="1" stroke-dasharray="3,4"></line>';
              });

              let bars = '';
              model.columnStats.forEach((c, i) => {
                const groupX = leftPad + i * (groupWidth + groupGap);
                const passHeight = scale(c.passCount);
                const failHeight = scale(c.failCount);
                const passX = groupX;
                const failX = groupX + barWidth + barGap;
                const passTip = c.col + ' \\u2014 Pass: ' + c.passCount + '/' + c.total + ' (' + c.passRate.toFixed(0) + '%)';
                const failTip = c.col + ' \\u2014 Fail: ' + c.failCount + '/' + c.total;
                bars += '<rect data-tooltip="' + escapeHtml(passTip) + '" data-col="' + escapeHtml(c.col) + '" data-status="pass" x="' + passX + '" y="' + (baselineY - passHeight) + '" width="' + barWidth + '" height="' + Math.max(passHeight, c.passCount > 0 ? 2 : 0) + '" rx="4" fill="var(--pass)"></rect>';
                bars += '<rect data-tooltip="' + escapeHtml(failTip) + '" data-col="' + escapeHtml(c.col) + '" data-status="fail" x="' + failX + '" y="' + (baselineY - failHeight) + '" width="' + barWidth + '" height="' + Math.max(failHeight, c.failCount > 0 ? 2 : 0) + '" rx="4" fill="var(--fail)"></rect>';
                if (c.passCount > 0) bars += '<text x="' + (passX + barWidth / 2) + '" y="' + (baselineY - passHeight - 8) + '" text-anchor="middle" font-size="11" font-weight="600" fill="var(--ink-secondary)">' + c.passCount + '</text>';
                if (c.failCount > 0) bars += '<text x="' + (failX + barWidth / 2) + '" y="' + (baselineY - failHeight - 8) + '" text-anchor="middle" font-size="11" font-weight="600" fill="var(--ink-secondary)">' + c.failCount + '</text>';
                const label = c.col.length > 18 ? c.col.slice(0, 17) + '\\u2026' : c.col;
                bars += '<text x="' + (groupX + groupWidth / 2) + '" y="' + (baselineY + 20) + '" text-anchor="middle" font-size="11" fill="var(--ink)">' + escapeHtml(label) + '<title>' + escapeHtml(c.col) + '</title></text>';
              });
              const svg = '<svg width="' + Math.max(width, 200) + '" height="' + height + '" viewBox="0 0 ' + Math.max(width, 200) + ' ' + height + '">' +
                gridlines +
                '<line x1="' + leftPad + '" y1="' + baselineY + '" x2="' + (width - leftPad + groupGap) + '" y2="' + baselineY + '" stroke="var(--border-strong)" stroke-width="1.5"></line>' +
                bars + '</svg>';
              document.querySelector('#chart-passfail').innerHTML = svg;
              const chartEl = attachTooltip('#chart-passfail', '#chartwrap-passfail', '#tooltip-passfail');
              chartEl.addEventListener('click', event => {
                const target = event.target.closest('[data-status]');
                if (!target) return;
                renderVariantCasesModal(model, target.dataset.col, target.dataset.status);
              });
            }

            function renderAssertionChart(model) {
              const matrix = buildAssertionMatrix(model);
              const container = document.querySelector('#chart-assertions');
              if (matrix.labels.length === 0) {
                container.innerHTML = '<p class="muted">No assertions recorded.</p>';
                document.querySelector('#legend-assertions').innerHTML = '';
                return;
              }
              const barWidth = 20, barGap = 4, groupGap = 30, leftPad = 20, topPad = 20, chartHeight = 120, labelHeight = 40;
              const groupWidth = model.columns.length * barWidth + Math.max(0, model.columns.length - 1) * barGap;
              const width = leftPad * 2 + matrix.labels.length * (groupWidth + groupGap) - groupGap;
              const height = topPad + chartHeight + labelHeight;
              const baselineY = topPad + chartHeight;

              let gridlines = '';
              [0.25, 0.5, 0.75].forEach(fraction => {
                const y = baselineY - chartHeight * fraction;
                gridlines += '<line x1="' + leftPad + '" y1="' + y + '" x2="' + (width - leftPad + groupGap) + '" y2="' + y + '" stroke="var(--border)" stroke-width="1" stroke-dasharray="3,4"></line>';
              });

              let bars = '';
              matrix.labels.forEach((label, li) => {
                const groupX = leftPad + li * (groupWidth + groupGap);
                model.columns.forEach((col, ci) => {
                  const entry = matrix.cellMap.get(label + '\\u0001' + col);
                  const avg = entry ? entry.sum / entry.count : 0;
                  const barHeight = avg * chartHeight;
                  const x = groupX + ci * (barWidth + barGap);
                  const tip = col + ' \\u2014 ' + label + ': ' + avg.toFixed(2);
                  bars += '<rect data-tooltip="' + escapeHtml(tip) + '" data-label="' + escapeHtml(label) + '" data-col="' + escapeHtml(col) + '" x="' + x + '" y="' + (baselineY - barHeight) + '" width="' + barWidth + '" height="' + Math.max(barHeight, entry ? 2 : 0) + '" rx="3" fill="' + seriesColor(ci) + '"></rect>';
                });
                const labelText = label.length > 16 ? label.slice(0, 15) + '\\u2026' : label;
                bars += '<text x="' + (groupX + groupWidth / 2) + '" y="' + (baselineY + 20) + '" text-anchor="middle" font-size="11" fill="var(--ink)">' + escapeHtml(labelText) + '<title>' + escapeHtml(label) + '</title></text>';
              });
              const svg = '<svg width="' + Math.max(width, 200) + '" height="' + height + '" viewBox="0 0 ' + Math.max(width, 200) + ' ' + height + '">' +
                gridlines +
                '<line x1="' + leftPad + '" y1="' + baselineY + '" x2="' + (width - leftPad + groupGap) + '" y2="' + baselineY + '" stroke="var(--border-strong)" stroke-width="1.5"></line>' +
                bars + '</svg>';
              container.innerHTML = svg;
              const chartEl = attachTooltip('#chart-assertions', '#chartwrap-assertions', '#tooltip-assertions');
              chartEl.addEventListener('click', event => {
                const target = event.target.closest('[data-label]');
                if (!target) return;
                renderAssertionBreakdownModal(target.dataset.label, target.dataset.col, matrix);
              });

              document.querySelector('#legend-assertions').innerHTML = model.columns.map((col, i) =>
                '<span><span class="swatch" style="background:' + seriesColor(i) + '"></span>' + escapeHtml(col) + '</span>').join('');
            }

            function heatColor(score) {
              const t = Math.min(1, Math.abs(score - 0.5) * 2);
              const isPass = score >= 0.5;
              const base = isPass ? [20, 128, 79] : [194, 42, 63];
              const alpha = 0.08 + t * 0.35;
              return 'rgba(' + base[0] + ',' + base[1] + ',' + base[2] + ',' + alpha.toFixed(2) + ')';
            }

            function renderHeatmap(model) {
              const header = '<tr><th></th>' + model.columns.map(col => '<th>' + escapeHtml(col) + '</th>').join('') + '</tr>';
              const body = model.rows.map(row => {
                const cells = model.columns.map(col => {
                  const cell = row.cells.get(col);
                  if (!cell) return '<td class="muted">\\u2014</td>';
                  return '<td class="heat-cell" style="background:' + heatColor(cell.score) + '" data-case="' +
                    escapeHtml(row.testCaseId) + '" data-col="' + escapeHtml(col) + '">' + (cell.score * 100).toFixed(0) + '%</td>';
                }).join('');
                return '<tr><th>' + escapeHtml(row.caseId) + '</th>' + cells + '</tr>';
              }).join('');
              const table = document.querySelector('#heatmap');
              table.innerHTML = '<thead>' + header + '</thead><tbody>' + body + '</tbody>';
              table.addEventListener('click', event => {
                const target = event.target.closest('[data-case]');
                if (!target) return;
                const row = model.rows.find(r => r.testCaseId === target.dataset.case);
                if (row) renderEvalModal(row, target.dataset.col);
              });
            }

            function renderStatsTable(model) {
              const header = '<tr><th>Variant</th><th>Cases</th><th>Pass rate</th><th>Avg score</th><th>Avg latency</th></tr>';
              const body = model.columnStats.map(c => '<tr><td>' + escapeHtml(c.col) + '</td><td>' + c.total + '</td><td>' +
                c.passRate.toFixed(1) + '%</td><td>' + (c.avgScore * 100).toFixed(1) + '%</td><td>' +
                c.avgLatency.toFixed(0) + ' ms</td></tr>').join('');
              document.querySelector('#stats-table').innerHTML = header + body;
            }

            function renderInputPreview(inputs) {
              if (!inputs || Object.keys(inputs).length === 0) return '<span class="muted">No inputs</span>';
              return Object.entries(inputs).map(([key, value]) =>
                '<div class="kv"><span class="k">' + escapeHtml(key) + '</span><span class="v">' + escapeHtml(formatValue(value)) + '</span></div>'
              ).join('');
            }

            function renderHeader(model) {
              const variableHeader = '<th>Variables</th>';
              const variantHeaders = model.columnStats.map(c => {
                const chips = c.assertionChips.map(chip =>
                  '<span class="metric-chip">' + escapeHtml(chip.label) + ': ' + chip.avgScore.toFixed(2) + '</span>').join('');
                return '<th class="variant-header">' +
                  '<div class="variant-name">' + escapeHtml(c.col) + '</div>' +
                  '<div class="variant-stats">Passing: <strong>' + c.passRate.toFixed(0) + '%</strong> (' + c.passCount + '/' + c.total + ' cases, ' +
                  c.assertPassCount + '/' + c.assertTotal + ' asserts) \\u00b7 avg ' + c.avgLatency.toFixed(0) + 'ms</div>' +
                  '<div class="variant-chips">' + chips + '</div>' +
                  '</th>';
              }).join('');
              document.querySelector('#header-row').innerHTML = variableHeader + variantHeaders;
            }

            function renderOutputCell(row, col, cell) {
              if (!cell) return '<td class="output-cell"><span class="muted">\\u2014</span></td>';
              const failingAssertions = (cell.assertions || []).filter(a => !a.pass);
              let banner = '';
              if (!cell.pass) {
                const first = failingAssertions[0];
                const extra = failingAssertions.length > 1 ? ' (+' + (failingAssertions.length - 1) + ' more failing)' : '';
                const message = cell.error || (first ? (first.expectation ? 'Expected: ' + first.expectation + ' \\u2014 ' : '') + (first.feedback || 'assertion failed') : 'evaluation failed');
                banner = '<div class="fail-banner">\\u2717 FAIL \\u2014 ' + escapeHtml(message) + escapeHtml(extra) + '</div>';
              } else {
                banner = '<span class="pill pass">\\u2713 PASS</span>';
              }
              const chips = (cell.assertions || []).map(a =>
                '<span class="metric-chip ' + (a.pass ? 'pass' : 'fail') + '">' + escapeHtml(a.label) + ': ' + a.score.toFixed(2) + '</span>').join('');
              return '<td class="output-cell">' + banner +
                (chips ? '<div>' + chips + '</div>' : '') +
                '<div class="output-block">' + escapeHtml(formatValue(cell.output)) + '</div>' +
                '<button class="link-btn" data-action="eval" data-case="' + escapeHtml(row.testCaseId) + '" data-col="' + escapeHtml(col) + '">Assertion details \\u2192</button>' +
                '</td>';
            }

            function renderTable(model) {
              renderHeader(model);
              document.querySelector('#rows').innerHTML = model.rows.map(row => {
                const metaLine = row.className
                  ? '<div class="case-meta">' + escapeHtml(row.className) + ' \\u00b7 ' + escapeHtml(row.methodName) + '</div>' : '';
                const descLine = row.description ? '<div class="case-desc">' + escapeHtml(row.description) + '</div>' : '';
                const cells = model.columns.map(col => renderOutputCell(row, col, row.cells.get(col))).join('');
                return '<tr>' +
                  '<td class="variables-cell">' + descLine + renderInputPreview(row.inputs) +
                  '<button class="link-btn" data-action="input" data-case="' + escapeHtml(row.testCaseId) + '">Full input \\u2192</button>' +
                  metaLine + '</td>' +
                  cells + '</tr>';
              }).join('');
            }

            function openModal(html) {
              document.querySelector('#modal-body').innerHTML = html;
              document.querySelector('#overlay').classList.add('open');
            }

            function closeModal() {
              document.querySelector('#overlay').classList.remove('open');
            }

            function renderInputModal(row) {
              openModal('<h3>' + escapeHtml(row.caseId) + '</h3>' +
                '<p class="muted">' + escapeHtml(row.description || '') + '</p>' +
                '<pre>' + escapeHtml(formatValue(row.inputs)) + '</pre>');
            }

            function renderEvalModal(row, col) {
              const cell = row.cells.get(col);
              if (!cell) return;
              const assertions = cell.assertions || [];
              const items = assertions.map(a => '<div class="assertion-item">' +
                '<div class="assertion-item-head">' +
                '<span class="pill ' + (a.pass ? 'pass' : 'fail') + '">' + (a.pass ? 'PASS' : 'FAIL') + '</span>' +
                '<strong>' + escapeHtml(a.label) + '</strong>' +
                '<span class="metric-chip">weight ' + a.weight.toFixed(0) + '%</span>' +
                '<span class="metric-chip">score ' + a.score.toFixed(2) + '</span>' +
                '</div>' +
                (a.expectation ? '<div class="assertion-field"><span class="k">Expectation</span><p>' + escapeHtml(a.expectation) + '</p></div>' : '') +
                (a.feedback ? '<div class="assertion-field"><span class="k">Feedback</span><p>' + escapeHtml(a.feedback) + '</p></div>' : '') +
                '<div class="assertion-field"><span class="k">Input</span><pre>' + escapeHtml(formatValue(a.input)) + '</pre></div>' +
                '</div>').join('');
              openModal('<h3>' + escapeHtml(row.caseId) + ' \\u00b7 ' + escapeHtml(col) + '</h3>' +
                '<p><span class="pill ' + (cell.pass ? 'pass' : 'fail') + '">' + (cell.pass ? 'PASS' : 'FAIL') +
                '</span> \\u2014 weighted score ' + (cell.score * 100).toFixed(1) + '%</p>' +
                (cell.error ? '<pre>' + escapeHtml(cell.error) + '</pre>' : '') +
                '<div class="assertion-list">' + (items || '<p class="muted">No assertions recorded</p>') + '</div>');
            }

            function renderVariantCasesModal(model, col, status) {
              const wantPass = status === 'pass';
              const matches = model.rows
                .map(row => ({ row, cell: row.cells.get(col) }))
                .filter(entry => entry.cell && entry.cell.pass === wantPass);
              const total = model.rows.filter(row => row.cells.get(col)).length;
              const rowsHtml = matches.map(({ row, cell }) => '<tr>' +
                '<td><strong>' + escapeHtml(row.caseId) + '</strong><div class="muted">' + escapeHtml(row.description || '') + '</div></td>' +
                '<td>' + (cell.score * 100).toFixed(0) + '%</td>' +
                '<td><button class="link-btn" data-modal-case="' + escapeHtml(row.testCaseId) + '" data-modal-col="' + escapeHtml(col) + '">View \\u2192</button></td>' +
                '</tr>').join('');
              openModal('<h3>' + escapeHtml(col) + '</h3>' +
                '<p><span class="pill ' + (wantPass ? 'pass' : 'fail') + '">' + (wantPass ? 'PASSING' : 'FAILING') + '</span> \\u2014 ' +
                matches.length + ' of ' + total + ' cases</p>' +
                '<table class="assertion-table"><thead><tr><th>Test case</th><th>Score</th><th></th></tr></thead><tbody>' +
                (rowsHtml || '<tr><td colspan="3" class="muted">None</td></tr>') + '</tbody></table>');
            }

            function renderAssertionBreakdownModal(label, col, matrix) {
              const entry = matrix.cellMap.get(label + '\\u0001' + col);
              const entries = entry ? entry.entries : [];
              const avg = entry ? entry.sum / entry.count : 0;
              const rowsHtml = entries.map(e => '<tr>' +
                '<td>' + escapeHtml(e.caseId) + '</td>' +
                '<td><span class="pill ' + (e.pass ? 'pass' : 'fail') + '">' + (e.pass ? 'PASS' : 'FAIL') + '</span></td>' +
                '<td>' + e.score.toFixed(2) + '</td>' +
                '<td>' + escapeHtml(e.feedback || '') + '</td>' +
                '<td><button class="link-btn" data-modal-case="' + escapeHtml(e.testCaseId) + '" data-modal-col="' + escapeHtml(col) + '">View \\u2192</button></td>' +
                '</tr>').join('');
              openModal('<h3>' + escapeHtml(label) + ' \\u00b7 ' + escapeHtml(col) + '</h3>' +
                '<p class="muted">Average score ' + avg.toFixed(2) + ' across ' + entries.length + ' case(s)</p>' +
                '<table class="assertion-table"><thead><tr><th>Test case</th><th>Result</th><th>Score</th><th>Feedback</th><th></th></tr></thead><tbody>' +
                (rowsHtml || '<tr><td colspan="5" class="muted">None</td></tr>') + '</tbody></table>');
            }

            const model = buildModel();
            document.querySelector('#meta').innerHTML = model.meta;
            renderSummary(model);
            if (model.rows.length === 0) {
              document.querySelector('#summary').insertAdjacentHTML('afterend',
                '<div class="card"><div class="card-title">No results were recorded for this run</div>' +
                '<div class="card-subtitle">If this was an evaluation, check the test output: the dataset may be empty, ' +
                'or the class failed before any test case ran.</div></div>');
            }
            renderPassFailChart(model);
            renderAssertionChart(model);
            renderHeatmap(model);
            renderStatsTable(model);
            renderTable(model);

            document.querySelector('#rows').addEventListener('click', event => {
              const target = event.target.closest('[data-action]');
              if (!target) return;
              const row = model.rows.find(r => r.testCaseId === target.dataset.case);
              if (!row) return;
              if (target.dataset.action === 'input') renderInputModal(row);
              if (target.dataset.action === 'eval') renderEvalModal(row, target.dataset.col);
            });
            document.querySelector('#modal-body').addEventListener('click', event => {
              const target = event.target.closest('[data-modal-case]');
              if (!target) return;
              const row = model.rows.find(r => r.testCaseId === target.dataset.modalCase);
              if (row) renderEvalModal(row, target.dataset.modalCol);
            });
            document.querySelector('#modal-close').addEventListener('click', closeModal);
            document.querySelector('#overlay').addEventListener('click', event => {
              if (event.target.id === 'overlay') closeModal();
            });
            document.addEventListener('keydown', event => {
              if (event.key === 'Escape') closeModal();
            });
            </script></body></html>
            """;
}
