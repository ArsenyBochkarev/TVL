import shutil
import sys
import subprocess
import re
import os
import json

MAX_TRACE_STEPS = 20

PCAL_CMD = f"pcal"
TLC_CMD = f"tlc"
SPIN_CMD = "spin"

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
            action_info = f"{action} -> `{code_text}` (line {source_line})"
    elif source_line:
        action_info = f"{action} (line {source_line})"

    print(f"  Action : {action_info}")
    if state_diff:
        print(f"  State  : {state_diff}")
    print()

def is_ignored(name):
    return any(re.match(p, name) for p in IGNORED_PATTERNS)

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

            if step_count > MAX_TRACE_STEPS:
                break

            source_line = source_map.get(action)
            print_unified_step(step_count, actor_moved, action, source_line, source_code,
                               ", ".join([f"{k}={v}" for k, v in changed_vars.items()]))

            prev_pc, prev_state, step_count = current_pc, current_state, step_count + 1

    elif "No error" in output or "states generated" in output:
        print("\nVERIFICATION SUCCESSFUL (TLA+)")

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

    for prop in ltl_names:
        header = f" CHECKING PROPERTY: {prop} " if prop else " CHECKING DEFAULT PROPERTIES "
        print(f"\n{'='*20}{header}{'='*20}")

        gen_cmd = f"{SPIN_CMD} -a {target_file}"
        run_cmd(gen_cmd)
        run_cmd("gcc -O2 pan.c -o pan.out")
        pan_result = run_cmd(f"./pan.out -a -f -N {prop}")

        if "errors: 0" in pan_result.stdout:
            print(f"RESULT: SUCCESS for {prop if prop else 'model'}")
        else:
            print(f"RESULT: FAILED for {prop if prop else 'model'}")
            print("-" * 50)

            if os.path.exists(local_trail):
                shutil.copy2(local_trail, target_trail)

            trace_cmd = f"{SPIN_CMD} -t -p {target_file}"
            trace_result = run_cmd(trace_cmd)

            proc_re = re.compile(r'proc\s+\d+\s+\(([^)]+)\)')
            line_re = re.compile(r':(\d+)\s+\(state')
            action_re = re.compile(r'\[(.*?)\]')

            steps = []
            for line in trace_result.stdout.split('\n'):
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
                if pml_line is not None:
                    candidates = [ln for ln in pml_labels.keys() if ln <= pml_line]
                    if candidates:
                        nearest_line = max(candidates)
                        label = pml_labels[nearest_line]

                source_line = source_map.get(label) if label else None
                action_for_print = label if label else action_clean

                steps.append({
                    'actor': actor,
                    'action': action_for_print,
                    'source_line': source_line,
                })

            step_count = 1
            for step in steps:
                if step_count > MAX_TRACE_STEPS:
                    break
                print_unified_step(
                    step_count,
                    step['actor'],
                    step['action'],
                    step['source_line'],
                    source_code,
                    ""
                )
                step_count += 1

            # Trails file named the same for different properties, so cleaning them once finished with current property
            trail_file = f"{os.path.basename(target_file)}.trail"
            if os.path.exists(trail_file):
                os.remove(trail_file)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python verifier.py <tla|spin> <model_file> <tvl source file> <line mapping file>")
        sys.exit(1)

    target, model_file, source_file, map_file = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

    source_map = load_source_map(map_file)
    source_code = load_source_code(source_file)

    if target == "tla":
        run_cmd(f"{PCAL_CMD} {model_file}")
        cfg = os.path.splitext(model_file)[0] + ".cfg"
        cmd = f"{TLC_CMD} -config {cfg} {model_file}" if os.path.exists(cfg) else f"{TLC_CMD} {model_file}"
        parse_tla_output(run_cmd(cmd).stdout, source_map, source_code)
    elif target == "spin":
        parse_spin_output(model_file, source_map, source_code)