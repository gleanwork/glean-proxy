#!/usr/bin/env python3

import os
import subprocess
import sys


class Gazelle:
    def __init__(self, name, root_dirs, gazelle_command, static_directories=None):
        if static_directories is None:
            static_directories = []
        self.name = name
        self.root_dirs = root_dirs
        self.gazelle_command = gazelle_command
        self.static_directories = static_directories

    def filter_files(self, files):
        return [
            line.split('\t')[1]
            for line in files
            if any(line.split('\t')[1].startswith(root_dir) for root_dir in self.root_dirs)
        ]

    def run(self):
        changed_files = get_changed_files()
        filtered_files = self.filter_files(changed_files)
        moved_or_deleted = any(
            (line.split('\t')[0].startswith('D') or line.split('\t')[0].startswith('R')) for line in changed_files
        )

        if moved_or_deleted:
            self.run_gazelle_root()
        else:
            unique_directories = get_unique_directories(filtered_files)
            self.run_gazelle(unique_directories)

    def run_gazelle(self, directories):
        timeout = 60
        directories = directories + self.static_directories
        if directories:
            command = ['bazel', 'run', self.gazelle_command, '--'] + directories
            with open(f'/tmp/gazelle_{self.name}_output.log', 'w') as f:
                try:
                    result = subprocess.run(command, stdout=f, stderr=subprocess.STDOUT, timeout=timeout, check=False)
                except subprocess.TimeoutExpired:
                    print(f'Bazel command timed out after {timeout} seconds')
                except Exception as e:
                    print(f'An unexpected error occurred: {e}')
            if result.returncode != 0:
                print(
                    f'Failed to run gazelle for directories: {directories}. Check /tmp/gazelle_{self.name}_output.log for details.'
                )
                sys.exit(1)
            else:
                print(f'Successfully ran gazelle for directories: {directories}')

    def run_gazelle_root(self):
        timeout = 100
        with open(f'/tmp/gazelle_{self.name}_output.log', 'w') as f:
            for root_dir in self.root_dirs:
                try:
                    result = subprocess.run(
                        ['bazel', 'run', self.gazelle_command, '--', root_dir] + self.static_directories,
                        stdout=f,
                        stderr=subprocess.STDOUT,
                        timeout=timeout,
                        check=False,
                    )
                except subprocess.TimeoutExpired:
                    print(f'Bazel command timed out after {timeout} seconds')
                except Exception as e:
                    print(f'An unexpected error occurred: {e}')
            if result.returncode != 0:
                print(f'Failed to run gazelle. Check /tmp/gazelle_{self.name}_output.log for details.')
                sys.exit(1)
            else:
                print('Successfully ran gazelle')


def get_changed_files():
    """Get the list of changed files from git."""
    result = subprocess.run(['git', 'diff', '--cached', '--name-status'], capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise Exception('Failed to get changed files from git.')
    return result.stdout.splitlines()


def get_unique_directories(files):
    """Get a list of unique directories from the list of files."""
    directories = set()
    for file in files:
        directory = os.path.dirname(file)
        directories.add(directory)
    return list(directories)


def main():
    try:
        gazelle = Gazelle(
            name='java',
            root_dirs=['src/main/java/com/glean'],
            gazelle_command='//:gazelle_java',
        )
        gazelle.run()
    except Exception as e:
        print(f'Error: {e}')


if __name__ == '__main__':
    main()
