#!/usr/bin/env python3
"""Validate a canonical tvl-trace/1 counterexample against a concrete Promela
model via a generated never-claim monitor.

The monitor is a snapshot sequence machine: for every trace step (actor, node)
it waits (self-looping once per system step) until the actor's pc is parked at
the node's L_<id> label — a concrete execution replaying the trace keeps every
actor parked at its trace node at the aligned step, so such an execution
always reaches the final `assert(0)`.

Precision notes (important):
  * pc positions are the only observable, so the monitor is an
    OVER-approximation: it also accepts executions with extra unobserved steps
    between the trace steps. Therefore:
        no match (errors: 0) -> the trace is definitively SPURIOUS
        match                -> possibly real: confirm with the precise
                                validator (`curtis validate`) before
                                declaring a real bug
  * For lasso traces only the finite part (prefix + one loop iteration) is
    checked; loop closure is not verified by this validator.
  * Steps with node == null (plumbing: INIT, L_END_*, flag sets) are skipped:
    they have no pc event.

Cost note: `pan` explores the full product of the concrete model and the
monitor, so validation costs about as much as a safety check of the concrete
model. The cheap and precise validator for the SPIN target is the Curtis
replay (fallback); this monitor is the in-tool cross-check.

Culprit extraction (for refinement): binary search over prefix monitors, each
check being one `pan` run (the monitor over steps[:k] asserts when the prefix
of length k completed).

Exit codes: 0 feasible(match), 1 spurious, 2 error.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

SPIN_CMD = os.environ.get("SPIN_CMD", "spin")


def run(cmd, cwd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=cwd)


def group_steps(steps):
    """Group runs of consecutive identical (actor, node) steps into one event.

    A trace step is statement-level while `A@L_n` is label-level: one IR node
    emits several statements under its label (op + bookkeeping), so executing
    one node can yield two consecutive identical steps. One arrival at L_n
    plus one departure covers exactly one execution of the node's block.
    """
    groups = []
    for s in steps:
        if s.get("node") is None:
            continue
        if groups and groups[-1][0]["actor"] == s["actor"] \
                and groups[-1][0].get("node") == s.get("node"):
            continue
        groups.append((s, f"{s['actor']}@L_{s['node']}"))
    return groups


def build_monitor(events):
    """Two-phase event-sequence OBSERVER proctype over grouped (actor, node)
    runs: W-phase waits until the actor's pc arrives at L_n, D-phase waits
    until it leaves (the node's block actually executed — an actor blocked and
    parked at L_n never passes D).

    This must be a normal `active proctype`, NOT a never claim: a claim is
    paced in lockstep with the system (one claim transition per system step),
    so its phases can consume/lag past transient pc windows and miss events. A
    regular process interleaves freely, and pan explores every interleaving —
    including the ones where the observer keeps pace — so a realizable event
    sequence is always matched.
    Returns None for an empty sequence.
    """
    if not events:
        return None
    lines = ["active proctype tvl_trace_validator() {"]
    for i, (_, cond) in enumerate(events):
        nxt = f"W_{i + 2}" if i + 1 < len(events) else "DONE"
        lines += [
            f"W_{i + 1}:",
            "\tif",
            f"\t:: ({cond}) -> goto D_{i + 1}",
            f"\t:: (!({cond})) -> goto W_{i + 1}",
            "\tfi;",
            f"D_{i + 1}:",
            "\tif",
            f"\t:: (!({cond})) -> goto {nxt}",
            f"\t:: ({cond}) -> goto D_{i + 1}",
            "\tfi;",
        ]
    lines += [
        "DONE:",
        "\tassert(0)",
        "}",
    ]
    return "\n".join(lines)


def strip_ltl(model_text):
    # LTL formulas do not contain nested braces
    return re.sub(r"ltl\s+\w*\s*\{[^{}]*\}", "", model_text)


def check(text, workdir, tag):
    """Compile+run pan on the model text; True iff the monitor matched."""
    d = os.path.join(workdir, tag)
    os.makedirs(d, exist_ok=True)
    model = os.path.join(d, "model.pml")
    with open(model, "w", encoding="utf-8") as f:
        f.write(text)
    r = run(f"{SPIN_CMD} -a model.pml", d)
    if r.returncode != 0:
        raise RuntimeError(f"spin -a failed:\n{r.stdout}\n{r.stderr}")
    r = run("gcc -O2 -DNOREDUCE pan.c -o pan.out -w", d)
    if r.returncode != 0:
        raise RuntimeError(f"gcc failed:\n{r.stderr}")
    r = run("./pan.out -m100000", d)
    return "errors: 0" not in r.stdout


def report(verdict, step=None, actor=None, node=None, extra=""):
    line = f"validate: {verdict}"
    if step is not None:
        line += f" step={step} actor={actor} node={node}"
    print(line)
    if extra:
        print(extra)
    return 0 if verdict == "feasible" else 1


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("trace", help="canonical tvl-trace/1 JSON from verifier.py")
    ap.add_argument("model", help="concrete .pml model")
    ap.add_argument("--no-culprit", action="store_true",
                    help="skip the prefix binary search on a spurious verdict")
    args = ap.parse_args()

    if not shutil.which(SPIN_CMD):
        print(f"error: {SPIN_CMD} not found on PATH", file=sys.stderr)
        return 2
    if not shutil.which("gcc"):
        print("error: gcc not found on PATH (needed to build pan)", file=sys.stderr)
        return 2

    with open(args.trace, "r", encoding="utf-8") as f:
        trace = json.load(f)
    with open(args.model, "r", encoding="utf-8") as f:
        model_text = strip_ltl(f.read())

    events = group_steps(trace.get("steps", []))
    workdir = tempfile.mkdtemp(prefix="tvl-spin-monitor-")
    try:
        if not events:
            return report("feasible",
                          extra="nothing to watch: the violation is at INIT")

        monitor = build_monitor(events)
        matched = check(model_text + "\n" + monitor, workdir, "full")
        if matched:
            note = ""
            if trace.get("kind") == "lasso":
                note = ("lasso: finite part (prefix + one loop iteration) reproduced; "
                        "loop closure not checked — confirm with curtis validate")
            return report("feasible", extra=note or "monitor matched (over-approximation: confirm with curtis validate)")

        # Spurious: find the first unreproduced event by binary search over prefixes
        if args.no_culprit or len(events) == 1:
            culprit = events[0][0]
            return report("spurious", culprit["index"], culprit["actor"], culprit.get("node"),
                          extra="monitor did not match")
        lo, hi = 0, len(events)  # prefix of length lo matches, hi does not
        while hi - lo > 1:
            mid = (lo + hi) // 2
            ok = check(model_text + "\n" + build_monitor(events[:mid]), workdir, f"pre{mid}")
            lo, hi = (mid, hi) if ok else (lo, mid)
        culprit = events[lo][0]
        return report("spurious", culprit["index"], culprit["actor"], culprit.get("node"),
                      extra=f"longest reproduced prefix: {lo} events "
                            f"(heuristic under the over-approximation)")
    except RuntimeError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
