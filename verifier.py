import shutil
import sys
import subprocess
import re
import os
import json

MAX_TRACE_STEPS = 20

# Optional flags (parsed from the command line in __main__):
#   --trace-json <file>  dump the counterexample in the canonical `tvl-trace/1` format
#   --no-truncate        do not cut console trace rendering at MAX_TRACE_STEPS
#   --channel-size <n>   channel capacity to stamp into the trace (source of truth: translate)
#   --fairness <mode>    fairness for verification: weak (default) | strong | none.
#                        tla: already baked into the generated model (fair/fair+);
#                        spin: the pan -f flag (weak) or no flag (none; strong is
#                        rejected - SPIN has no strong fairness);
#                        curtis: --weak-fairness / --strong-fairness / no flag
TRACE_JSON_FILE = None
NO_TRUNCATE = False
CHANNEL_SIZE = None
FAIRNESS = "weak"

PCAL_CMD = f"pcal"
TLC_CMD = f"tlc"
SPIN_CMD = "spin"
CURTIS_CMD = "curtis"

IGNORED_PATTERNS = [
    r"^cur_msg_.*",
    r"recv_Q_*",
    r"send_Q_*",
    r".*_finished = 1",
    r"\(run \w+\(\)\)",
    r".*_finished$",
    r"^sched_block.*_branch.*"
]

def run_cmd(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True)

def max_trace_steps():
    return 10**9 if NO_TRUNCATE else MAX_TRACE_STEPS

def load_source_map(map_file_path):
    if os.path.exists(map_file_path):
        try:
            with open(map_file_path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception as e:
            print(f"Mapping error: {e}")
    return {}

def load_source_code(file_path):
    if not file_path or not os.path.exists(file_path):
        return None
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return f.readlines()
    except Exception as e:
        print(f"Source code load error: {e}")
        return None

def print_unified_step(step_num, actor, action, source_line=None, source_code=None, state_diff=""):
    print(f"[ STEP {step_num} ]")
    print(f"  Actor  : {actor}")

    action_info = action
    if source_line and source_code:
        line_idx = int(source_line) - 1
        if 0 <= line_idx < len(source_code):
            code_text = source_code[line_idx].strip()
            action_info = f"`{code_text}`, line {source_line} ({action} in generated code)"
    elif source_line:
        action_info = f"line {source_line} ({action} in generated code)"

    print(f"  Action : {action_info}")
    if state_diff:
        print(f"  State  : {state_diff}")
    print()

def is_ignored(name):
    return any(re.match(p, name) for p in IGNORED_PATTERNS)

# ---------------------------------------------------------------------------
# Canonical trace format `tvl-trace/1` (counterexample export for trace
# validation). A step is identified by (actor, executed IR node id); both
# backends label every IR instruction L_<id>, which is where the process
# sat before the step (pre-state pc).
# ---------------------------------------------------------------------------

def node_from_label(label):
    """L_<id> -> int; everything else (user labels, L_END_*, INIT) -> None."""
    if not label:
        return None
    m = re.fullmatch(r"L_(\d+)", label)
    return int(m.group(1)) if m else None

def write_trace_json(path, target, model_file, prop, kind, steps, loop_start_index, channel_size):
    doc = {
        "format": "tvl-trace/1",
        "source": {
            "target": target,
            "model": os.path.basename(model_file),
            "property": prop,
            "verdict": "violated",
        },
        "kind": kind,
        "channel_size": channel_size,
        "steps": [{"index": i + 1, "actor": s["actor"], "node": s.get("node"),
                   "next": s.get("next"), "action": s["action"]} for i, s in enumerate(steps)],
    }
    if loop_start_index is not None:
        doc["loop_start_index"] = loop_start_index
    with open(path, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2)
    print(f"Trace written: {path} ({len(steps)} steps, kind={kind})")

def parse_tla_state(vars_str):
    state = {}
    pattern = r"/\\ ([a-zA-Z0-9_]+) = (.*?)(?=\n/\\ |\Z)"
    for match in re.finditer(pattern, vars_str, re.DOTALL):
        state[match.group(1)] = match.group(2).strip()
    return state

def parse_tla_output(output, source_map, source_code):
    failed = any(x in output for x in ["Error:", "Invariant", "Property"])

    if failed:
        print("\n" + "="*50)
        print("VERIFICATION FAILED (TLA+)")

        err_match = re.search(r"Error: (.*)", output)
        reason = err_match.group(1) if err_match else "Violation found"
        print(f"Reason: {reason}")
        print("="*50 + "\n")

        state_blocks = re.split(r'\nState \d+: ', '\n' + output)
        state_blocks = state_blocks[1:] if len(state_blocks) > 1 else []

        prev_pc, prev_state, step_count = {}, {}, 1

        for block in state_blocks:
            lines = block.strip().split('\n')
            action_match = re.search(r"<(.*?)(?:\s+line|>$)", lines[0])
            action = action_match.group(1) if action_match else "INIT"
            if action == "Initial predicate": action = "INIT"

            actor_moved = "System"
            vars_str = "\n".join(lines[1:])
            current_state = parse_tla_state(vars_str)

            pc_raw = current_state.get("pc", "")
            current_pc = {}
            if pc_raw:
                pc_clean = re.sub(r'\s+', ' ', pc_raw).strip('()[] ')
                sep = '@@' if '@@' in pc_clean else ','
                map_sep = ':>' if ':>' in pc_clean else '|->'
                for mapping in pc_clean.split(sep):
                    if map_sep in mapping:
                        k, v = mapping.split(map_sep, 1)
                        current_pc[k.strip(' "')] = v.strip(' "')

                if prev_pc:
                    for k, v in current_pc.items():
                        if k in prev_pc and prev_pc[k] != v:
                            actor_moved = k; break

            changed_vars = {k: re.sub(r'\s+', ' ', v) for k, v in current_state.items()
                            if k != "pc" and not is_ignored(k) and (k not in prev_state or prev_state[k] != v)}

            if action != "INIT" and not changed_vars and (is_ignored(action) or not changed_vars):
                prev_pc, prev_state = current_pc, current_state
                continue

            if step_count > max_trace_steps():
                break

            source_line = source_map.get(action)
            print_unified_step(step_count, actor_moved, action, source_line, source_code,
                               ", ".join([f"{k}={v}" for k, v in changed_vars.items()]))

            prev_pc, prev_state, step_count = current_pc, current_state, step_count + 1

    elif "No error" in output or "states generated" in output:
        print("\nVERIFICATION SUCCESSFUL (TLA+)")

def tla_ce_steps(ce):
    """Convert a TLC counterexample (parsed -dumpTrace json) to canonical steps.

    Each action entry is [fromRef, actionInfo, toRef] where fromRef/toRef are
    [stateNumber, stateVars] and actionInfo carries the executed label in "name".
    The actor is the process whose pc changed; the node is the executed label
    (pre-state pc), which is exactly what the backends call L_<id>. The "next"
    field is the actor's pc AFTER the step (from the to-state): it disambiguates
    same-node transitions (loop-guard pass vs exit) during trace replay.
    Returns (steps, loop_start_index or None).
    """
    transitions = ce.get("action") or []
    steps, loop_start = [], None
    for tr in transitions:
        if not (isinstance(tr, list) and len(tr) == 3):
            continue
        from_ref, info, to_ref = tr
        name = (info or {}).get("name") or ""
        if name == "Init" or is_ignored(name):
            continue
        actor, nxt = "System", None
        try:
            pc_from = (from_ref[1] or {}).get("pc", {})
            pc_to = (to_ref[1] or {}).get("pc", {})
            for k, v in pc_to.items():
                if pc_from.get(k) != v:
                    actor = k
                    nxt = node_from_label(v)
                    break
        except Exception:
            pass
        steps.append({"actor": actor, "node": node_from_label(name), "next": nxt, "action": name})
        # A liveness trace closes back onto an earlier state: the loop starts there
        try:
            if loop_start is None and to_ref[0] <= from_ref[0]:
                loop_start = len(steps)
        except Exception:
            pass
    return steps, loop_start

def export_tla_trace(dump_path, model_file, tlc_output):
    """Write the canonical trace from a TLC -dumpTrace json file (if a
    counterexample was dumped)."""
    try:
        with open(dump_path, "r", encoding="utf-8") as f:
            ce_doc = json.load(f)
    except Exception as e:
        print(f"Warning: could not read TLC trace dump {dump_path}: {e}")
        return
    ce = ce_doc.get("counterexample")
    if not ce and isinstance(ce_doc, dict) and ("action" in ce_doc or "state" in ce_doc):
        ce = ce_doc  # some TLC versions dump the counterexample object unwrapped
    if not ce:
        return  # no counterexample dumped (verification succeeded)
    steps, loop_start = tla_ce_steps(ce)
    if not steps:
        return
    reason = re.search(r"Error: (.*)", tlc_output)
    prop = reason.group(1).strip() if reason else "unknown"
    write_trace_json(TRACE_JSON_FILE, "tla", model_file, prop,
                     "lasso" if loop_start is not None else "safety",
                     steps, loop_start, CHANNEL_SIZE)

def extract_labels_from_pml(pml_file):
    labels = {}
    if not os.path.exists(pml_file):
        return labels
    try:
        with open(pml_file, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        label_pattern = re.compile(r'^\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*:')
        for i, line in enumerate(lines, start=1):
            match = label_pattern.match(line)
            if match:
                labels[i] = match.group(1)
    except Exception as e:
        print(f"Warning: could not parse labels from {pml_file}: {e}")
    return labels

def collect_spin_steps(trace_stdout, pml_labels):
    """Canonical steps from the `spin -t -p` replay output.

    Node ids come from the nearest preceding L_<id> label in the .pml (every IR
    instruction is emitted under its own L_<id> label, so all statements of one
    node map to that node). For liveness counterexamples the replay prints a
    `<<<<<START OF CYCLE>>>>` marker: the steps after it are the loop (the
    loop may be empty — the last state stutters forever, e.g. every actor is
    blocked). Returns (steps, loop_start_index or None).
    """
    ir_labels = {ln: node_from_label(name) for ln, name in pml_labels.items() if node_from_label(name) is not None}
    proc_re = re.compile(r'proc\s+\d+\s+\(([^)]+)\)')
    line_re = re.compile(r':(\d+)\s+\(state')
    action_re = re.compile(r'\[(.*?)\]')

    steps, cycle_seen, loop_start = [], False, None
    for line in trace_stdout.split('\n'):
        if 'START OF CYCLE' in line:
            cycle_seen = True
            continue
        if 'proc' not in line:
            continue
        proc_match = proc_re.search(line)
        if not proc_match:
            continue

        actor = proc_match.group(1).split(':')[0]
        line_match = line_re.search(line)
        pml_line = int(line_match.group(1)) if line_match else None

        action_match = action_re.search(line)
        action_raw = action_match.group(1) if action_match else ""
        if not action_raw:
            continue
        if action_raw.startswith('((') or action_raw == 'else' or is_ignored(action_raw):
            continue

        action_clean = action_raw.split('(')[0].strip()

        label = None
        node = None
        if pml_line is not None:
            candidates = [ln for ln in pml_labels.keys() if ln <= pml_line]
            if candidates:
                label = pml_labels[max(candidates)]
            ir_candidates = [ln for ln in ir_labels.keys() if ln <= pml_line]
            if ir_candidates:
                node = ir_labels[max(ir_candidates)]

        steps.append({
            'actor': actor,
            'action': label if label else action_clean,
            'node': node,
        })
        if cycle_seen and loop_start is None:
            loop_start = len(steps)
    if cycle_seen and loop_start is None:
        # Empty loop: the marker is the last line, the final state stutters forever
        loop_start = len(steps) + 1
    # "next" = the node of the actor's next step (its pc after this step);
    # disambiguates same-node transitions (loop-guard pass vs exit). None for
    # the actor's last step or across unlabeled plumbing steps.
    last_by_actor = {}
    for i in range(len(steps) - 1, -1, -1):
        steps[i]['next'] = last_by_actor.get(steps[i]['actor'])
        last_by_actor[steps[i]['actor']] = steps[i]['node']
    return steps, loop_start

def parse_spin_output(target_file, source_map, source_code):
    # This will parse source file to extract labels position (trail output doesn't contain labels)
    try:
        with open(target_file, 'r', encoding='utf-8') as f:
            content = f.read()
        ltl_names = re.findall(r"ltl\s+([a-zA-Z0-9_]+)", content)
    except Exception as e:
        print(f"Error reading model file: {e}")
        return

    if not ltl_names:
        print("No LTL formulas found")
        return

    pml_labels = extract_labels_from_pml(target_file)

    base_name = os.path.basename(target_file)
    local_trail = f"{base_name}.trail"
    target_trail = f"{target_file}.trail"
    trace_written = False

    # pan artifacts live in the cwd: wipe leftovers from earlier runs, or a
    # stale pan.out silently "verifies" the current model with the wrong
    # generated checker (exactly as dangerous as a stale trail).
    for stale in ("pan.c", "pan.out", "pan.b", "pan.h", "pan.m", "pan.pre", local_trail):
        if os.path.exists(stale):
            os.remove(stale)

    for prop in ltl_names:
        header = f" CHECKING PROPERTY: {prop} " if prop else " CHECKING DEFAULT PROPERTIES "
        print(f"\n{'='*20}{header}{'='*20}")

        gen_cmd = f"{SPIN_CMD} -a {target_file}"
        gen_result = run_cmd(gen_cmd)
        build_result = run_cmd("gcc -O2 pan.c -o pan.out")
        if gen_result.returncode != 0 or build_result.returncode != 0:
            # A failed generation/build must not be mistaken for a verdict:
            # no "errors: 0" below => FAILED without a trail => the caller
            # reports an error, not a successful check.
            print(f"RESULT: FAILED for {prop if prop else 'model'}")
            print(f"pan generation/build failed (is gcc on PATH?):\n{gen_result.stderr}{build_result.stderr}")
            continue
        # -f: accept only weakly fair execution sequences (pan flag); "none"
        # checks all paths, "strong" never reaches here (rejected in __main__)
        fair_flag = " -f" if FAIRNESS == "weak" else ""
        pan_result = run_cmd(f"./pan.out -a{fair_flag} -N {prop}")

        if "errors: 0" in pan_result.stdout:
            print(f"RESULT: SUCCESS for {prop if prop else 'model'}")
        else:
            print(f"RESULT: FAILED for {prop if prop else 'model'}")
            print("-" * 50)

            if os.path.exists(local_trail) and os.path.abspath(local_trail) != os.path.abspath(target_trail):
                shutil.copy2(local_trail, target_trail)

            trace_cmd = f"{SPIN_CMD} -t -p {target_file}"
            trace_result = run_cmd(trace_cmd)

            steps, loop_start = collect_spin_steps(trace_result.stdout, pml_labels)

            step_count = 1
            for step in steps:
                if step_count > max_trace_steps():
                    break
                print_unified_step(
                    step_count,
                    step['actor'],
                    step['action'],
                    source_map.get(step['action']),
                    source_code,
                    ""
                )
                step_count += 1

            if TRACE_JSON_FILE and not trace_written:
                write_trace_json(TRACE_JSON_FILE, "spin", target_file, prop,
                                 "lasso" if loop_start is not None else "safety",
                                 steps, loop_start, CHANNEL_SIZE)
                trace_written = True

            # Trails file named the same for different properties, so cleaning them once finished with current property
            trail_file = f"{os.path.basename(target_file)}.trail"
            if os.path.exists(trail_file):
                os.remove(trail_file)


def parse_curtis_output(model_file, channel_size, max_steps):
    """Curtis consumes the .tvir IR dump directly and prints its own verdict lines
    ([ltl|ctl] <name>: HOLDS|VIOLATED) plus counterexample traces — relay them
    as-is, truncating counterexample steps at max_steps (--trace-size), like the
    tla/spin trace rendering. Exit codes: 0 all specs hold, 1 some spec violated, 2 error."""
    cmd = f"{CURTIS_CMD} --channel-size {channel_size}"
    if FAIRNESS == "weak":
        cmd += " --weak-fairness"
    elif FAIRNESS == "strong":
        cmd += " --strong-fairness"
    result = run_cmd(f"{cmd} {model_file}")
    if result.stdout:
        step_re = re.compile(r"^    \d+\. ")
        in_cex, printed, truncated = False, 0, False
        for line in result.stdout.splitlines():
            if line.startswith("  counterexample"):
                in_cex, printed, truncated = True, 0, False
                print(line)
            elif in_cex and step_re.match(line):
                printed += 1
                if printed <= max_steps:
                    print(line)
                elif not truncated:
                    truncated = True
                    print(f"    ... (counterexample truncated at --trace-size={max_steps} steps)")
            else:
                in_cex = False
                print(line)
    if result.returncode == 2 and result.stderr:
        print(result.stderr, end="")
    if result.returncode == 0:
        print("VERIFICATION SUCCESSFUL (Curtis)")
    else:
        print("VERIFICATION FAILED (Curtis)")


def parse_flags(argv):
    """Split positional args from optional flags (--trace-json, --no-truncate,
    --channel-size, --fairness)."""
    positional, flags, i = [], {}, 0
    while i < len(argv):
        a = argv[i]
        if a == "--trace-json" and i + 1 < len(argv):
            flags["trace_json"] = argv[i + 1]; i += 2
        elif a == "--no-truncate":
            flags["no_truncate"] = True; i += 1
        elif a == "--channel-size" and i + 1 < len(argv):
            flags["channel_size"] = int(argv[i + 1]); i += 2
        elif a == "--fairness" and i + 1 < len(argv):
            flags["fairness"] = argv[i + 1]; i += 2
        else:
            positional.append(a); i += 1
    return positional, flags

if __name__ == "__main__":
    positional, flags = parse_flags(sys.argv[1:])
    TRACE_JSON_FILE = flags.get("trace_json")
    NO_TRUNCATE = flags.get("no_truncate", False)
    # Used by curtis only (passed to its CLI); the tla/spin codegen already embeds it
    CHANNEL_SIZE = flags.get("channel_size", 20)
    FAIRNESS = flags.get("fairness", "weak")

    if len(positional) < 5:
        print("Usage: python verifier.py <tla|spin|curtis> <model_file> <tvl source file> <line mapping file> <trace size>"
              " [--trace-json <file>] [--no-truncate] [--channel-size <n>] [--fairness <weak|strong|none>]")
        sys.exit(1)
    if FAIRNESS not in ("weak", "strong", "none"):
        print(f"Error: invalid --fairness '{FAIRNESS}' (weak|strong|none)")
        sys.exit(1)

    target, model_file, source_file, map_file, trace_size = positional[0], positional[1], positional[2], positional[3], int(positional[4])
    MAX_TRACE_STEPS = trace_size

    source_map = load_source_map(map_file)
    source_code = load_source_code(source_file)

    if target == "tla":
        run_cmd(f"{PCAL_CMD} {model_file}")
        cfg = os.path.splitext(model_file)[0] + ".cfg"
        base_cmd = f"{TLC_CMD} -config {cfg} {model_file}" if os.path.exists(cfg) else f"{TLC_CMD} {model_file}"
        cmd, tlc_dump = base_cmd, None
        if TRACE_JSON_FILE:
            # Same run, plus a machine-readable counterexample dump
            tlc_dump = f"{TRACE_JSON_FILE}.tlc.json"
            cmd = f"{TLC_CMD} -dumpTrace json {tlc_dump} " + base_cmd[len(TLC_CMD) + 1:]
        output = run_cmd(cmd).stdout
        parse_tla_output(output, source_map, source_code)
        if TRACE_JSON_FILE and tlc_dump:
            export_tla_trace(tlc_dump, model_file, output)
    elif target == "spin":
        if FAIRNESS == "strong":
            print("Error: SPIN does not support strong fairness (use weak or none)")
            sys.exit(1)
        parse_spin_output(model_file, source_map, source_code)
    elif target == "curtis":
        parse_curtis_output(model_file, CHANNEL_SIZE, MAX_TRACE_STEPS)