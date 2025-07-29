#!/bin/sh

set -x

if grep "import .*\.\*;" "$@"
then
  printf "\nWildcard imports found. Please remove them before committing."
  exit 1
fi

exit 0