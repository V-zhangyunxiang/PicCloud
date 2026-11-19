#!/usr/bin/env bash
set -euo pipefail

tool_root=$(cd "$(dirname "$0")" && pwd)
repository_root=$(cd "$tool_root/../.." && pwd)
mode=${1:-}

case "$mode" in
  --local)
    "$repository_root/gradlew" -p "$tool_root/plugin" publishToMavenLocal
    "$repository_root/gradlew" -p "$repository_root" :tools:thread-tamer:runtime:publishToMavenLocal
    ;;
  --remote)
    "$repository_root/gradlew" -p "$tool_root/plugin" publishAllPublicationsToXalRepository
    "$repository_root/gradlew" -p "$repository_root" :tools:thread-tamer:runtime:publishAllVariants
    ;;
  *)
    echo "usage: $0 --local|--remote" >&2
    exit 2
    ;;
esac
