#!/usr/bin/env python3
import os
import subprocess

# Path to the exclude list file
EXCLUDE_LIST_FILE = os.path.join(os.getcwd(), 'tools/java_refactor_scripts/exclude_list.txt')


def get_exclude_list():
    """Read the exclude list file and return a set of excluded paths."""
    if not os.path.exists(EXCLUDE_LIST_FILE):
        return set()

    with open(EXCLUDE_LIST_FILE) as f:
        exclude_list = {line.strip() for line in f if line.strip()}
    return exclude_list


def get_changed_files():
    """Get the list of changed BUILD.bazel files under src/main/java/com/glean."""
    result = subprocess.run(
        ['git', 'diff', '--cached', '--name-only', '--diff-filter=ACM'], stdout=subprocess.PIPE, text=True, check=False
    )
    changed_files = result.stdout.splitlines()
    return [f for f in changed_files if f.startswith('src/main/java/com/glean/') and f.endswith('BUILD.bazel')]


def count_jira_library_targets(file_path):
    """Count the number of jira_library targets in the given file."""
    with open(file_path) as f:
        content = f.read()
    # TODO: Slightly hacky. Ideal way would be to used buildozer to list java_library targets. Will follow up on this.
    return content.count('java_library(')


def main():
    exclude_list = get_exclude_list()
    changed_files = get_changed_files()
    error_flag = False

    for file in changed_files:
        if file in exclude_list:
            continue

        jira_library_count = count_jira_library_targets(file)

        if jira_library_count > 1:
            print(f'Error: {file} contains more than one jira_library target.')
            error_flag = True

    if error_flag:
        exit(1)
    else:
        exit(0)


if __name__ == '__main__':
    main()
