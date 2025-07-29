#!/usr/bin/env python3

import os
import subprocess
import sys


def get_modified_files():
    cmd = ['git', 'status', '--porcelain']
    result = subprocess.run(cmd, capture_output=True, text=True, check=True)

    modified_files = []
    for line in result.stdout.splitlines():
        status = line[:2].strip()
        file_path = line[3:].strip()

        if status == 'R':
            old_path, new_path = file_path.split(' -> ')

            if old_path.startswith('src/main/java/com/glean/') and old_path.endswith('.java'):
                test_old_path = old_path.replace('src/main/java/', 'src/test/java/')
                if test_old_path.endswith('.java'):
                    test_old_path = test_old_path[:-5] + 'Test.java'

                test_new_path = new_path.replace('src/main/java/', 'src/test/java/')
                if test_new_path.endswith('.java'):
                    test_new_path = test_new_path[:-5] + 'Test.java'  # Remove 'Test' from filename

                modified_files.append(
                    {
                        'status': 'R',
                        'old_path': old_path,
                        'test_old_path': test_old_path,
                        'new_path': new_path,
                        'test_new_path': test_new_path,
                    }
                )

        elif status == 'D' and file_path.startswith('src/main/java/com/glean/') and file_path.endswith('.java'):
            test_path = file_path.replace('src/main/java/', 'src/test/java/')
            if test_path.endswith('.java'):
                test_path = test_path[:-5] + 'Test.java'

            modified_files.append({'status': 'D', 'path': file_path, 'test_path': test_path})

    return modified_files


def check_file_paths(modified_files):
    inconsistent_files = []

    for file_info in modified_files:
        if file_info['status'] == 'R':
            if os.path.exists(file_info['test_old_path']):
                inconsistent_files.append(
                    {
                        'type': 'rename',
                        'src_old': file_info['old_path'],
                        'src_new': file_info['new_path'],
                        'test_old': file_info['test_old_path'],
                    }
                )
        elif file_info['status'] == 'D':
            if os.path.exists(file_info['test_path']):
                inconsistent_files.append({'type': 'delete', 'src': file_info['path'], 'test': file_info['test_path']})

    return inconsistent_files


def main():
    modified_files = get_modified_files()

    if not modified_files:
        print('No moved or deleted files found in src/main/java/com/glean/')
        return 0

    inconsistent_files = check_file_paths(modified_files)

    if inconsistent_files:
        error_msg = 'Error: Found inconsistencies between source and test files:\n'
        for file in inconsistent_files:
            if file['type'] == 'rename':
                error_msg += '\nSource file moved:\n'
                error_msg += f"  From: {file['src_old']}\n"
                error_msg += f"  To: {file['src_new']}\n"
                error_msg += f"But test file still exists at old location: {file['test_old']}\n"
            else:
                error_msg += f"\nSource file deleted: {file['src']}\n"
                error_msg += f"But test file still exists: {file['test']}\n"
        raise Exception(error_msg)

    return 0


if __name__ == '__main__':
    sys.exit(main())
