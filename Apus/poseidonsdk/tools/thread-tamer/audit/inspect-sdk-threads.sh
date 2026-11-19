#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "usage: $0 OUTPUT_DIRECTORY [COORDINATE_FILE]" >&2
  exit 2
fi

scan_root=$1
target_list=${2:-"$(cd "$(dirname "$0")" && pwd)/sdk-artifacts.txt"}
cache_root=${GRADLE_USER_HOME:-"$HOME/.gradle"}/caches/modules-2/files-2.1
jadx_command=${JADX_COMMAND:-/Users/apus/Downloads/new_file/jadx/jadx-cli/build/install/jadx/bin/jadx}
needle='new Thread\(|ThreadPoolExecutor\(|ScheduledThreadPoolExecutor\(|Executors\.new|HandlerThread\(|Timer\(|CoroutineScope\(|Dispatchers\.|newSingleThread|newFixedThread|newCachedThread|newScheduledThread'

mkdir -p "$scan_root/decompiled"
summary_file="$scan_root/thread-sites.tsv"
printf 'coordinate\tstatus\thits\tartifact\n' > "$summary_file"

while IFS= read -r coordinate; do
  [[ -z "$coordinate" || "$coordinate" == \#* ]] && continue
  IFS=: read -r group_id artifact_id version_id <<< "$coordinate"
  version_root="$cache_root/$group_id/$artifact_id/$version_id"
  binary=$(find "$version_root" -type f -name '*.aar' -print 2>/dev/null | head -n 1 || true)
  if [[ -z "$binary" ]]; then
    printf '%s\tmissing\t0\t-\n' "$coordinate" >> "$summary_file"
    continue
  fi

  safe_name=$(printf '%s' "$coordinate" | tr ':./' '____')
  source_root="$scan_root/decompiled/$safe_name"
  "$jadx_command" --no-res --threads-count 2 --output-dir "$source_root" "$binary" >/dev/null 2>&1 || true
  hit_count=$({ rg -n -e "$needle" "$source_root/sources" 2>/dev/null || true; } | wc -l | tr -d ' ')
  printf '%s\tdecompiled\t%s\t%s\n' "$coordinate" "$hit_count" "$binary" >> "$summary_file"
  if [[ "$hit_count" -gt 0 ]]; then
    rg -n -e "$needle" "$source_root/sources" > "$scan_root/$safe_name-sites.txt" || true
  fi
done < "$target_list"

echo "$summary_file"
