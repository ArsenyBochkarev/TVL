#!/usr/bin/env python3
"""Validate a canonical tvl-trace/1 counterexample against a concrete TLA+
model, using TLC's native `-loadTrace` (the trace is loaded as a constraint
on state-space exploration).

Inputs:
  <trace.json>   canonical tvl-trace/1 file written by verifier.py
                 (used for step naming / culprit reporting)
  <model.tla>    the CONCRETE PlusCal model (pcal is run on it in place,
                 exactly like verifier.py does); the sibling .cfg is used
  --raw <file>   the raw TLC `-dumpTrace json` counterexample that produced
                 the canonical trace (default: <trace.json>.tlc.json). It is
                 the file actually passed to `-loadTrace`, because it carries
                 the full state values (pc, channels, flags) of the abstract
                 counterexample.

Interpretation (precise — states are matched by full values, and every
explored transition is a genuine transition of the concrete model):
  violation found              -> validate: feasible  (real counterexample)
  completed, no error          -> validate: spurious  (the constrained
                                  exploration died out before reproducing
                                  the trace)

Culprit step (for refinement): TLC's reported search depth ~= the number of
reproduced states, so the first unreproduced step is ~depth (heuristic; the
driver can fall back to the precise Curtis validator).

Exit codes: 0 feasible, 1 spurious, 2 error.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys

TLC_CMD = os.environ.get("TLC_CMD", "tlc")
PCAL_CMD = os.environ.get("PCAL_CMD", "pcal")


def run(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True)


def load_trace(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def find_depth(output):
    m = re.search(r"The depth of the complete state graph search is (\d+)", output)
    return int(m.group(1)) if m else None


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
    ap.add_argument("model", help="concrete .tla model (PlusCal; expanded in place)")
    ap.add_argument("--raw", default=None,
                    help="raw TLC -dumpTrace json counterexample (default: <trace>.tlc.json)")
    ap.add_argument("--no-pcal", action="store_true",
                    help="the model is already pcal-expanded")
    args = ap.parse_args()

    raw = args.raw or f"{args.trace}.tlc.json"
    if not os.path.exists(raw):
        print(f"error: raw TLC trace not found: {raw} "
              f"(run verifier.py with --trace-json)", file=sys.stderr)
        return 2
    if not shutil.which(TLC_CMD):
        print(f"error: {TLC_CMD} not found on PATH", file=sys.stderr)
        return 2

    trace = load_trace(args.trace)
    steps = trace.get("steps", [])

    if not args.no_pcal:
        r = run(f"{PCAL_CMD} {args.model}")
        if r.returncode != 0:
            print(f"error: pcal failed:\n{r.stdout}\n{r.stderr}", file=sys.stderr)
            return 2

    cfg = os.path.splitext(args.model)[0] + ".cfg"
    cmd = (f"{TLC_CMD} -loadTrace json {raw} -config {cfg} {args.model}"
           if os.path.exists(cfg)
           else f"{TLC_CMD} -loadTrace json {raw} {args.model}")
    r = run(cmd)
    output = r.stdout + r.stderr

    if ("Invariant" in output and "violated" in output) or \
       "Temporal properties" in output or \
       ("Error:" in output and "Action or state" not in output and "loadTrace" not in output):
        return report("feasible",
                      extra=f"TLC reproduced a violation under the trace constraint")

    if "Model checking completed" in output or "states generated" in output:
        depth = find_depth(output)
        culprit = None
        if depth is not None and steps:
            # depth states ~ depth-1 reproduced transitions; clamp to the trace
            k = max(1, min(depth, len(steps)))
            culprit = steps[k - 1]
        if culprit:
            return report("spurious", culprit["index"], culprit["actor"],
                          culprit.get("node"),
                          extra=f"constrained exploration died out (depth {depth})")
        return report("spurious", extra="constrained exploration died out")

    print(f"error: could not interpret TLC output (exit {r.returncode}):\n"
          f"{output[-2000:]}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
