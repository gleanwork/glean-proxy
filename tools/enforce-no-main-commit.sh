#!/bin/bash

# Stops accidental commits to main branches

BRANCH=`git rev-parse --abbrev-ref HEAD`

if [[ "$BRANCH" == "main" ]]; then
  >&2 echo "You are on branch $BRANCH. Are you sure you want to commit to this branch?"
  >&2 echo "If so, commit with -n to bypass this pre-commit hook."
  exit 1
fi
