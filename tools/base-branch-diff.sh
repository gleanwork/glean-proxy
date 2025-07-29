#!/bin/sh

RED='\033[0;31m'
NC='\033[0m'

# hook to flag if current branch is up-to-date with latest changes from base branch before pushing

# Check login status of Github CLI and login if needed
gh auth status > /dev/null 2>&1
retVal=$?
if [ $retVal -ne 0 ]; then
  echo "${RED}[-] Please run: gh auth login${NC}"
  exit 1
fi

# Check if a PR exists on the current branch
BASE_BRANCH=$(gh pr view --json baseRefName --jq .baseRefName)
retVal=$?
if [ $retVal -eq 0 ]; then

  # Sync local git state with remote
  git fetch origin > /dev/null 2>&1

  # Get current branch
  CURRENT_BRANCH=$(git branch | grep \* | cut -d ' ' -f2)

  # Check the number of commits in the diff between local branch vs remote base branch
  NB_COMMITS_IN_DIFF=$(git rev-list --count origin/${BASE_BRANCH}..${CURRENT_BRANCH})

  # If number of commits greater than threshold then abort push
  if [ "${NB_COMMITS_IN_DIFF}" -gt "80" ]; then
    echo "${RED}[-] Your branch ${CURRENT_BRANCH} has ${NB_COMMITS_IN_DIFF} commits diff with origin/${BASE_BRANCH}.${NC}"
    echo "${RED}[-] Aborting push.${NC}"
    exit 1
  fi

  # Check if merged with a branch other than the PR base branch
  BAD_MERGES=$(git rev-list origin/${BASE_BRANCH}..${CURRENT_BRANCH} --pretty="tformat:%B" | grep -m 1 "^Merge branch .* into ${CURRENT_BRANCH}$" | grep -v "^Merge branch '${BASE_BRANCH}'.* into ${CURRENT_BRANCH}$" | grep -v "^Merge branch '${CURRENT_BRANCH}'.* into ${CURRENT_BRANCH}$")

  # If merges from a branch other than base branch are found then abort push
  if [ ! -z "${BAD_MERGES}" ]; then
    echo "${RED}[-] Your branch ${CURRENT_BRANCH} has below merge commits which don't match the base branch ${BASE_BRANCH} on the PR.${NC}"
    echo "${RED}[-] ${BAD_MERGES}${NC}"
    echo "${RED}[-] Run 'git rev-list origin/${BASE_BRANCH}..${CURRENT_BRANCH} --pretty='tformat:%B'' to get the list of commits${NC}"
    echo "${RED}[-] Run 'git reset --hard <commit-sha>' for all the bad merge commits.${NC}"
    echo "${RED}[-] Aborting push.${NC}"
    exit 1
  fi

fi
