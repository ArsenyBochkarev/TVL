#!/usr/bin/env python3
"""CEGAR driver for TVL: abstract -> translate -> verify -> validate -> refine.

Loop (each iteration permanently un-abstracts at least one node, so the loop
terminates in at most (#abstractable nodes + 1) iterations):

    0. concrete.tvir   = frontend dump of the source model (validation target)
       concrete.<ext>  = translation of the concrete model into the target
                         language (external validators run the ABSTRACT trace
                         against THIS model)
    1. abstract.tvir   = concrete.tvir transformed per abstraction.json
    2. abstract.<ext>  = translation of the abstract model
    3. verifier.py --trace-json ...  -> verdict + canonical tvl-trace/1
    4. validate the trace against the CONCRETE model:
         target=tla  -> utils/tlc_loadtrace.py   (native TLC -loadTrace, precise)
         target=spin -> utils/spin_monitor.py    (observer proctype, over-approx)
         fallback    -> curtis validate concrete.tvir trace.json (precise replay)
    5. spurious -> blacklist the culprit node, goto 1

Exit codes: 0 = VERIFIED (abstract model satisfies all specs; with sound
abstractions this implies the concrete model does too), 1 = REAL counterexample
(or an iteration/error budget exhausted), 2 = usage/tooling error.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys

REPO_ROOT = os.path.dirname(os.path.abspath(__file__))

SBT_OPTS = ("--enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED "
            "-XX:+IgnoreUnrecognizedVMOptions -XX:-PrintWarnings "
            "--sun-misc-unsafe-memory-access=allow")

SPIN_MONITOR = os.path.join(REPO_ROOT, "utils", "spin_monitor.py")
TLC_LOADTRACE = os.path.join(REPO_ROOT, "utils", "tlc_loadtrace.py")
VERIFIER = os.path.join(REPO_ROOT, "verifier.py")


def run(cmd, cwd=REPO_ROOT, env=None):
    e = dict(os.environ)
    if env:
        e.update(env)
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=cwd, env=e)


def sbt(*commands):
    """Run sbt runMain commands in one JVM session (mirrors ./translate)."""
    args = " ".join(f'"runMain main {c}"' for c in commands)
    r = run(f'sbt -error {args}', env={"SBT_OPTS": SBT_OPTS})
    if r.returncode != 0:
        raise RuntimeError(f"sbt failed:\n{r.stdout[-2000:]}\n{r.stderr[-2000:]}")
    return r.stdout


def tvl_source_for(model):
    """verifier.py maps traces back to .tvl lines; .tvir inputs use the sibling source."""
    if model.endswith(".tvir"):
        return model[:-5] + ".tvl"
    return model


def translate(model, output, target, cap):
    ext = {"spin": "pml", "tla": "tla", "ir": "tvir"}[target]
    assert output.endswith("." + ext), f"output {output} must have .{ext} extension for target {target}"
    sbt(f"{model} {output} {target} {cap} -")


def verify(target, model_file, source_file, map_file, trace_size, trace_json, cap):
    cmd = (f"python3 {VERIFIER} {target} {model_file} {source_file} {map_file} {trace_size} "
           f"--trace-json {trace_json} --channel-size {cap}")
    r = run(cmd)
    print(r.stdout, end="")
    if r.returncode != 0:
        print(r.stderr, end="", file=sys.stderr)
    return os.path.exists(trace_json)


def parse_validate_line(output):
    m = re.search(r"^validate: (\S+)(?: step=(\S+) actor=(\S+) node=(\S+))?\s*$",
                  output, re.M)
    if not m:
        return None
    verdict = m.group(1)
    step = int(m.group(2)) if m.group(2) and m.group(2) != "?" else None
    actor = m.group(3) if m.group(3) != "?" else None
    node = int(m.group(4)) if m.group(4) and re.fullmatch(r"-?\d+", m.group(4)) else None
    return {"verdict": verdict, "step": step, "actor": actor, "node": node}


def validate_trace(validator, target, trace, concrete_model, concrete_tvir, cap):
    """Returns the parsed `validate:` line (dict) or None on failure."""
    cmd = None
    if validator in ("auto", "tlc-loadtrace") and target == "tla":
        cmd = f"python3 {TLC_LOADTRACE} {trace} {concrete_model}"
    elif validator in ("auto", "spin-monitor") and target == "spin":
        cmd = f"python3 {SPIN_MONITOR} {trace} {concrete_model}"
    # explicit or fallback
    if cmd is None and validator in ("auto", "curtis"):
        if shutil.which("curtis"):
            cmd = f"curtis validate {concrete_tvir} {trace} --channel-size {cap}"
        else:
            print("warning: curtis not on PATH; no validator ran", file=sys.stderr)
            return None
    if cmd is None:
        print(f"warning: no validator available for target={target}, validator={validator}",
              file=sys.stderr)
        return None
    r = run(cmd)
    print(r.stdout, end="")
    if r.stderr:
        print(r.stderr, end="", file=sys.stderr)
    parsed = parse_validate_line(r.stdout)
    if parsed is None and r.returncode != 2:
        print(f"warning: validator produced no verdict line (exit {r.returncode})", file=sys.stderr)
    return parsed


def main():
    ap = argparse.ArgumentParser(description="CEGAR driver for TVL")
    ap.add_argument("--model", required=True, help="source .tvl (or .tvir) model")
    ap.add_argument("--target", required=True, choices=["spin", "tla"])
    ap.add_argument("--workdir", default=None, help="artifact directory (default: cegar-out)")
    ap.add_argument("--channel-size", type=int, default=20)
    ap.add_argument("--trace-size", type=int, default=20)
    ap.add_argument("--iterations", type=int, default=20)
    ap.add_argument("--validator", default="auto",
                    choices=["auto", "tlc-loadtrace", "spin-monitor", "curtis", "none"])
    args = ap.parse_args()

    model = os.path.abspath(args.model)
    if not os.path.exists(model):
        print(f"error: model not found: {model}", file=sys.stderr)
        return 2
    workdir = os.path.abspath(args.workdir or
                              os.path.join(os.path.dirname(model), "cegar-out"))
    os.makedirs(workdir, exist_ok=True)

    ext = "pml" if args.target == "spin" else "tla"

    # ---- Step 0: concrete IR + concrete target model -----------------------
    concrete_tvir = os.path.join(workdir, "concrete.tvir")
    concrete_model = os.path.join(workdir, f"concrete.{ext}")
    print(f"[cegar] translating the concrete model ({args.target})")
    translate(model, concrete_tvir, "ir", args.channel_size)
    translate(concrete_tvir, concrete_model, args.target, args.channel_size)

    concrete_map = os.path.join(workdir, "concrete.map.json")
    source_file = tvl_source_for(model)

    for it in range(1, args.iterations + 1):
        itdir = os.path.join(workdir, f"iter_{it}")
        os.makedirs(itdir, exist_ok=True)
        # (abstraction lands here in M3; for now abstract == concrete)
        abstract_tvir = os.path.join(itdir, "abstract.tvir")
        shutil.copy2(concrete_tvir, abstract_tvir)
        abstract_model = os.path.join(itdir, f"abstract.{ext}")
        translate(abstract_tvir, abstract_model, args.target, args.channel_size)

        print(f"\n[cegar] iteration {it}: verifying the abstract model")
        trace_json = os.path.join(itdir, "trace.json")
        if os.path.exists(trace_json):
            os.remove(trace_json)
        abstract_map = os.path.join(itdir, "abstract.map.json")
        violated = verify(args.target, abstract_model, source_file, abstract_map,
                          args.trace_size, trace_json, args.channel_size)
        if not violated:
            print(f"\n[cegar] VERIFIED: all specs hold on the abstract model "
                  f"(iteration {it})")
            return 0

        print(f"[cegar] counterexample found, validating against the concrete model")
        verdict = (validate_trace(args.validator, args.target, trace_json,
                                  concrete_model, concrete_tvir, args.channel_size)
                   if args.validator != "none" else None)
        if verdict is None:
            print("[cegar] UNKNOWN: could not validate the counterexample")
            return 1
        if verdict["verdict"] == "feasible":
            print(f"\n[cegar] REAL counterexample (see {itdir}/trace.json)")
            return 1
        # spurious: refine at the culprit node (M3 wires this into abstraction.json)
        print(f"[cegar] SPURIOUS at step {verdict.get('step')} "
              f"(actor={verdict.get('actor')}, node={verdict.get('node')}) — refining")
        return 1  # placeholder until the abstractor exists (M3)

    print(f"\n[cegar] UNKNOWN: iteration budget ({args.iterations}) exhausted")
    return 1


if __name__ == "__main__":
    sys.exit(main())
