#!/usr/bin/env python3
"""CEGAR driver for TVL: abstract -> translate -> verify -> validate -> refine.

Loop (each iteration permanently blacklists at least one node, so the loop
terminates in at most (#abstractable nodes + 1) iterations):

    0. concrete.tvir   = frontend dump of the source model (validation target)
       concrete.<ext>  = translation of the concrete model into the target
                         language (external validators run the ABSTRACT trace
                         against THIS model)
    1. abstract.tvir   = concrete.tvir transformed per abstraction.json
                         (auto = ["loop-unroll", "branch-hoist"], minus blacklist)
    2. abstract.<ext>  = translation of the abstract model
    3. verifier.py --trace-json ...  -> verdict + canonical tvl-trace/1
    4. validate the trace against the CONCRETE model:
         target=tla  -> utils/tlc_loadtrace.py   (native TLC -loadTrace, precise)
         target=spin -> utils/spin_monitor.py    (observer proctype, over-approx)
         fallback    -> curtis validate concrete.tvir trace.json (precise replay)
    5. spurious -> blacklist the culprit node, goto 1

Exit codes: 0 = VERIFIED (abstract model satisfies all specs; with sound
abstractions this implies the concrete model does too), 1 = REAL counterexample
or an iteration budget exhausted, 2 = usage/tooling error.
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

ABSTRACTION_KINDS = ["loop-unroll", "branch-hoist"]


def run(cmd, cwd=REPO_ROOT, env=None):
    e = dict(os.environ)
    if env:
        e.update(env)
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=cwd, env=e)


def sbt_runmain(*commands):
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


def translate(model, output, target, cap, abstraction=None):
    ext = {"spin": "pml", "tla": "tla", "ir": "tvir"}[target]
    assert output.endswith("." + ext), f"output {output} must have .{ext} extension for target {target}"
    cmd = f"{model} {output} {target} {cap} - {abstraction or '-'}"
    sbt_runmain(cmd)


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
    step = int(m.group(2)) if m.group(2) and re.fullmatch(r"\d+", m.group(2)) else None
    actor = m.group(3) if m.group(3) != "?" else None
    node = int(m.group(4)) if m.group(4) and re.fullmatch(r"-?\d+", m.group(4)) else None
    return {"verdict": verdict, "step": step, "actor": actor, "node": node}


def run_validator(cmd):
    r = run(cmd)
    print(r.stdout, end="")
    if r.stderr:
        print(r.stderr, end="", file=sys.stderr)
    return parse_validate_line(r.stdout)


def validate_trace(validator, target, trace, concrete_model, concrete_tvir, cap):
    """Returns the parsed `validate:` line (dict) or None on failure."""
    cmd = None
    primary = validator
    if validator in ("auto", "tlc-loadtrace") and target == "tla":
        cmd = f"python3 {TLC_LOADTRACE} {trace} {concrete_model}"
        primary = "tlc-loadtrace"
    elif validator in ("auto", "spin-monitor") and target == "spin":
        cmd = f"python3 {SPIN_MONITOR} {trace} {concrete_model}"
        primary = "spin-monitor"
    # explicit choice or fallback
    if cmd is None and validator in ("auto", "curtis"):
        if shutil.which("curtis"):
            cmd = f"curtis validate {concrete_tvir} {trace} --channel-size {cap}"
            primary = "curtis"
        else:
            print("warning: curtis not on PATH; no validator ran", file=sys.stderr)
            return None
    if cmd is None:
        print(f"warning: no validator available for target={target}, validator={validator}",
              file=sys.stderr)
        return None

    parsed = run_validator(cmd)
    if parsed is None:
        print("warning: validator produced no verdict line", file=sys.stderr)
        return None

    # The spin monitor cannot check lasso loop closure (its feasible only means
    # the finite part reproduced); confirm with the precise validator.
    if (parsed["verdict"] == "feasible" and primary == "spin-monitor"
            and load_json(trace).get("kind") == "lasso"):
        if shutil.which("curtis"):
            print("[cegar] lasso: confirming loop closure with curtis validate")
            confirmed = run_validator(f"curtis validate {concrete_tvir} {trace} --channel-size {cap}")
            if confirmed is not None:
                return confirmed
        print("warning: lasso loop closure NOT confirmed (no precise validator); "
              "treating the verdict as provisional", file=sys.stderr)
    return parsed


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def culprit_to_concrete_node(verdict, abs_report):
    """Map the trace culprit node to a CONCRETE node id for blacklisting.

    Node ids are shared between abstract and concrete IR except for nodes the
    abstraction pass inserted (fresh ids); those are reported per decision in
    the .abs.json, so an inserted culprit refines the decision that created it.
    """
    node, actor = verdict.get("node"), verdict.get("actor")
    if node is None:
        return None, None
    for d in abs_report.get("applied", []):
        if d.get("actor") == actor and node in d.get("inserted", []):
            return d["actor"], d["node"]
    return actor, node


def main():
    ap = argparse.ArgumentParser(description="CEGAR driver for TVL")
    ap.add_argument("--model", required=True, help="source .tvl (or .tvir) model")
    ap.add_argument("--target", required=True, choices=["spin", "tla"])
    ap.add_argument("--workdir", default=None, help="artifact directory (default: <model dir>/cegar-out)")
    ap.add_argument("--channel-size", type=int, default=20)
    ap.add_argument("--trace-size", type=int, default=20)
    ap.add_argument("--iterations", type=int, default=20)
    ap.add_argument("--kinds", default=",".join(ABSTRACTION_KINDS),
                    help="comma-separated auto abstraction kinds")
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
    source_file = tvl_source_for(model)

    # ---- Step 0: concrete IR + concrete target model -----------------------
    concrete_tvir = os.path.join(workdir, "concrete.tvir")
    concrete_model = os.path.join(workdir, f"concrete.{ext}")
    print(f"[cegar] translating the concrete model ({args.target})")
    translate(model, concrete_tvir, "ir", args.channel_size)
    translate(concrete_tvir, concrete_model, args.target, args.channel_size)

    # ---- bootstrap the abstraction sidecar ---------------------------------
    abstraction_json = os.path.join(workdir, "abstraction.json")
    spec = {
        "format": "tvl-abstraction/1",
        "decisions": [],
        "auto": [k.strip() for k in args.kinds.split(",") if k.strip()],
        "blacklist": [],
        "disable_specs": [],
    }
    with open(abstraction_json, "w", encoding="utf-8") as f:
        json.dump(spec, f, indent=2)

    history = []
    for it in range(1, args.iterations + 1):
        itdir = os.path.join(workdir, f"iter_{it}")
        os.makedirs(itdir, exist_ok=True)

        abstract_tvir = os.path.join(itdir, "abstract.tvir")
        translate(concrete_tvir, abstract_tvir, "ir", args.channel_size, abstraction=abstraction_json)
        abs_report_path = f"{abstract_tvir}.abs.json"
        abs_report = load_json(abs_report_path) if os.path.exists(abs_report_path) else {"applied": []}

        abstract_model = os.path.join(itdir, f"abstract.{ext}")
        translate(abstract_tvir, abstract_model, args.target, args.channel_size)

        print(f"\n[cegar] iteration {it}: {len(abs_report.get('applied', []))} abstraction(s) "
              f"applied, verifying the abstract model")
        trace_json = os.path.join(itdir, "trace.json")
        if os.path.exists(trace_json):
            os.remove(trace_json)
        abstract_map = os.path.join(itdir, "abstract.map.json")
        violated = verify(args.target, abstract_model, source_file, abstract_map,
                          args.trace_size, trace_json, args.channel_size)
        if not violated:
            print(f"\n[cegar] VERIFIED: all specs hold on the abstract model (iteration {it})")
            history.append({"iteration": it, "verdict": "verified"})
            report = {"verdict": "VERIFIED", "iterations": history}
            with open(os.path.join(workdir, "report.json"), "w", encoding="utf-8") as f:
                json.dump(report, f, indent=2)
            return 0

        print(f"[cegar] counterexample found, validating against the concrete model")
        verdict = (validate_trace(args.validator, args.target, trace_json,
                                  concrete_model, concrete_tvir, args.channel_size)
                   if args.validator != "none" else None)
        if verdict is None:
            print("[cegar] UNKNOWN: could not validate the counterexample")
            history.append({"iteration": it, "verdict": "unknown"})
            return 1
        if verdict["verdict"] == "feasible":
            print(f"\n[cegar] REAL counterexample (see {itdir}/trace.json)")
            history.append({"iteration": it, "verdict": "real"})
            with open(os.path.join(workdir, "report.json"), "w", encoding="utf-8") as f:
                json.dump({"verdict": "REAL", "iterations": history}, f, indent=2)
            return 1

        # spurious: refine at the culprit node
        actor, node = culprit_to_concrete_node(verdict, abs_report)
        reason = f"spurious at iter {it}, step {verdict.get('step')}"
        history.append({"iteration": it, "verdict": "spurious",
                        "step": verdict.get("step"), "actor": actor, "node": node})
        if node is None:
            print(f"[cegar] UNKNOWN: spurious counterexample without a culprit node "
                  f"— cannot refine")
            return 1
        already = any(e.get("actor") == actor and e.get("node") == node
                      for e in spec["blacklist"])
        spec["blacklist"].append({"actor": actor, "node": node, "reason": reason})
        with open(abstraction_json, "w", encoding="utf-8") as f:
            json.dump(spec, f, indent=2)
        if already:
            # the culprit heuristic pointed at an already-refined node: no
            # progress is possible this way, stop instead of looping
            print(f"[cegar] UNKNOWN: culprit ({actor}, {node}) is already blacklisted "
                  f"(validator heuristic imprecision)")
            return 1
        print(f"[cegar] SPURIOUS at step {verdict.get('step')} "
              f"(actor={actor}, node={node}) — refined, retrying")

    print(f"\n[cegar] UNKNOWN: iteration budget ({args.iterations}) exhausted")
    with open(os.path.join(workdir, "report.json"), "w", encoding="utf-8") as f:
        json.dump({"verdict": "UNKNOWN", "iterations": history}, f, indent=2)
    return 1


if __name__ == "__main__":
    sys.exit(main())
