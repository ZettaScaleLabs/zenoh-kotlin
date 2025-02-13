#!/usr/bin/env bash

set -xeo pipefail

readonly live_run=${LIVE_RUN:-false}
# Release number
readonly version=${VERSION:?input VERSION is required}
# Dependencies' pattern
readonly bump_deps_pattern=${BUMP_DEPS_PATTERN:-''}
# Dependencies' version
readonly bump_deps_version=${BUMP_DEPS_VERSION:-''}
# Dependencies' git branch
readonly bump_deps_branch=${BUMP_DEPS_BRANCH:-''}
# Git actor name
readonly git_user_name=${GIT_USER_NAME:?input GIT_USER_NAME is required}
# Git actor email
readonly git_user_email=${GIT_USER_EMAIL:?input GIT_USER_EMAIL is required}

cargo +stable install toml-cli

# NOTE(fuzzypixelz): toml-cli doesn't yet support in-place modification
# See: https://github.com/gnprice/toml-cli?tab=readme-ov-file#writing-ish-toml-set
function toml_set_in_place() {
  local tmp=$(mktemp)
  toml set "$1" "$2" "$3" > "$tmp"
  mv "$tmp" "$1"
}

export GIT_AUTHOR_NAME=$git_user_name
export GIT_AUTHOR_EMAIL=$git_user_email
export GIT_COMMITTER_NAME=$git_user_name
export GIT_COMMITTER_EMAIL=$git_user_email

# Bump Gradle project version
printf '%s' "$version" > version.txt
# Propagate version change to zenoh-jni
toml_set_in_place zenoh-jni/Cargo.toml "package.version" "$version"

git commit version.txt zenoh-jni/Cargo.toml -m "chore: Bump version to \`$version\`"

# Select all package dependencies that match $bump_deps_pattern and bump them to $bump_deps_version
if [[ "$bump_deps_pattern" != '' ]]; then
  deps=$(toml get zenoh-jni/Cargo.toml dependencies | jq -r "keys[] | select(test(\"$bump_deps_pattern\"))")
  for dep in $deps; do
    if [[ -n $bump_deps_version ]]; then
      toml_set_in_place zenoh-jni/Cargo.toml "dependencies.$dep.version" "$bump_deps_version"
    fi

    if [[ -n $bump_deps_branch ]]; then
      toml_set_in_place zenoh-jni/Cargo.toml "dependencies.$dep.branch" "$bump_deps_branch"
    fi
  done
  # Update lockfile
  cargo check --manifest-path zenoh-jni/Cargo.toml

  if [[ -n $bump_deps_version || -n $bump_deps_branch ]]; then
    git commit zenoh-jni/Cargo.toml zenoh-jni/Cargo.lock -m "chore: Bump \`$bump_deps_pattern\` dependencies to \`$bump_deps_version\`"
  else
    echo "warn: no changes have been made to any dependencies matching $bump_deps_pattern"
  fi
fi

if [[ ${live_run} ]]; then
  git tag --force "$version" -m "v$version"
fi
git log -10
git show-ref --tags
git push origin
git push --force origin "$version"
