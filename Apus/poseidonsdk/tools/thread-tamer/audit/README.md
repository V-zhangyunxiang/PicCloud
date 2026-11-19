# SDK thread-source audit

`inspect-sdk-threads.sh` decompiles the resolved advertising SDK AARs from the local Gradle cache
with JADX and records concrete thread/executor construction sites. It intentionally excludes
AndroidX, Kotlin, and Java support artifacts so the report remains focused on third-party SDK
thread ownership.

```bash
chmod +x tools/thread-tamer/audit/inspect-sdk-threads.sh
tools/thread-tamer/audit/inspect-sdk-threads.sh /tmp/thread-tamer-jadx
```

The generated `thread-sites.tsv` is the summary. Per-artifact `*-sites.txt` files retain the source
path and line evidence for every matching construction site.

`results-20260902.tsv` records the checked-in summary from the branch audit. It omits machine-local
cache paths while retaining coverage and hit counts for all 51 AARs.
