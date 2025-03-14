#!/bin/bash

repo_path() {
  local root_dir="$1"
  local repo="$2"
  echo "$root_dir/$repo"
}

ensure_repo_cloned() {
  local root_dir="$1"
  local repo="$2"
  echo "ensuring that repo $repo is cloned"
  if [ -d "$(repo_path "$root_dir" "$repo")" ]; then
    echo "$(repo_path "$root_dir" "$repo") is already cloned"
  else
    git clone "https://github.com/dbeaver/$repo.git" "$(repo_path "$root_dir" "$repo")"
  fi
}

clone_dependencies() {
  local root_dir="$1"
  local repo="$2"
  echo "cloning dependencies for $repo"
  project_dependencies_file=$(repo_path "$root_dir" "$repo")/project.deps
  echo "reading dependencies file $project_dependencies_file"
  while IFS= read -r dep_repo || [[ -n "$dep_repo" ]]; do
    ensure_repo_cloned "$root_dir" "$dep_repo"
  done < "$project_dependencies_file"
}

# Example of usage:
# script_dir="$(realpath "$(dirname "$0")")"
# repositories_root_dir="$(realpath "$script_dir/../..")" <======== ENTER CORRECT PATH HERE DEPENDING ON YOUR SCRIPT LOCATION
# [ ! -d "$repositories_root_dir/dbeaver-common" ] && git clone --depth 1 https://github.com/dbeaver/dbeaver-common.git "$repositories_root_dir/dbeaver-common"
# source "$repositories_root_dir/dbeaver-common/scripts/git_utils.sh

prepare_repo_and_dependencies() {
  if [ $# -ne 2 ]; then
    echo "Usage: prepare_repo_and_dependencies <repositories_root_dir> <repo_name>"
    return 1
  fi

  local repositories_root_dir="$1"
  local repo_name="$2"
  
  echo "preparing repo $repo_name in $repositories_root_dir"
  ensure_repo_cloned "$repositories_root_dir" "$repo_name"
  clone_dependencies "$repositories_root_dir" "$repo_name"
}

export prepare_repo_and_dependencies
