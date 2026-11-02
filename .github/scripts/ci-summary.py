"""Condenses each CI job's outcome into a digest, and every digest into one run summary.

Each job runs `collect` (from the repository root, after Gradle) to boil its test results (JUnit XML reports of Kotlin
and Python tests) down to a small JSON digest, uploaded as an artifact. Then it runs `report` over every digest uploaded
so far, plus the run's live job list, and `publish`es the result to the run's "CI summary" check run, so that the
summary appears with the first finished job and fills in as the others finish. The final `summary` job renders the
complete version into the run summary page, emits annotations, and completes the check run.

    python3 ci-summary.py collect --out digest.json --kind build|matrix [--dim k=v ...] [--outcome success]
    python3 ci-summary.py report --digests DIR [--jobs jobs.jsonl] --summary OUT [--title-out FILE] [--annotate]
    python3 ci-summary.py publish --check-run ID --summary FILE --title FILE [--conclusion success|failure]
"""

import argparse
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

MAX_FAILURES = 100  # per digest, so that a catastrophic run keeps its artifact (and the summary) small
MAX_ANNOTATIONS = 10  # GitHub shows at most 10 error annotations per step anyway
MAX_CHECK_RUN_TEXT = 65000  # check run outputs are limited to 65535 characters


# ---------------------------------------------------------------------------------------------------- collect


def module_dirs():
    """Directories holding a tracked Gradle build script, i.e. not stale build directories of removed modules."""
    scripts = subprocess.run(["git", "ls-files", "build.gradle.kts", "*/build.gradle.kts"], capture_output=True, text=True)
    return {Path(p).parent.as_posix() for p in scripts.stdout.split()}


def collect_tests():
    """Test counts per (module, task), and the first failures, from JUnit XML reports (Gradle's and pytest's)."""
    modules = module_dirs()
    suites = defaultdict(lambda: {"tests": 0, "failures": 0, "skipped": 0})
    failed = []
    for report in sorted(Path(".").glob("**/build/test-results/*/**/*.xml")):
        if "node_modules" in report.parts:
            continue
        parts = report.as_posix().split("/build/test-results/")
        module, task = parts[0], parts[1].split("/")[0]
        if module not in modules:
            continue
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            suite = suites[(module, task)]
            suite["tests"] += 1
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                suite["failures"] += 1
                if len(failed) < MAX_FAILURES:
                    message = (problem.get("message") or problem.text or "").strip().splitlines()
                    failed.append(
                        {
                            "module": module,
                            "task": task,
                            "class": case.get("classname", ""),
                            "name": case.get("name", ""),
                            "message": message[0][:300] if message else "",
                        }
                    )
            elif case.find("skipped") is not None:
                suite["skipped"] += 1
    return [{"module": m, "task": t, **counts} for (m, t), counts in sorted(suites.items())], failed


def collect(args):
    suites, failed = collect_tests()
    digest = {
        "kind": args.kind,
        "dims": dict(d.split("=", 1) for d in args.dim),
        "outcome": args.outcome,
        "suites": suites,
        "failed": failed,
    }
    Path(args.out).write_text(json.dumps(digest))


# ----------------------------------------------------------------------------------------------------- report


JOB_ICONS = {"success": "✅", "failure": "❌", "cancelled": "🚫", "skipped": "➖", "timed_out": "⌛"}


def job_icon(job):
    if job is None:
        return "➖"
    if job["status"] != "completed":
        return "⏳"
    return JOB_ICONS.get(job["conclusion"], "⚠️")


def totals(suites):
    return {k: sum(s[k] for s in suites) for k in ("tests", "failures", "skipped")}


def status_icon(digest):
    if digest["outcome"] == "skipped":
        return "⏭️"  # inputs unchanged since the last green run: tests were not re-run
    failures = totals(digest["suites"])["failures"]
    if failures:
        return f"❌ {failures}"
    if digest["outcome"] not in ("success", ""):
        return "⚠️"  # the job failed without any failing test: compilation, setup, infrastructure
    return "✅"


def is_bad(digest):
    return status_icon(digest).startswith(("❌", "⚠️"))


def escape(text):
    return text.replace("|", "\\|")


def failure_rows(failed):
    rows = ["| Module | Task | Test | Message |", "|---|---|---|---|"]
    for f in failed:
        test = escape(f"{f['class'].rsplit('.', 1)[-1]} › {f['name']}")
        rows.append(f"| `{f['module']}` | `{f['task']}` | {test} | {escape(f['message'])} |")
    return rows


def pending(title, job):
    if job is not None and job["status"] != "completed":
        return [f"## {title}", "", "⏳ Still running.", ""]
    if job is not None and job["conclusion"] in ("skipped", "cancelled"):
        return [f"## {title}", "", f"{job_icon(job)} Not run ({job['conclusion']}).", ""]
    return [f"## {title}", "", "No digest: the job didn't get as far as writing one.", ""]


def section_tests(build, build_job):
    title = "Tests (build)"
    if build is None:
        return pending(title, build_job)
    suites, failed = build["suites"], build["failed"]
    if not suites:
        return [f"## {title}", "", "No results (the job didn't get as far as running the tests).", ""]
    t = totals(suites)
    icon = "❌" if t["failures"] else "✅"
    out = [f"## {title}", "", f"{icon} **{t['tests']}** tests, **{t['failures']}** failed, {t['skipped']} skipped", ""]
    if failed:
        out += failure_rows(failed)
        if t["failures"] > len(failed):
            out.append(f"| … | | only the first {MAX_FAILURES} failures are listed | |")
        out.append("")
    out += ["<details><summary>Per module</summary>", "", "| Module | Task | Tests | Failed | Skipped |", "|---|---|--:|--:|--:|"]
    for s in suites:
        out.append(f"| `{s['module']}` | `{s['task']}` | {s['tests']} | {s['failures']} | {s['skipped']} |")
    return out + ["", "</details>", ""]


RUNTIMES = {"jvm": "JDK", "js": "Node", "python": "Python"}


def matrix_column(dims):
    return f"{RUNTIMES[dims['runtime']]} {dims['version']} {dims.get('dist', '')}".strip()


MATRIX_JOB = re.compile(r"test-(jvm|js|python) \(([^)]*)\)$")


def matrix_jobs(jobs):
    """Test-extensively jobs of the live job list, keyed by (os, column) like the digests."""
    found = {}
    for job in jobs:
        match = MATRIX_JOB.search(job["name"])
        if match:
            values = [v.strip() for v in match.group(2).split(",")]
            dims = {"runtime": match.group(1), "os": values[0], "version": values[1]}
            if len(values) > 2:
                dims["dist"] = values[2]
            found[(dims["os"], matrix_column(dims))] = job
    return found


def column_order(column):
    runtime, version = column.split(" ", 2)[:2]
    numbers = tuple(int(n) for n in re.findall(r"\d+", version))
    return list(RUNTIMES.values()).index(runtime), numbers, column


def section_matrix(matrix, jobs):
    out = ["## Test extensively", ""]
    live = matrix_jobs(jobs)
    if not matrix and not live:
        return out + ["Not run (it only starts once `build` succeeds).", ""]
    cell = {(d["dims"]["os"], matrix_column(d["dims"])): d for d in matrix}
    keys = set(cell) | set(live)
    oses = sorted({os_name for os_name, _ in keys})
    columns = sorted({column for _, column in keys}, key=column_order)
    out += ["| OS | " + " | ".join(columns) + " |", "|---|" + "---|" * len(columns)]
    for os_name in oses:
        row = []
        for column in columns:
            if (os_name, column) in cell:
                row.append(status_icon(cell[(os_name, column)]))
            else:
                row.append(job_icon(live.get((os_name, column))) if (os_name, column) in live else " ")
        out.append(f"| **{os_name}** | " + " | ".join(row) + " |")
    out += [
        "",
        "✅ passed · ❌ n failing tests · ⚠️ job failed outside tests · ⏭️ skipped, inputs unchanged since a green run · "
        "⏳ running · ➖ not run · 🚫 cancelled",
        "",
    ]

    # Projections: for each dimension value, how many of its jobs went wrong. A value whose jobs ALL fail, while
    # others pass, points at that OS / runtime / version.
    projections = {
        "OS": lambda d: d["dims"]["os"],
        "Runtime": lambda d: RUNTIMES[d["dims"]["runtime"]],
        "Version": lambda d: f"{RUNTIMES[d['dims']['runtime']]} {d['dims']['version']}",
        "JDK distribution": lambda d: d["dims"].get("dist"),
    }
    bad_jobs = [d for d in matrix if is_bad(d)]
    if not bad_jobs:
        running = sum(1 for key, job in live.items() if key not in cell and job["status"] != "completed")
        done = f"All {len(matrix)} finished jobs passed (or were skipped as unchanged since a green run)"
        return out + [done + (f"; {running} still running." if running else "."), ""]
    out += ["| Dimension | Value | Jobs with problems |", "|---|---|---|"]
    suspects = []
    for dimension, key in projections.items():
        groups = defaultdict(list)
        for d in matrix:
            if key(d):
                groups[key(d)].append(d)
        for value, group in sorted(groups.items()):
            bad = sum(is_bad(j) for j in group)
            out.append(f"| {dimension} | {value} | {'🔴' if bad == len(group) else '🟠' if bad else '🟢'} {bad}/{len(group)} |")
            if bad and all(key(j) == value for j in bad_jobs) and len(groups) > 1:
                suspects.append(f"{dimension.lower()} **{value}**")
    out.append("")
    if suspects:
        out += ["> [!WARNING]", "> Every job with problems shares " + " and ".join(suspects) + ".", ""]

    by_test = defaultdict(list)
    for d in matrix:
        for f in d["failed"]:
            by_test[(f["module"], f["class"].rsplit(".", 1)[-1], f["name"])].append(
                f"{d['dims']['os']} / {matrix_column(d['dims'])}"
            )
    if by_test:
        out += ["| Module | Test | Failed on |", "|---|---|---|"]
        for (module, cls, name), where in sorted(by_test.items()):
            out.append(f"| `{module}` | {escape(cls)} › {escape(name)} | {', '.join(sorted(where))} |")
        out.append("")
    return out


def find_job(jobs, suffix):
    return next((j for j in jobs if j["name"] == suffix or j["name"].endswith(" / " + suffix)), None)


def report(args):
    digests = [json.loads(p.read_text()) for p in sorted(Path(args.digests).glob("**/*.json"))] if Path(args.digests).exists() else []
    # one JSON object ({name, status, conclusion}) per line, as `gh api --paginate --jq '.jobs[]|...'` prints them
    jobs_file = Path(args.jobs) if args.jobs else None
    jobs = [json.loads(line) for line in jobs_file.read_text().splitlines() if line.strip()] if jobs_file and jobs_file.exists() else []
    by_kind = defaultdict(list)
    for digest in digests:
        by_kind[digest["kind"]].append(digest)
    build = next(iter(by_kind["build"]), None)
    matrix = by_kind["matrix"]
    build_job = find_job(jobs, "ci / build")

    # the summary job itself (and the run-wide `success` gate) aren't part of the progress
    counted = [j for j in jobs if not j["name"].endswith(("summary", "success"))]
    finished = [j for j in counted if j["status"] == "completed"]
    problems = [j for j in finished if j["conclusion"] in ("failure", "timed_out", "cancelled")]
    if counted:
        state = "running" if len(finished) < len(counted) else ("failed" if problems else "passed")
        headline = f"{len(finished)}/{len(counted)} jobs finished" + (f", {len(problems)} with problems" if problems else "")
    else:
        state, headline = ("failed" if any(is_bad(d) for d in digests) else "passed"), f"{len(digests)} jobs reported"
    icon = {"running": "⏳", "failed": "❌", "passed": "✅"}[state]

    out = ["# CI summary", "", f"{icon} {headline}", ""]
    if jobs:
        if build is not None and build_job is not None and build_job["status"] != "completed":
            # the job publishing this very update is still "in progress", though its digest is final
            cells = [f"{'✅' if build['outcome'] in ('success', '') else '❌'} Build"]
        else:
            cells = [f"{job_icon(build_job)} Build"]
        live = matrix_jobs(jobs).values()
        if live:
            done = sum(1 for j in live if j["status"] == "completed")
            failing = any(j["conclusion"] == "failure" for j in live)
            cells.append(f"{'⏳' if done < len(live) else '❌' if failing else '✅'} Test extensively {done}/{len(live)}")
        out += [" · ".join(cells), ""]

    out += section_tests(build, build_job)
    out += section_matrix(matrix, jobs)
    Path(args.summary).open("a").write("\n".join(out) + "\n")
    if args.title_out:
        Path(args.title_out).write_text(f"{icon} {headline}")

    if not args.annotate:
        return
    annotations = 0
    for d in [build, *matrix]:
        for f in (d or {}).get("failed", []):
            if annotations == MAX_ANNOTATIONS:
                break
            where = f" ({d['dims']['os']} / {matrix_column(d['dims'])})" if d["kind"] == "matrix" else ""
            print(f"::error title=Test failed{where}: {f['module']} › {f['name']}::{f['message']}")
            annotations += 1


# ---------------------------------------------------------------------------------------------------- publish


def publish(args):
    """Updates the "CI summary" check run with the rendered summary (completing it if a conclusion is given)."""
    text = Path(args.summary).read_text()
    if len(text) > MAX_CHECK_RUN_TEXT:
        text = text[:MAX_CHECK_RUN_TEXT] + "\n\n… truncated: see the run summary page for the rest."
    payload = {"output": {"title": Path(args.title).read_text().strip()[:200], "summary": text}}
    if args.conclusion:
        payload.update(status="completed", conclusion=args.conclusion)
    repository = os.environ["GITHUB_REPOSITORY"]
    subprocess.run(
        ["gh", "api", "--method", "PATCH", f"repos/{repository}/check-runs/{args.check_run}", "--input", "-"],
        input=json.dumps(payload),
        text=True,
        check=True,
        stdout=subprocess.DEVNULL,
    )


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    c = commands.add_parser("collect")
    c.add_argument("--out", required=True)
    c.add_argument("--kind", choices=("build", "matrix"), required=True)
    c.add_argument("--dim", action="append", default=[])
    c.add_argument("--outcome", default="")
    r = commands.add_parser("report")
    r.add_argument("--digests", required=True)
    r.add_argument("--jobs")
    r.add_argument("--summary", required=True)
    r.add_argument("--title-out")
    r.add_argument("--annotate", action="store_true")
    p = commands.add_parser("publish")
    p.add_argument("--check-run", required=True)
    p.add_argument("--summary", required=True)
    p.add_argument("--title", required=True)
    p.add_argument("--conclusion", choices=("success", "failure", "neutral", "cancelled"))
    args = parser.parse_args()
    {"collect": collect, "report": report, "publish": publish}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
