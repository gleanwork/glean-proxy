#!/usr/bin/python3
import logging
import os
import subprocess
import sys


def find_targets_tests():
    diff_command = ['git', 'diff', '--diff-filter=ACMR', '--name-only']
    process = subprocess.run(diff_command, capture_output=True, check=False)
    changed_files = process.stdout.decode('utf-8').split('\n')

    process = subprocess.run(diff_command + ['--staged'], capture_output=True, check=False)
    changed_files += process.stdout.decode('utf-8').split('\n')

    bazel_targets = set()
    test_targets = set()
    for changed_file in changed_files:
        dir_name = os.path.dirname(changed_file)

        # Finds the first inner directory that contains a BUILD.bazel file
        while dir_name:
            if not os.path.exists(f'{dir_name}/BUILD.bazel'):
                dir_name = os.path.dirname(dir_name)
                continue
            break

        if not dir_name:
            continue

        # Finds the targets and tests that depend on the changed files
        deps = f'rdeps({dir_name}/...,  {changed_file})'
        process = subprocess.run(['bazel', 'query', deps], capture_output=True, check=False)
        bazel_targets.update(process.stdout.decode('utf-8').split('\n'))
        process = subprocess.run(['bazel', 'query', f'kind(test, {deps})'], capture_output=True, check=False)
        test_targets.update(process.stdout.decode('utf-8').split('\n'))

    targets = [t for t in (bazel_targets - test_targets) if t]
    tests = [t for t in test_targets if t]
    return targets, tests


def run_bazel_command(command: str, bazel_targets: list[str]):
    cmd = ['bazel', command] + bazel_targets
    process = subprocess.run(cmd, capture_output=True, check=False)
    if process.returncode:
        logging.error('"{}" failed\n{}'.format(' '.join(cmd), process.stdout.decode('utf-8')))
        sys.exit(1)


def main():
    targets, tests = find_targets_tests()
    if targets:
        logging.warning(f'Building bazel targets: {targets}')
        run_bazel_command('build', targets)
    if tests:
        logging.warning(f'Testing bazel targets: {tests}')
        run_bazel_command('test', tests)


if __name__ == '__main__':
    main()
