import sys
import subprocess
import re

MAX_TRACE_STEPS = 10

def run_cmd(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True)

def print_unified_step(step_num, actor, action, extra_info=""):
    print(f"STEP: {step_num}")
    print(f"  Actor  : {actor}")
    print(f"  Action : {action}")
    if extra_info:
        print(f"  State  : {extra_info}")
    print()

def parse_tla_output(output):
    if "Error: Deadlock reached" in output or "Error: " in output:
        print("\n==================================================")
        print("VERIFICATION FAILED (TLA+)")
        err_match = re.search(r"Error: (.*)", output)
        if err_match:
            print(f"Reason: {err_match.group(1)}")
        print("==================================================\n")

        # Парсим состояния
        states = re.findall(r"State (\d+): <(.*?)>\n(.*?)(?=\nState \d+:|\n\n|\Z)", output, re.DOTALL)

        prev_pc = {}
        step_count = 1

        for state_num, location, vars_str in states:
            if step_count > MAX_TRACE_STEPS:
                print(f"... Trace truncated. Exceeded {MAX_TRACE_STEPS} steps.")
                break

            action = location.strip().split(' ')[0] # Достаем только метку, например L_7
            actor_moved = "System"

            # Ищем изменения в pc (program counter), чтобы понять, кто походил
            pc_match = re.search(r"/\\ pc = \[(.*?)\]", vars_str)
            if pc_match:
                current_pc = {}
                # Парсим R1 |-> "L_1", R2 |-> "L_4"
                for mapping in pc_match.group(1).split(', '):
                    if "|->" in mapping:
                        k, v = mapping.split(" |-> ")
                        current_pc[k.strip()] = v.strip().strip('"')

                # Ищем разницу с предыдущим шагом
                if prev_pc:
                    for k, v in current_pc.items():
                        if k in prev_pc and prev_pc[k] != v:
                            actor_moved = k
                            break
                prev_pc = current_pc

            # Опционально: достаем состояние очередей для контекста
            queues = ""
            q_match = re.search(r"/\\ queues = (\[.*?\]|<<>>)", vars_str)
            if q_match:
                queues = q_match.group(1)

            print_unified_step(step_count, actor_moved, action, queues)
            step_count += 1

    elif "No error has been found" in output or "states generated" in output:
        print("\nVERIFICATION SUCCESSFUL (TLA+)\nNo violations found.")
    else:
        print("\nUNKNOWN RESULT\n", output)

def parse_spin_output(target_file):
    run_cmd(f"spin -a {target_file}")
    run_cmd("gcc -O2 pan.c -o pan.out")
    pan_result = run_cmd("./pan.out -a -f")

    if "errors: 0" in pan_result.stdout:
        print("\nVERIFICATION SUCCESSFUL (SPIN)\nNo violations found.")
    else:
        print("\n==================================================")
        print("VERIFICATION FAILED (SPIN)")

        if "acceptance cycle" in pan_result.stdout:
            print("Reason: LTL Property Violation (Acceptance cycle found)")
        elif "assertion violated" in pan_result.stdout:
            print("Reason: Assertion Violated")
        elif "invalid end state" in pan_result.stdout:
            print("Reason: Deadlock (Invalid end state)")
        print("==================================================\n")

        # Запускаем трейс
        trace_result = run_cmd(f"spin -t -p {target_file}")

        # Парсим вывод SPIN: 25:  proc  2 (R3:1) ./test.pml:81 (state 37) [Q_R1_R3!MSG_X2]
        trace_lines = re.findall(r"\s*\d+:\s+proc\s+\d+\s+\((.*?):\d+\).*?\[(.*?)\]", trace_result.stdout)

        step_count = 1
        for actor, action in trace_lines:
            # Игнорируем внутренние проверки планировщика и else
            if action.startswith("((") or action == "else":
                continue

            if step_count > MAX_TRACE_STEPS:
                print(f"... Trace truncated. Exceeded {MAX_TRACE_STEPS} steps.")
                break

            print_unified_step(step_count, actor, action)
            step_count += 1

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python verifier.py <target> <file>")
        sys.exit(1)

    target = sys.argv[1]
    file_path = sys.argv[2]

    if target == "tla":
        run_cmd(f"pcal {file_path}")
        tlc_result = run_cmd(f"tlc {file_path}")
        parse_tla_output(tlc_result.stdout)
    elif target == "spin":
        parse_spin_output(file_path)